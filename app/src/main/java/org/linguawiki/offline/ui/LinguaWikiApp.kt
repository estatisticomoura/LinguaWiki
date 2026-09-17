package org.linguawiki.offline.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.linguawiki.offline.R
import org.linguawiki.offline.data.ColorPalette
import org.linguawiki.offline.data.DictionaryEntry
import org.linguawiki.offline.data.EntrySummary
import org.linguawiki.offline.data.FontScale
import org.linguawiki.offline.data.LineSpacing
import org.linguawiki.offline.data.MatchKind
import org.linguawiki.offline.data.OfflinePackError
import org.linguawiki.offline.data.OfflinePackState
import org.linguawiki.offline.data.OnlineEdition
import org.linguawiki.offline.data.OnlineSuggestion
import org.linguawiki.offline.data.SearchNormalizer
import org.linguawiki.offline.data.Sense
import org.linguawiki.offline.data.ThemeMode
import org.linguawiki.offline.data.Translation
import org.linguawiki.offline.data.WordForm
import java.text.Collator
import java.util.Locale

@Composable
fun LinguaWikiApp(viewModel: MainViewModel, state: MainUiState) {
    val context = LocalContext.current
    var textToSpeech by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        val engine = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) textToSpeech = null
        }
        textToSpeech = engine
        onDispose {
            engine.stop()
            engine.shutdown()
        }
    }

    fun speak(entry: DictionaryEntry) {
        val engine = textToSpeech
        if (engine == null) {
            Toast.makeText(context, context.getString(R.string.tts_unavailable), Toast.LENGTH_SHORT).show()
            return
        }
        val locale = Locale.forLanguageTag(entry.language).takeUnless { it.language.isBlank() } ?: Locale.ENGLISH
        val availability = engine.setLanguage(locale)
        if (availability == TextToSpeech.LANG_MISSING_DATA || availability == TextToSpeech.LANG_NOT_SUPPORTED) {
            Toast.makeText(context, context.getString(R.string.tts_unavailable), Toast.LENGTH_SHORT).show()
        } else {
            engine.speak(entry.lemma, TextToSpeech.QUEUE_FLUSH, null, "linguawiki-${entry.id}")
        }
    }

    BackHandler(enabled = state.screen == AppScreen.ENTRY || state.screen == AppScreen.ONLINE) {
        viewModel.goBack()
    }

    when (state.screen) {
        AppScreen.ENTRY -> state.selectedEntry?.let { entry ->
            EntryScreen(
                entry = entry,
                englishReferences = state.englishReferences,
                onBack = viewModel::goBack,
                onFavorite = viewModel::toggleFavorite,
                onSpeak = { speak(entry) },
                onOnline = { viewModel.openOnline(entry.lemma) },
                onTranslation = viewModel::openTranslation,
            )
        }

        AppScreen.ONLINE -> OnlineScreen(
            committedQuery = state.onlineQuery,
            draftQuery = state.onlineDraft,
            suggestions = state.onlineSuggestions,
            suggestionsLoading = state.onlineSuggestionLoading,
            suggestionsFailed = state.onlineSuggestionError,
            suggestionsExpanded = state.onlineSuggestionsExpanded,
            enabledEditions = viewModel.availableEditions.filter { it.code in state.onlineEditions },
            activeEditionCode = state.activeOnlineEdition,
            headwordLanguage = state.activeLanguage,
            onBack = viewModel::goBack,
            onQueryChange = viewModel::updateOnlineDraft,
            onSearch = viewModel::submitOnlineSearch,
            onEdition = viewModel::selectOnlineEdition,
            onRetrySuggestions = viewModel::retryOnlineSuggestions,
        )

        else -> MainScaffold(viewModel = viewModel, state = state)
    }
}

@Composable
private fun MainScaffold(viewModel: MainViewModel, state: MainUiState) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationItem(
                    selected = state.screen == AppScreen.SEARCH,
                    onClick = { viewModel.showScreen(AppScreen.SEARCH) },
                    icon = { Icon(Icons.Default.Search, null) },
                    label = stringResource(R.string.search),
                )
                NavigationItem(
                    selected = state.screen == AppScreen.FAVORITES,
                    onClick = { viewModel.showScreen(AppScreen.FAVORITES) },
                    icon = { Icon(Icons.Default.Star, null) },
                    label = stringResource(R.string.favorites),
                )
                NavigationItem(
                    selected = state.screen == AppScreen.HISTORY,
                    onClick = { viewModel.showScreen(AppScreen.HISTORY) },
                    icon = { Icon(Icons.Default.History, null) },
                    label = stringResource(R.string.history),
                )
                NavigationItem(
                    selected = state.screen == AppScreen.SETTINGS,
                    onClick = { viewModel.showScreen(AppScreen.SETTINGS) },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = stringResource(R.string.settings),
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.screen) {
                AppScreen.SEARCH -> SearchScreen(state, viewModel)
                AppScreen.FAVORITES -> CollectionScreen(
                    title = stringResource(R.string.favorites),
                    emptyMessage = stringResource(R.string.no_favorites),
                    entries = state.favorites,
                    onEntry = viewModel::openEntry,
                )
                AppScreen.HISTORY -> CollectionScreen(
                    title = stringResource(R.string.history),
                    emptyMessage = stringResource(R.string.no_history),
                    entries = state.history,
                    onEntry = viewModel::openEntry,
                    action = if (state.history.isNotEmpty()) {
                        {
                            IconButton(onClick = viewModel::clearHistory) {
                                Icon(Icons.Default.Delete, stringResource(R.string.clear_history))
                            }
                        }
                    } else null,
                )
                AppScreen.SETTINGS -> SettingsScreen(
                    state = state,
                    editions = viewModel.availableEditions,
                    onTheme = viewModel::setThemeMode,
                    onPalette = viewModel::setColorPalette,
                    onFontScale = viewModel::setFontScale,
                    onLineSpacing = viewModel::setLineSpacing,
                    onEdition = viewModel::toggleOnlineEdition,
                    onInstallPack = viewModel::installPack,
                    onRemovePack = viewModel::removePack,
                    onDismissPackError = viewModel::dismissPackError,
                    requiredPeakBytes = viewModel::requiredPeakBytes,
                )
                else -> Unit
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun RowScope.NavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        label = { Text(label) },
    )
}

@Composable
private fun SearchScreen(state: MainUiState, viewModel: MainViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Text(
                stringResource(R.string.dictionary_selector),
                style = MaterialTheme.typography.labelLarge,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                items(viewModel.installedDictionaryCodes, key = { it }) { language ->
                    FilterChip(
                        selected = state.activeLanguage == language,
                        onClick = { viewModel.selectLanguage(language) },
                        label = { Text("${flagForLanguage(language)} ${localizedLanguageName(language)}") },
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (state.query.isNotBlank()) {
                        IconButton(onClick = { viewModel.openOnline() }) {
                            Icon(Icons.Default.Language, stringResource(R.string.search_online))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { state.results.firstOrNull()?.let { viewModel.openEntry(it.id) } },
                ),
            )
        }

        if (state.query.isBlank()) {
            item { InformationCard(text = stringResource(R.string.search_welcome)) }
        } else if (!state.loading && state.results.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.no_results_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.no_results_body))
                        OutlinedButton(onClick = { viewModel.openOnline() }) {
                            Icon(Icons.Default.Language, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.online_cta), Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        } else {
            items(state.results, key = { it.id }) { entry ->
                EntrySummaryRow(entry = entry, onClick = { viewModel.openEntry(entry.id) })
            }
            if (state.results.isNotEmpty()) {
                item {
                    TextButton(onClick = { viewModel.openOnline() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Language, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.search_online), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun InformationCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, null)
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun EntrySummaryRow(entry: EntrySummary, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.lemma, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                    if (entry.favorite) {
                        Icon(
                            Icons.Default.Star,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 6.dp).size(17.dp),
                        )
                    }
                }
                Text(
                    buildString {
                        append(languageBadge(entry.language))
                        append(" · ")
                        append(entry.partsOfSpeech.joinToString(" · ") { it.replaceFirstChar(Char::uppercase) })
                        entry.ipa?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MatchDescription(entry)
            }
        }
    }
}

@Composable
private fun MatchDescription(entry: EntrySummary) {
    val text = when (entry.matchKind) {
        MatchKind.EXACT_LEMMA -> null
        MatchKind.EXACT_FORM -> entry.matchedSurface?.let { stringResource(R.string.match_form, it, entry.lemma) }
        MatchKind.DIACRITIC -> entry.matchedSurface?.let { stringResource(R.string.match_form, it, entry.lemma) }
            ?: stringResource(R.string.match_diacritic)
        MatchKind.PREFIX -> entry.matchedSurface?.let { stringResource(R.string.match_form, it, entry.lemma) }
            ?: stringResource(R.string.match_prefix)
        MatchKind.APPROXIMATE -> stringResource(R.string.match_approximate)
    }
    if (text != null) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.padding(top = 7.dp),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun CollectionScreen(
    title: String,
    emptyMessage: String,
    entries: List<EntrySummary>,
    onEntry: (String) -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                action?.invoke()
            }
        }
        if (entries.isEmpty()) {
            item { InformationCard(emptyMessage) }
        } else {
            items(entries, key = { it.id }) { entry ->
                EntrySummaryRow(entry = entry, onClick = { onEntry(entry.id) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(
    state: MainUiState,
    editions: List<OnlineEdition>,
    onTheme: (ThemeMode) -> Unit,
    onPalette: (ColorPalette) -> Unit,
    onFontScale: (FontScale) -> Unit,
    onLineSpacing: (LineSpacing) -> Unit,
    onEdition: (String) -> Unit,
    onInstallPack: (String) -> Unit,
    onRemovePack: (String) -> Unit,
    onDismissPackError: (String) -> Unit,
    requiredPeakBytes: (org.linguawiki.offline.data.OfflinePackDescriptor) -> Long,
) {
    var editionFilter by rememberSaveable { mutableStateOf("") }
    var pendingInstall by remember { mutableStateOf<OfflinePackState?>(null) }
    var pendingRemove by remember { mutableStateOf<OfflinePackState?>(null) }
    val displayLocale = LocalConfiguration.current.locales[0]
    val filterKey = SearchNormalizer.fold(editionFilter)
    val visibleEditions = editions
        .sortedBy { editionDisplayLabel(it, displayLocale).lowercase(displayLocale) }
        .filter { edition ->
            filterKey.isBlank() || SearchNormalizer.fold(
                "${edition.code} ${edition.nativeName} ${editionDisplayLabel(edition, displayLocale)}",
            ).contains(filterKey)
        }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold) }
        item { SectionTitle(stringResource(R.string.theme)) }
        items(ThemeMode.entries) { mode ->
            val label = when (mode) {
                ThemeMode.SYSTEM -> stringResource(R.string.system_default)
                ThemeMode.LIGHT -> stringResource(R.string.light)
                ThemeMode.DARK -> stringResource(R.string.dark)
                ThemeMode.HIGH_CONTRAST_LIGHT -> stringResource(R.string.high_contrast_light)
                ThemeMode.HIGH_CONTRAST_DARK -> stringResource(R.string.high_contrast_dark)
            }
            SettingChoice(label, selected = state.themeMode == mode, onClick = { onTheme(mode) })
        }
        item { SectionTitle(stringResource(R.string.color_palette)) }
        items(ColorPalette.entries) { palette ->
            val label = when (palette) {
                ColorPalette.GREEN -> stringResource(R.string.palette_green)
                ColorPalette.BLUE -> stringResource(R.string.palette_blue)
                ColorPalette.AMBER -> stringResource(R.string.palette_amber)
            }
            SettingChoice(label, selected = state.colorPalette == palette, onClick = { onPalette(palette) })
        }
        item { SectionTitle(stringResource(R.string.font_size)) }
        items(FontScale.entries) { scale ->
            SettingChoice(
                stringResource(R.string.font_size_percent, (scale.multiplier * 100).toInt()),
                selected = state.fontScale == scale,
                onClick = { onFontScale(scale) },
            )
        }
        item { SectionTitle(stringResource(R.string.line_spacing)) }
        items(LineSpacing.entries) { spacing ->
            val label = when (spacing) {
                LineSpacing.COMPACT -> stringResource(R.string.line_spacing_compact)
                LineSpacing.STANDARD -> stringResource(R.string.line_spacing_standard)
                LineSpacing.RELAXED -> stringResource(R.string.line_spacing_relaxed)
            }
            SettingChoice(label, selected = state.lineSpacing == spacing, onClick = { onLineSpacing(spacing) })
        }
        item {
            Spacer(Modifier.height(6.dp))
            SectionTitle(stringResource(R.string.offline_sources))
            Text(
                stringResource(R.string.offline_sources_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.offlinePacks.isEmpty()) {
            item { InformationCard(stringResource(R.string.no_offline_packages)) }
        } else {
            items(state.offlinePacks, key = { it.descriptor.packId }) { pack ->
                OfflinePackCard(
                    pack = pack,
                    displayLocale = displayLocale,
                    requiredPeakBytes = requiredPeakBytes(pack.descriptor),
                    onInstall = { pendingInstall = pack },
                    onRemove = { pendingRemove = pack },
                    onDismissError = { onDismissPackError(pack.descriptor.packId) },
                )
            }
        }
        item {
            Spacer(Modifier.height(6.dp))
            SectionTitle(stringResource(R.string.online_sources))
            Text(
                stringResource(R.string.online_sources_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                pluralStringResource(
                    R.plurals.online_sources_count,
                    state.onlineEditions.size,
                    state.onlineEditions.size,
                    editions.size,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
            OutlinedTextField(
                value = editionFilter,
                onValueChange = { editionFilter = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true,
                label = { Text(stringResource(R.string.filter_online_sources)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
            )
        }
        items(visibleEditions, key = { it.code }) { edition ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = edition.code in state.onlineEditions,
                    onCheckedChange = { onEdition(edition.code) },
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(editionDisplayLabel(edition, displayLocale))
                    Text(
                        edition.code.uppercase(Locale.ROOT),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (visibleEditions.isEmpty()) {
            item { InformationCard(stringResource(R.string.no_online_sources_match)) }
        }
        item {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.privacy))
            Text(stringResource(R.string.privacy_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SectionTitle(stringResource(R.string.prototype_data_title))
            Text(stringResource(R.string.prototype_data_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    pendingInstall?.let { pack ->
        val enoughSpace = pack.freeBytes >= requiredPeakBytes(pack.descriptor)
        AlertDialog(
            onDismissRequest = { pendingInstall = null },
            title = { Text(stringResource(R.string.download_dictionary_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.download_dictionary_confirmation,
                        languageDisplayName(pack.descriptor.headwordLanguage, displayLocale),
                        formatBytes(pack.descriptor.downloadBytes, displayLocale),
                        formatBytes(pack.descriptor.installedBytes, displayLocale),
                        formatBytes(requiredPeakBytes(pack.descriptor), displayLocale),
                        formatBytes(pack.freeBytes, displayLocale),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = enoughSpace,
                    onClick = {
                        onInstallPack(pack.descriptor.packId)
                        pendingInstall = null
                    },
                ) { Text(stringResource(R.string.download)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingInstall = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    pendingRemove?.let { pack ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text(stringResource(R.string.remove_dictionary_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.remove_dictionary_confirmation,
                        languageDisplayName(pack.descriptor.headwordLanguage, displayLocale),
                        formatBytes(pack.descriptor.installedBytes, displayLocale),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemovePack(pack.descriptor.packId)
                        pendingRemove = null
                    },
                ) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun OfflinePackCard(
    pack: OfflinePackState,
    displayLocale: Locale,
    requiredPeakBytes: Long,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
    onDismissError: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${flagForLanguage(pack.descriptor.headwordLanguage)} ${languageDisplayName(pack.descriptor.headwordLanguage, displayLocale)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.package_version, pack.descriptor.version),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(if (pack.installed) R.string.installed else R.string.available),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                stringResource(
                    R.string.package_statistics,
                    String.format(displayLocale, "%,d", pack.descriptor.entryCount),
                    String.format(displayLocale, "%,d", pack.descriptor.translationCount),
                    String.format(displayLocale, "%,d", pack.descriptor.formCount),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(
                    R.string.package_sizes,
                    formatBytes(pack.descriptor.downloadBytes, displayLocale),
                    formatBytes(pack.descriptor.installedBytes, displayLocale),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!pack.installed) {
                Text(
                    stringResource(
                        R.string.package_space,
                        formatBytes(requiredPeakBytes, displayLocale),
                        formatBytes(pack.freeBytes, displayLocale),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (pack.downloading) {
                LinearProgressIndicator(
                    progress = { pack.progress ?: 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.downloading_percent, ((pack.progress ?: 0f) * 100).toInt()),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            pack.error?.let { error ->
                Text(
                    offlinePackErrorText(error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onDismissError) { Text(stringResource(R.string.close)) }
            }
            if (!pack.downloading) {
                OutlinedButton(
                    onClick = if (pack.installed) onRemove else onInstall,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(if (pack.installed) R.string.remove else R.string.download))
                }
            }
            Text(
                stringResource(R.string.package_license, pack.descriptor.licenses.joinToString(", ")),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun offlinePackErrorText(error: OfflinePackError): String = when (error) {
    OfflinePackError.NOT_ENOUGH_SPACE -> stringResource(R.string.package_error_space)
    OfflinePackError.NETWORK -> stringResource(R.string.package_error_network)
    OfflinePackError.INVALID_PACKAGE -> stringResource(R.string.package_error_invalid)
    OfflinePackError.INSTALLATION -> stringResource(R.string.package_error_installation)
    OfflinePackError.REMOVAL -> stringResource(R.string.package_error_removal)
}

private fun formatBytes(bytes: Long, locale: Locale): String {
    val mib = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mib >= 1024) {
        String.format(locale, "%.2f GB", mib / 1024.0)
    } else {
        String.format(locale, "%.1f MB", mib)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SettingChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
            RadioButton(selected = selected, onClick = onClick)
            Text(label, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EntryScreen(
    entry: DictionaryEntry,
    englishReferences: List<DictionaryEntry>,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onSpeak: () -> Unit,
    onOnline: () -> Unit,
    onTranslation: (String, String, String?, String?) -> Unit,
) {
    var showForms by remember(entry.id) { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.offline_dictionary)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(onClick = onOnline) { Icon(Icons.Default.Language, stringResource(R.string.search_online)) }
                    IconButton(onClick = onFavorite) {
                        Icon(
                            if (entry.favorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                            if (entry.favorite) stringResource(R.string.remove_favorite) else stringResource(R.string.add_favorite),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    stringResource(
                        R.string.primary_edition_title,
                        localizedLanguageName(entry.edition),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(entry.lemma, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                Text(
                    partOfSpeechLabel(entry.partOfSpeech),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entry.pronunciations.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.pronunciation), style = MaterialTheme.typography.labelLarge)
                            entry.pronunciations.forEach { pronunciation ->
                                Text(
                                    pronunciation.ipa + pronunciation.labels.takeIf { it.isNotEmpty() }?.joinToString(", ", " — ").orEmpty(),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            }
                        }
                        OutlinedButton(onClick = onSpeak) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.speak), Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
            entry.etymology?.let { etymology ->
                item {
                    SectionTitle(stringResource(R.string.etymology))
                    Text(etymology, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (entry.forms.isNotEmpty()) {
                item {
                    OutlinedButton(onClick = { showForms = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(18.dp))
                        Text(
                            stringResource(
                                if (entry.inflectionKind == "conjugation") R.string.conjugation else R.string.declension,
                            ),
                            Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.meanings)) }
            items(entry.senses, key = { it.order }) { sense ->
                SenseCard(sense = sense)
            }
            val allTranslations = entry.senses.flatMap { it.translations } + entry.generalTranslations
            if (allTranslations.isNotEmpty()) {
                item {
                    TranslationSection(allTranslations, entry.edition, onTranslation)
                }
            }
            if (entry.edition != "en" && !entry.id.startsWith("pack/")) {
                item {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    SectionTitle(stringResource(R.string.english_reference_title))
                    Text(
                        stringResource(R.string.english_reference_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (englishReferences.isEmpty()) {
                    item { InformationCard(stringResource(R.string.english_reference_missing)) }
                } else {
                    items(englishReferences, key = { "english-reference-${it.id}" }) { reference ->
                        EnglishReferenceCard(reference)
                    }
                }
            }
            item {
                Text(
                    stringResource(
                        if (entry.id.startsWith("pack/")) R.string.source_wiktionary else R.string.source_note,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                )
            }
        }
    }

    if (showForms) {
        AlertDialog(
            onDismissRequest = { showForms = false },
            title = {
                Text(
                    stringResource(
                        if (entry.inflectionKind == "conjugation") R.string.conjugation else R.string.declension,
                    ),
                )
            },
            text = {
                LazyColumn(Modifier.heightIn(max = 470.dp)) {
                    val groups = entry.forms.groupBy(::formGroup)
                    groups.forEach { (group, forms) ->
                        item { Text(group, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) }
                        items(forms) { form ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(formPronoun(entry.language, form), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(.42f))
                                Text(form.surface, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(.58f))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showForms = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
private fun EnglishReferenceCard(entry: DictionaryEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    partOfSpeechLabel(entry.partOfSpeech),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                entry.ipa?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            entry.senses.forEach { sense ->
                Text("${sense.order}. ${sense.definition}", style = MaterialTheme.typography.bodyLarge)
                sense.examples.forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            entry.etymology?.let {
                Text(stringResource(R.string.etymology), style = MaterialTheme.typography.labelLarge)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SenseCard(sense: Sense) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${sense.order}.", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(sense.definition, style = MaterialTheme.typography.bodyLarge)
            sense.examples.forEach {
                Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TranslationSection(
    translations: List<Translation>,
    dictionaryLanguage: String,
    onTranslation: (String, String, String?, String?) -> Unit,
) {
    val locale = Locale.forLanguageTag(dictionaryLanguage)
    val collator = remember(locale) { Collator.getInstance(locale) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(stringResource(R.string.translations))
        translations.groupBy { it.senseOrder ?: 0 }.toSortedMap().forEach { (senseOrder, senseTranslations) ->
            if (senseOrder > 0) Text(senseOrder.toString() + ".", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            senseTranslations.groupBy { it.language }.entries
                .sortedWith { a, b -> collator.compare(languageDisplayName(a.key, locale, a.value.first().languageName), languageDisplayName(b.key, locale, b.value.first().languageName)) }
                .forEach { (language, values) ->
                    Column {
                        Text(languageDisplayName(language, locale, values.first().languageName), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            values.distinctBy { it.term }.forEach { translation ->
                                AssistChip(
                                    onClick = { onTranslation(translation.language, translation.term, translation.targetLemma, translation.targetEntryId) },
                                    label = { Text(translation.term) },
                                )
                            }
                        }
                    }
                }
        }
    }
}

private fun languageDisplayName(code: String, locale: Locale, fallback: String): String {
    val display = Locale.forLanguageTag(code).getDisplayLanguage(locale)
    return display.takeIf { it.isNotBlank() && !it.equals(code, true) } ?: fallback
}

private fun formGroup(form: WordForm): String {
    val structural = listOf("indicative", "subjunctive", "imperative", "conditional", "present", "past", "future", "perfect", "imperfect", "pluperfect", "infinitive", "participle", "gerund")
    return (form.tags + form.rawTags).filter { tag -> structural.any { tag.contains(it, ignoreCase = true) } }.joinToString(" · ").ifBlank { form.label.ifBlank { "Forms" } }
}

private fun formPronoun(language: String, form: WordForm): String {
    val tags = (form.tags + form.rawTags).map { it.lowercase() }
    val person = when {
        tags.any { it == "first-person" || it == "1" || it.startsWith("1st") } -> 1
        tags.any { it == "second-person" || it == "2" || it.startsWith("2nd") } -> 2
        tags.any { it == "third-person" || it == "3" || it.startsWith("3rd") } -> 3
        else -> 0
    }
    val plural = tags.any { it == "plural" }
    val pronouns = when (language) {
        "pt" -> listOf("eu", "tu", "ele/ela/você", "nós", "vós", "eles/elas/vocês")
        "en" -> listOf("I", "you", "he/she/it", "we", "you", "they")
        "de" -> listOf("ich", "du", "er/sie/es", "wir", "ihr", "sie")
        "es" -> listOf("yo", "tú", "él/ella/usted", "nosotros", "vosotros", "ellos/ellas/ustedes")
        "fr" -> listOf("je", "tu", "il/elle/on", "nous", "vous", "ils/elles")
        "it" -> listOf("io", "tu", "lui/lei", "noi", "voi", "loro")
        "pl" -> listOf("ja", "ty", "on/ona/ono", "my", "wy", "oni/one")
        "nl" -> listOf("ik", "jij", "hij/zij/het", "wij", "jullie", "zij")
        "ru" -> listOf("я", "ты", "он/она/оно", "мы", "вы", "они")
        "cs" -> listOf("já", "ty", "on/ona/ono", "my", "vy", "oni/ony/ona")
        "el" -> listOf("εγώ", "εσύ", "αυτός/αυτή/αυτό", "εμείς", "εσείς", "αυτοί/αυτές/αυτά")
        "tr" -> listOf("ben", "sen", "o", "biz", "siz", "onlar")
        "id", "ms" -> listOf("saya", "kamu", "dia", "kami", "kalian", "mereka")
        "vi" -> listOf("tôi", "bạn", "anh ấy/cô ấy", "chúng tôi", "các bạn", "họ")
        "zh" -> listOf("我", "你", "他/她", "我们", "你们", "他们/她们")
        "ja" -> listOf("私", "あなた", "彼/彼女", "私たち", "あなたたち", "彼ら/彼女ら")
        "ko" -> listOf("나", "너", "그/그녀", "우리", "너희", "그들")
        else -> emptyList()
    }
    if (person in 1..3 && pronouns.size == 6) return pronouns[(if (plural) 3 else 0) + person - 1]
    return form.label.ifBlank { (form.tags + form.rawTags).joinToString(" · ").ifBlank { "—" } }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun OnlineScreen(
    committedQuery: String,
    draftQuery: String,
    suggestions: List<OnlineSuggestion>,
    suggestionsLoading: Boolean,
    suggestionsFailed: Boolean,
    suggestionsExpanded: Boolean,
    enabledEditions: List<OnlineEdition>,
    activeEditionCode: String,
    headwordLanguage: String,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onEdition: (String) -> Unit,
    onRetrySuggestions: () -> Unit,
) {
    val editions = enabledEditions.ifEmpty {
        listOf(OnlineEdition("en", "English", "https://en.wiktionary.org"))
    }
    val displayLocale = LocalConfiguration.current.locales[0]
    val focusManager = LocalFocusManager.current
    val selected = editions.firstOrNull { it.code == activeEditionCode } ?: editions.first()
    var loadFailed by remember(selected.code, committedQuery) { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val encoded = Uri.encode(committedQuery.replace(' ', '_'))
    val url = "${selected.baseUrl}/wiki/$encoded${wiktionaryLanguageSection(selected.code, headwordLanguage)}"
    val submitSearch = {
        if (draftQuery.isNotBlank()) {
            focusManager.clearFocus()
            onSearch(draftQuery)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.online_title))
                        Text(committedQuery, style = MaterialTheme.typography.labelMedium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                stringResource(R.string.online_explanation),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = draftQuery,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
                label = { Text(stringResource(R.string.online_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    IconButton(onClick = submitSearch) {
                        Icon(Icons.Default.Language, stringResource(R.string.online_open_entry))
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
            )
            if (suggestionsLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            if (suggestionsFailed) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.online_suggestions_error),
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = onRetrySuggestions) { Text(stringResource(R.string.retry)) }
                    }
                }
            }
            if (suggestionsExpanded && suggestions.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .heightIn(max = 240.dp),
                ) {
                    LazyColumn {
                        items(suggestions, key = { it.title }) { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        focusManager.clearFocus()
                                        onSearch(suggestion.title)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Column(Modifier.padding(start = 12.dp)) {
                                    Text(suggestion.title, style = MaterialTheme.typography.bodyLarge)
                                    if (suggestion.approximate) {
                                        Text(
                                            stringResource(R.string.online_suggestion_approximate),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(editions, key = { it.code }) { edition ->
                    FilterChip(
                        selected = selected.code == edition.code,
                        onClick = { onEdition(edition.code) },
                        label = { Text(editionDisplayLabel(edition, displayLocale)) },
                    )
                }
            }
            if (loadFailed) {
                InformationCard(stringResource(R.string.wiktionary_error))
            }
            AndroidWebView(
                url = url,
                onCreated = { webView = it },
                onFailure = { loadFailed = true },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AndroidWebView(
    url: String,
    onCreated: (WebView) -> Unit,
    onFailure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.mediaPlaybackRequiresUserGesture = false
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val host = request?.url?.host.orEmpty()
                        return !host.endsWith("wiktionary.org")
                    }

                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        if (request?.isForMainFrame == true) onFailure()
                    }
                }
                onCreated(this)
            }
        },
        update = { view ->
            if (view.tag != url) {
                view.tag = url
                view.loadUrl(url)
            }
        },
    )
}

@Composable
private fun localizedLanguageName(language: String): String =
    languageDisplayName(language, LocalConfiguration.current.locales[0])

internal fun wiktionaryLanguageSection(edition: String, headwordLanguage: String): String =
    if (edition == "en" && headwordLanguage == "pl") "#Polish" else ""

private fun editionDisplayLabel(edition: OnlineEdition, displayLocale: Locale): String {
    val localized = languageDisplayName(edition.code, displayLocale)
    return if (SearchNormalizer.fold(localized) == SearchNormalizer.fold(edition.nativeName)) {
        localized
    } else {
        "$localized · ${edition.nativeName}"
    }
}

private fun languageDisplayName(language: String, displayLocale: Locale): String {
    val locale = Locale.forLanguageTag(language.replace('_', '-'))
    val raw = locale.getDisplayLanguage(displayLocale)
        .takeIf { it.isNotBlank() && !it.equals(language, ignoreCase = true) }
        ?: language.uppercase(Locale.ROOT)
    return raw.substring(0, 1).uppercase(displayLocale) + raw.substring(1)
}

private fun flagForLanguage(language: String): String = when (language) {
    "en" -> "🇬🇧"
    "pt" -> "🇧🇷"
    "pl" -> "🇵🇱"
    else -> "🌐"
}

private fun languageBadge(language: String): String = when (language) {
    "en" -> "EN"
    "pt" -> "PT"
    "pl" -> "PL"
    "de" -> "DE"
    "fr" -> "FR"
    "it" -> "IT"
    "es" -> "ES"
    "ru" -> "RU"
    else -> language.uppercase(Locale.ROOT)
}

@Composable
private fun partOfSpeechLabel(partOfSpeech: String): String = when (partOfSpeech) {
    "noun" -> stringResource(R.string.noun)
    "verb" -> stringResource(R.string.verb)
    "adjective" -> stringResource(R.string.adjective)
    "adverb" -> stringResource(R.string.adverb)
    else -> stringResource(R.string.other)
}
