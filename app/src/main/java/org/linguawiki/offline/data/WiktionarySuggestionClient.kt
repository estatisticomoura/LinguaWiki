package org.linguawiki.offline.data

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class WiktionarySuggestionClient {
    fun suggestions(
        edition: OnlineEdition,
        rawQuery: String,
        limit: Int = DEFAULT_LIMIT,
    ): List<OnlineSuggestion> {
        val query = rawQuery.trim()
        if (query.isEmpty()) return emptyList()
        val safeLimit = limit.coerceIn(1, MAX_LIMIT)
        val prefix = parsePrefixResponse(request(buildPrefixEndpoint(edition, query, safeLimit)), query)
        if (prefix.size >= FALLBACK_THRESHOLD || query.length < 3) return prefix.take(safeLimit)

        val fallback = parseFallbackResponse(
            request(buildFallbackEndpoint(edition, query, safeLimit, diacriticVariants(query))),
            query,
        )
        return merge(prefix, fallback).take(safeLimit)
    }

    internal fun buildPrefixEndpoint(edition: OnlineEdition, query: String, limit: Int): URL {
        validateEdition(edition)
        return apiUrl(
            edition,
            listOf(
                "action" to "query",
                "list" to "prefixsearch",
                "pssearch" to query,
                "pslimit" to limit.toString(),
                "psnamespace" to "0",
                "format" to "json",
                "formatversion" to "2",
                "maxlag" to "5",
            ),
        )
    }

    internal fun buildFallbackEndpoint(
        edition: OnlineEdition,
        query: String,
        limit: Int,
        variants: List<String>,
    ): URL {
        validateEdition(edition)
        val parameters = mutableListOf(
            "action" to "query",
            "list" to "search",
            "srsearch" to query,
            "srnamespace" to "0",
            "srlimit" to limit.toString(),
            "srinfo" to "suggestion",
            "format" to "json",
            "formatversion" to "2",
            "redirects" to "1",
            "maxlag" to "5",
        )
        variants.take(MAX_VARIANTS).takeIf { it.isNotEmpty() }?.let {
            parameters += "titles" to it.joinToString("|")
        }
        return apiUrl(edition, parameters)
    }

    /** Kept for source compatibility with the first prototype tests. */
    internal fun buildEndpoint(edition: OnlineEdition, query: String, limit: Int): URL =
        buildPrefixEndpoint(edition, query, limit)

    internal fun parsePrefixResponse(json: String, query: String): List<OnlineSuggestion> {
        val array = JSONObject(json)
            .optJSONObject("query")
            ?.optJSONArray("prefixsearch")
            ?: return emptyList()
        return buildList {
            val seen = mutableSetOf<String>()
            for (index in 0 until array.length()) {
                val title = array.optJSONObject(index)?.optString("title").orEmpty().trim()
                addSuggestion(title, query, seen)
            }
        }
    }

    internal fun parseFallbackResponse(json: String, query: String): List<OnlineSuggestion> {
        val queryObject = JSONObject(json).optJSONObject("query") ?: return emptyList()
        return buildList {
            val seen = mutableSetOf<String>()
            queryObject.optJSONArray("pages")?.let { pages ->
                for (index in 0 until pages.length()) {
                    val page = pages.optJSONObject(index) ?: continue
                    if (page.optBoolean("missing", false)) continue
                    addSuggestion(page.optString("title").trim(), query, seen)
                }
            }
            queryObject.optJSONObject("searchinfo")
                ?.optString("suggestion")
                ?.trim()
                ?.let { addSuggestion(it, query, seen) }
            queryObject.optJSONArray("search")?.let { results ->
                for (index in 0 until results.length()) {
                    val title = results.optJSONObject(index)?.optString("title").orEmpty().trim()
                    addSuggestion(title, query, seen)
                }
            }
        }
    }

    internal fun diacriticVariants(query: String): List<String> {
        val variants = linkedSetOf<String>()
        preferredVariant(query, POLISH_DIACRITICS)?.let(variants::add)
        portugueseSuffixVariant(query)?.let(variants::add)
        expandVariants(query, POLISH_DIACRITICS, variants)
        expandVariants(query, PORTUGUESE_DIACRITICS, variants)
        variants.remove(query)
        return variants.take(MAX_VARIANTS)
    }

    private fun preferredVariant(input: String, replacements: Map<Char, List<Char>>): String? {
        val value = buildString {
            input.forEach { character -> append(replacements[character]?.firstOrNull() ?: character) }
        }
        return value.takeIf { it != input }
    }

    private fun portugueseSuffixVariant(input: String): String? {
        val value = when {
            input.endsWith("coes", ignoreCase = true) -> input.dropLast(4) +
                if (input.takeLast(4).first().isUpperCase()) "ÇÕES" else "ções"
            input.endsWith("cao", ignoreCase = true) -> input.dropLast(3) +
                if (input.takeLast(3).first().isUpperCase()) "ÇÃO" else "ção"
            input.endsWith("ao", ignoreCase = true) -> input.dropLast(2) +
                if (input.takeLast(2).first().isUpperCase()) "ÃO" else "ão"
            else -> return null
        }
        return value.takeIf { it != input }
    }

    private fun MutableList<OnlineSuggestion>.addSuggestion(
        title: String,
        query: String,
        seen: MutableSet<String>,
    ) {
        val key = title.lowercase(Locale.ROOT)
        if (title.isBlank() || !seen.add(key)) return
        val foldedQuery = SearchNormalizer.fold(query)
        val foldedTitle = SearchNormalizer.fold(title)
        add(OnlineSuggestion(title, approximate = !foldedTitle.startsWith(foldedQuery)))
    }

    private fun expandVariants(
        input: String,
        replacements: Map<Char, List<Char>>,
        target: MutableSet<String>,
    ) {
        var current = linkedSetOf(input)
        for (index in input.indices) {
            val alternatives = replacements[input[index]] ?: continue
            val expanded = LinkedHashSet(current)
            for (candidate in current) {
                for (replacement in alternatives) {
                    expanded += candidate.replaceRange(index, index + 1, replacement.toString())
                    if (expanded.size >= MAX_VARIANTS + 1) break
                }
                if (expanded.size >= MAX_VARIANTS + 1) break
            }
            current = expanded
            if (current.size >= MAX_VARIANTS + 1) break
        }
        target += current
    }

    private fun merge(first: List<OnlineSuggestion>, second: List<OnlineSuggestion>) = buildList {
        val seen = mutableSetOf<String>()
        (first + second).forEach { suggestion ->
            if (seen.add(suggestion.title.lowercase(Locale.ROOT))) add(suggestion)
        }
    }

    private fun request(endpoint: URL): String {
        var lastFailure: IOException? = null
        repeat(2) {
            try {
                return requestOnce(endpoint)
            } catch (error: IOException) {
                lastFailure = error
            }
        }
        throw lastFailure ?: IOException("Wiktionary suggestion request failed")
    }

    private fun requestOnce(endpoint: URL): String {
        val connection = endpoint.openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val response = connection.responseCode
            if (response !in 200..299) throw IOException("Wiktionary returned HTTP $response")
            if (connection.url.protocol != "https" || !isOfficialHost(connection.url.host)) {
                throw IOException("Unexpected redirect outside Wiktionary")
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun validateEdition(edition: OnlineEdition) {
        val base = URL(edition.baseUrl)
        require(base.protocol == "https" && isOfficialHost(base.host)) {
            "Only official HTTPS Wiktionary editions are accepted"
        }
    }

    private fun isOfficialHost(host: String): Boolean =
        host == "wiktionary.org" || host.endsWith(".wiktionary.org")

    private fun apiUrl(edition: OnlineEdition, parameters: List<Pair<String, String>>): URL {
        val query = parameters.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        return URL("${edition.baseUrl}/w/api.php?$query")
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        private const val DEFAULT_LIMIT = 12
        private const val MAX_LIMIT = 20
        private const val FALLBACK_THRESHOLD = 8
        private const val MAX_VARIANTS = 50
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 7_000
        private const val USER_AGENT = "LinguaWiki/0.4 (Android dictionary prototype)"

        private val POLISH_DIACRITICS = mapOf(
            'a' to listOf('ą'), 'A' to listOf('Ą'), 'c' to listOf('ć'), 'C' to listOf('Ć'),
            'e' to listOf('ę'), 'E' to listOf('Ę'), 'l' to listOf('ł'), 'L' to listOf('Ł'),
            'n' to listOf('ń'), 'N' to listOf('Ń'), 'o' to listOf('ó'), 'O' to listOf('Ó'),
            's' to listOf('ś'), 'S' to listOf('Ś'), 'z' to listOf('ź', 'ż'), 'Z' to listOf('Ź', 'Ż'),
        )

        private val PORTUGUESE_DIACRITICS = mapOf(
            'a' to listOf('ã', 'á', 'à', 'â'), 'A' to listOf('Ã', 'Á', 'À', 'Â'),
            'c' to listOf('ç'), 'C' to listOf('Ç'), 'e' to listOf('é', 'ê'), 'E' to listOf('É', 'Ê'),
            'i' to listOf('í'), 'I' to listOf('Í'), 'o' to listOf('õ', 'ó', 'ô'), 'O' to listOf('Õ', 'Ó', 'Ô'),
            'u' to listOf('ú'), 'U' to listOf('Ú'),
        )
    }
}
