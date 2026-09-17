package org.linguawiki.offline.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linguawiki.offline.data.ColorPalette
import org.linguawiki.offline.data.DictionaryDatabase
import org.linguawiki.offline.data.DictionaryEntry
import org.linguawiki.offline.data.EntrySummary
import org.linguawiki.offline.data.FontScale
import org.linguawiki.offline.data.LineSpacing
import org.linguawiki.offline.data.OfflinePackDatabase
import org.linguawiki.offline.data.OfflinePackDescriptor
import org.linguawiki.offline.data.OfflinePackError
import org.linguawiki.offline.data.OfflinePackException
import org.linguawiki.offline.data.OfflinePackManager
import org.linguawiki.offline.data.OfflinePackState
import org.linguawiki.offline.data.OnlineEdition
import org.linguawiki.offline.data.OnlineSuggestion
import org.linguawiki.offline.data.ThemeMode
import org.linguawiki.offline.data.WiktionaryCatalog
import org.linguawiki.offline.data.WiktionarySuggestionClient

enum class AppScreen {
    SEARCH,
    FAVORITES,
    HISTORY,
    SETTINGS,
    ENTRY,
    ONLINE,
}

data class MainUiState(
    val screen: AppScreen = AppScreen.SEARCH,
    val activeLanguage: String = "pt",
    val query: String = "",
    val results: List<EntrySummary> = emptyList(),
    val selectedEntry: DictionaryEntry? = null,
    val englishReferences: List<DictionaryEntry> = emptyList(),
    val favorites: List<EntrySummary> = emptyList(),
    val history: List<EntrySummary> = emptyList(),
    val onlineQuery: String = "",
    val onlineDraft: String = "",
    val onlineSuggestions: List<OnlineSuggestion> = emptyList(),
    val onlineSuggestionLoading: Boolean = false,
    val onlineSuggestionError: Boolean = false,
    val onlineSuggestionsExpanded: Boolean = false,
    val activeOnlineEdition: String = "pt",
    val onlineEditions: Set<String> = setOf("en", "pt"),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorPalette: ColorPalette = ColorPalette.GREEN,
    val fontScale: FontScale = FontScale.DEFAULT,
    val lineSpacing: LineSpacing = LineSpacing.STANDARD,
    val offlinePacks: List<OfflinePackState> = emptyList(),
    val loading: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = DictionaryDatabase(application)
    private val packManager = OfflinePackManager(application)
    private val suggestionClient = WiktionarySuggestionClient()
    private val preferences = application.getSharedPreferences(PREFERENCES, 0)
    private val packageDatabases = mutableMapOf<String, OfflinePackDatabase>()
    private val suggestionCache = linkedMapOf<String, List<OnlineSuggestion>>()

    private val _state = MutableStateFlow(
        MainUiState(
            onlineEditions = preferences.getStringSet(KEY_EDITIONS, setOf("en", "pt"))?.toSet()
                ?: setOf("en", "pt"),
            themeMode = enumPreference(KEY_THEME, ThemeMode.SYSTEM, ThemeMode::valueOf),
            colorPalette = enumPreference(KEY_PALETTE, ColorPalette.GREEN, ColorPalette::valueOf),
            fontScale = enumPreference(KEY_FONT_SCALE, FontScale.DEFAULT, FontScale::valueOf),
            lineSpacing = enumPreference(KEY_LINE_SPACING, LineSpacing.STANDARD, LineSpacing::valueOf),
            offlinePacks = packManager.initialStates(),
        ),
    )
    val state: StateFlow<MainUiState> = _state.asStateFlow()
    private var searchJob: Job? = null
    private var onlineSuggestionJob: Job? = null

    val availableEditions: List<OnlineEdition> = WiktionaryCatalog.editions
    val installedDictionaryCodes: List<String>
        get() = (listOf("en", "pt", "pl") + _state.value.offlinePacks
            .filter { it.installed }
            .map { it.descriptor.headwordLanguage })
            .distinct()

    init {
        refreshCollections()
    }

    fun updateQuery(value: String) {
        _state.update { it.copy(query = value) }
        scheduleSearch()
    }

    fun selectLanguage(language: String) {
        if (language == _state.value.activeLanguage) return
        _state.update { it.copy(activeLanguage = language) }
        scheduleSearch(immediate = true)
    }

    fun showScreen(screen: AppScreen) {
        if (screen == AppScreen.FAVORITES || screen == AppScreen.HISTORY) refreshCollections()
        if (screen == AppScreen.SETTINGS) refreshPackStates()
        _state.update { it.copy(screen = screen, selectedEntry = null, englishReferences = emptyList()) }
    }

    fun openEntry(entryId: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val (entry, englishReferences) = withContext(Dispatchers.IO) {
                val opened = getEntry(entryId)?.let { it.copy(favorite = database.isFavorite(it.id)) }
                if (opened != null) database.recordHistory(opened)
                opened to if (opened == null || opened.id.startsWith("pack/")) {
                    emptyList()
                } else {
                    database.getEnglishReferences(entryId)
                }
            }
            _state.update {
                it.copy(
                    screen = if (entry == null) AppScreen.SEARCH else AppScreen.ENTRY,
                    selectedEntry = entry,
                    englishReferences = englishReferences,
                    loading = false,
                )
            }
            refreshCollections()
        }
    }

    fun openTranslation(language: String, term: String, targetLemma: String?, targetEntryId: String?) {
        val lookup = targetLemma ?: term
        viewModelScope.launch {
            val targetId = withContext(Dispatchers.IO) {
                targetEntryId?.takeIf(::hasEntry)
                    ?: packDatabase(language)?.findExact(lookup)
                    ?: database.findExact(language, lookup)
            }
            if (targetId != null) {
                _state.update { it.copy(activeLanguage = language) }
                openEntry(targetId)
            } else {
                _state.update {
                    it.copy(
                        screen = AppScreen.SEARCH,
                        selectedEntry = null,
                        englishReferences = emptyList(),
                        activeLanguage = language,
                        query = lookup,
                    )
                }
                scheduleSearch(immediate = true)
            }
        }
    }

    fun toggleFavorite() {
        val current = _state.value.selectedEntry ?: return
        viewModelScope.launch {
            val favorite = withContext(Dispatchers.IO) { database.toggleFavorite(current) }
            _state.update { state ->
                state.copy(selectedEntry = state.selectedEntry?.copy(favorite = favorite))
            }
            refreshCollections()
            scheduleSearch(immediate = true)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { database.clearHistory() }
            _state.update { it.copy(history = emptyList()) }
        }
    }

    fun openOnline(term: String = _state.value.query) {
        val query = term.trim()
        if (query.isBlank()) return
        val current = _state.value
        val preferredEdition = current.activeLanguage.takeIf { it in current.onlineEditions }
            ?: "en".takeIf { it in current.onlineEditions }
            ?: availableEditions.firstOrNull { it.code in current.onlineEditions }?.code
            ?: "en"
        _state.update {
            it.copy(
                screen = AppScreen.ONLINE,
                onlineQuery = query,
                onlineDraft = query,
                onlineSuggestions = emptyList(),
                onlineSuggestionError = false,
                activeOnlineEdition = preferredEdition,
            )
        }
        scheduleOnlineSuggestions(immediate = true)
    }

    fun updateOnlineDraft(value: String) {
        _state.update { it.copy(onlineDraft = value, onlineSuggestionError = false, onlineSuggestionsExpanded = value.isNotBlank()) }
        scheduleOnlineSuggestions()
    }

    fun selectOnlineEdition(code: String) {
        if (code == _state.value.activeOnlineEdition) return
        _state.update {
            it.copy(
                activeOnlineEdition = code,
                onlineSuggestions = emptyList(),
                onlineSuggestionLoading = false,
                onlineSuggestionError = false,
                onlineSuggestionsExpanded = false,
            )
        }
    }

    fun submitOnlineSearch(term: String = _state.value.onlineDraft) {
        val query = term.trim()
        if (query.isBlank()) return
        onlineSuggestionJob?.cancel()
        _state.update {
            it.copy(
                onlineQuery = query,
                onlineDraft = query,
                onlineSuggestions = emptyList(),
                onlineSuggestionLoading = false,
                onlineSuggestionError = false,
                onlineSuggestionsExpanded = false,
            )
        }
    }

    fun retryOnlineSuggestions() {
        scheduleOnlineSuggestions(immediate = true)
    }

    fun setThemeMode(mode: ThemeMode) {
        preferences.edit().putString(KEY_THEME, mode.name).apply()
        _state.update { it.copy(themeMode = mode) }
    }

    fun setColorPalette(palette: ColorPalette) {
        preferences.edit().putString(KEY_PALETTE, palette.name).apply()
        _state.update { it.copy(colorPalette = palette) }
    }

    fun setFontScale(scale: FontScale) {
        preferences.edit().putString(KEY_FONT_SCALE, scale.name).apply()
        _state.update { it.copy(fontScale = scale) }
    }

    fun setLineSpacing(spacing: LineSpacing) {
        preferences.edit().putString(KEY_LINE_SPACING, spacing.name).apply()
        _state.update { it.copy(lineSpacing = spacing) }
    }

    fun toggleOnlineEdition(code: String) {
        val current = _state.value.onlineEditions
        val updated = if (code in current) {
            if (current.size == 1) return else current - code
        } else {
            current + code
        }
        preferences.edit().putStringSet(KEY_EDITIONS, updated).apply()
        _state.update { it.copy(onlineEditions = updated) }
    }

    fun installPack(packId: String) {
        val descriptor = descriptor(packId) ?: return
        if (_state.value.offlinePacks.firstOrNull { it.descriptor.packId == packId }?.downloading == true) return
        updatePack(packId) { it.copy(downloading = true, progress = 0f, error = null) }
        viewModelScope.launch {
            val failure = withContext(Dispatchers.IO) {
                runCatching {
                    packManager.install(descriptor) { progress ->
                        updatePack(packId) { current -> current.copy(progress = progress) }
                    }
                }.exceptionOrNull()
            }
            if (failure == null) {
                closePackDatabase(descriptor.headwordLanguage)
                updatePack(packId) {
                    it.copy(
                        installed = true,
                        downloading = false,
                        progress = null,
                        error = null,
                        freeBytes = packManager.availableBytes(),
                    )
                }
                _state.update { it.copy(activeLanguage = descriptor.headwordLanguage) }
                scheduleSearch(immediate = true)
            } else {
                val reason = (failure as? OfflinePackException)?.reason ?: OfflinePackError.INSTALLATION
                updatePack(packId) {
                    it.copy(
                        installed = packManager.isInstalled(descriptor),
                        downloading = false,
                        progress = null,
                        error = reason,
                        freeBytes = packManager.availableBytes(),
                    )
                }
            }
        }
    }

    fun removePack(packId: String) {
        val descriptor = descriptor(packId) ?: return
        viewModelScope.launch {
            closePackDatabase(descriptor.headwordLanguage)
            val removed = withContext(Dispatchers.IO) { packManager.remove(descriptor) }
            updatePack(packId) {
                it.copy(
                    installed = !removed,
                    downloading = false,
                    progress = null,
                    error = if (removed) null else OfflinePackError.REMOVAL,
                    freeBytes = packManager.availableBytes(),
                )
            }
            scheduleSearch(immediate = true)
        }
    }

    fun dismissPackError(packId: String) {
        updatePack(packId) { it.copy(error = null) }
    }

    fun requiredPeakBytes(descriptor: OfflinePackDescriptor): Long = packManager.requiredPeakBytes(descriptor)

    fun goBack() {
        onlineSuggestionJob?.cancel()
        when (_state.value.screen) {
            AppScreen.ENTRY, AppScreen.ONLINE -> _state.update {
                it.copy(
                    screen = AppScreen.SEARCH,
                    selectedEntry = null,
                    englishReferences = emptyList(),
                    onlineSuggestions = emptyList(),
                    onlineSuggestionLoading = false,
                    onlineSuggestionError = false,
                )
            }
            else -> Unit
        }
    }

    private fun scheduleSearch(immediate: Boolean = false) {
        searchJob?.cancel()
        val query = _state.value.query
        val language = _state.value.activeLanguage
        if (query.isBlank()) {
            _state.update { it.copy(results = emptyList(), loading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            if (!immediate) delay(130)
            _state.update { it.copy(loading = true) }
            val results = withContext(Dispatchers.IO) {
                val raw = packDatabase(language)?.search(query) ?: database.search(language, query)
                raw.map { it.copy(favorite = database.isFavorite(it.id)) }
            }
            if (_state.value.query == query && _state.value.activeLanguage == language) {
                _state.update { it.copy(results = results, loading = false) }
            }
        }
    }

    private fun scheduleOnlineSuggestions(immediate: Boolean = false) {
        onlineSuggestionJob?.cancel()
        val query = _state.value.onlineDraft.trim()
        val editionCode = _state.value.activeOnlineEdition
        val edition = availableEditions.firstOrNull { it.code == editionCode }
        if (query.isEmpty() || edition == null) {
            _state.update {
                it.copy(
                    onlineSuggestions = emptyList(),
                    onlineSuggestionLoading = false,
                    onlineSuggestionError = false,
                )
            }
            return
        }
        val cacheKey = "$editionCode\u0000${query.lowercase()}"
        suggestionCache[cacheKey]?.let { cached ->
            _state.update {
                it.copy(
                    onlineSuggestions = cached,
                    onlineSuggestionLoading = false,
                    onlineSuggestionError = false,
                )
            }
            return
        }

        onlineSuggestionJob = viewModelScope.launch {
            if (!immediate) delay(250)
            _state.update { it.copy(onlineSuggestionLoading = true, onlineSuggestionError = false) }
            val result = withContext(Dispatchers.IO) {
                runCatching { suggestionClient.suggestions(edition, query) }
            }
            if (
                _state.value.screen == AppScreen.ONLINE &&
                _state.value.onlineDraft.trim() == query &&
                _state.value.activeOnlineEdition == editionCode
            ) {
                result.onSuccess { suggestions ->
                    suggestionCache[cacheKey] = suggestions
                    while (suggestionCache.size > MAX_SUGGESTION_CACHE) {
                        suggestionCache.remove(suggestionCache.keys.first())
                    }
                    _state.update {
                        it.copy(
                            onlineSuggestions = suggestions,
                            onlineSuggestionLoading = false,
                            onlineSuggestionError = false,
                        )
                    }
                }.onFailure {
                    _state.update {
                        it.copy(
                            onlineSuggestions = emptyList(),
                            onlineSuggestionLoading = false,
                            onlineSuggestionError = true,
                        )
                    }
                }
            }
        }
    }

    private fun refreshCollections() {
        viewModelScope.launch {
            val collections = withContext(Dispatchers.IO) {
                database.favorites() to database.history()
            }
            _state.update { it.copy(favorites = collections.first, history = collections.second) }
        }
    }

    private fun refreshPackStates() {
        _state.update { current ->
            current.copy(
                offlinePacks = current.offlinePacks.map { pack ->
                    pack.copy(
                        installed = packManager.isInstalled(pack.descriptor),
                        freeBytes = packManager.availableBytes(),
                    )
                },
            )
        }
    }

    private fun descriptor(packId: String) = packManager.catalog.firstOrNull { it.packId == packId }

    private fun updatePack(packId: String, transform: (OfflinePackState) -> OfflinePackState) {
        _state.update { current ->
            current.copy(
                offlinePacks = current.offlinePacks.map {
                    if (it.descriptor.packId == packId) transform(it) else it
                },
            )
        }
    }

    private fun getEntry(entryId: String): DictionaryEntry? {
        if (!entryId.startsWith("pack/")) return database.getEntry(entryId)
        val packId = entryId.removePrefix("pack/").substringBefore('/')
        val pack = descriptor(packId) ?: return null
        val source = packDatabase(pack.headwordLanguage) ?: return null
        val entries = source.siblingEntryIds(entryId).mapNotNull(source::getEntry)
        if (entries.isEmpty()) return null
        if (entries.size == 1) return entries.first()
        val primary = entries.first()
        var nextSense = 1
        val senses = entries.flatMap { sibling ->
            sibling.senses.map { sense ->
                val order = nextSense++
                sense.copy(
                    order = order,
                    translations = sense.translations.map { it.copy(senseOrder = order) },
                )
            }
        }
        return primary.copy(
            partOfSpeech = entries.map { it.partOfSpeech }.distinct().joinToString(" · "),
            pronunciations = entries.flatMap { it.pronunciations }.distinct(),
            etymology = entries.mapNotNull { it.etymology }.distinct().joinToString("\n\n").ifBlank { null },
            senses = senses,
            forms = entries.flatMap { it.forms }.distinct(),
            generalTranslations = entries.flatMap { it.generalTranslations }.distinct(),
        )
    }

    private fun hasEntry(entryId: String): Boolean {
        if (!entryId.startsWith("pack/")) return database.hasEntry(entryId)
        val packId = entryId.removePrefix("pack/").substringBefore('/')
        val pack = descriptor(packId) ?: return false
        return packDatabase(pack.headwordLanguage)?.hasEntry(entryId) == true
    }

    private fun packDatabase(language: String): OfflinePackDatabase? = synchronized(packageDatabases) {
        packageDatabases[language] ?: packManager.openDatabase(language)?.also {
            packageDatabases[language] = it
        }
    }

    private fun closePackDatabase(language: String) = synchronized(packageDatabases) {
        packageDatabases.remove(language)?.close()
    }

    private fun <T> enumPreference(key: String, default: T, parser: (String) -> T): T =
        preferences.getString(key, null)?.let { runCatching { parser(it) }.getOrNull() } ?: default

    override fun onCleared() {
        synchronized(packageDatabases) {
            packageDatabases.values.forEach(OfflinePackDatabase::close)
            packageDatabases.clear()
        }
        database.close()
        super.onCleared()
    }

    companion object {
        private const val PREFERENCES = "linguawiki_preferences"
        private const val KEY_EDITIONS = "online_editions"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_PALETTE = "color_palette"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_LINE_SPACING = "line_spacing"
        private const val MAX_SUGGESTION_CACHE = 40
    }
}
