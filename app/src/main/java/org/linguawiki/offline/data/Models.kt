package org.linguawiki.offline.data

enum class MatchKind {
    EXACT_LEMMA,
    EXACT_FORM,
    DIACRITIC,
    PREFIX,
    APPROXIMATE,
}

data class EntrySummary(
    val id: String,
    val edition: String,
    val language: String,
    val lemma: String,
    val partOfSpeech: String,
    val ipa: String?,
    val matchedSurface: String? = null,
    val matchKind: MatchKind = MatchKind.EXACT_LEMMA,
    val favorite: Boolean = false,
    val entryIds: List<String> = listOf(id),
    val partsOfSpeech: List<String> = listOf(partOfSpeech),
    val contentScore: Long = 0,
)

data class Translation(
    val language: String,
    val languageName: String = language,
    val term: String,
    val targetLemma: String?,
    val targetEntryId: String?,
    val senseOrder: Int? = null,
    val senseLabel: String? = null,
    val tags: List<String> = emptyList(),
)

data class Pronunciation(val ipa: String, val labels: List<String> = emptyList())

data class Sense(
    val order: Int,
    val definition: String,
    val examples: List<String>,
    val translations: List<Translation>,
)

data class WordForm(
    val surface: String,
    val label: String,
    val tags: List<String> = emptyList(),
    val rawTags: List<String> = emptyList(),
)

data class DictionaryEntry(
    val id: String,
    val edition: String,
    val language: String,
    val lemma: String,
    val partOfSpeech: String,
    val ipa: String?,
    val pronunciations: List<Pronunciation> = ipa?.let { listOf(Pronunciation(it)) }.orEmpty(),
    val etymology: String?,
    val inflectionKind: String?,
    val grammaticalFeatures: List<String> = emptyList(),
    val senses: List<Sense>,
    val forms: List<WordForm>,
    val generalTranslations: List<Translation> = emptyList(),
    val favorite: Boolean,
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    HIGH_CONTRAST_LIGHT,
    HIGH_CONTRAST_DARK,
}

enum class ColorPalette {
    GREEN,
    BLUE,
    AMBER,
}

enum class FontScale(val multiplier: Float) {
    SMALL(0.90f),
    DEFAULT(1.00f),
    LARGE(1.15f),
    EXTRA_LARGE(1.30f),
    MAXIMUM(1.50f),
}

enum class LineSpacing(val multiplier: Float) {
    COMPACT(0.92f),
    STANDARD(1.00f),
    RELAXED(1.18f),
}

data class OnlineEdition(
    val code: String,
    val nativeName: String,
    val baseUrl: String,
)

data class OnlineSuggestion(
    val title: String,
    val approximate: Boolean,
)

data class OfflinePackDescriptor(
    val packId: String,
    val version: String,
    val headwordLanguage: String,
    val definitionLanguage: String = headwordLanguage,
    val primaryEdition: String,
    val displayName: String,
    val downloadBytes: Long,
    val installedBytes: Long,
    val entryCount: Long,
    val senseCount: Long,
    val translationCount: Long,
    val formCount: Long,
    val sha256: String,
    val url: String,
    val licenses: List<String>,
)

enum class OfflinePackError {
    NOT_ENOUGH_SPACE,
    NETWORK,
    INVALID_PACKAGE,
    INSTALLATION,
    REMOVAL,
}

class OfflinePackException(
    val reason: OfflinePackError,
    cause: Throwable? = null,
) : Exception(reason.name, cause)

data class OfflinePackState(
    val descriptor: OfflinePackDescriptor,
    val installed: Boolean,
    val downloading: Boolean = false,
    val progress: Float? = null,
    val error: OfflinePackError? = null,
    val freeBytes: Long = 0,
)
