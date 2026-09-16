package org.linguawiki.offline.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import java.io.Closeable
import java.io.File

class OfflinePackDatabase(
    file: File,
    private val descriptor: OfflinePackDescriptor,
) : Closeable {
    private val database = SQLiteDatabase.openDatabase(
        file.absolutePath,
        null,
        SQLiteDatabase.OPEN_READONLY,
    )

    fun search(rawQuery: String, limit: Int = 30): List<EntrySummary> {
        val query = SearchNormalizer.canonical(rawQuery)
        if (query.isBlank()) return emptyList()
        val folded = SearchNormalizer.fold(query)
        val matches = linkedMapOf<String, RankedSummary>()

        addEntryMatches("e.lemma_key = ?", arrayOf(query), MatchKind.EXACT_LEMMA, 0, matches)
        addFormMatches("f.surface_key = ?", arrayOf(query), MatchKind.EXACT_FORM, 1, matches)
        addEntryMatches("e.folded_key = ?", arrayOf(folded), MatchKind.DIACRITIC, 2, matches)
        addFormMatches("f.folded_key = ?", arrayOf(folded), MatchKind.DIACRITIC, 3, matches)

        val prefix = escapeLike(folded) + "%"
        addEntryMatches("e.folded_key LIKE ? ESCAPE '\\'", arrayOf(prefix), MatchKind.PREFIX, 4, matches)
        addFormMatches("f.folded_key LIKE ? ESCAPE '\\'", arrayOf(prefix), MatchKind.PREFIX, 5, matches)

        if (matches.size < 8 && folded.length >= 3) {
            val threshold = if (folded.length >= 8) 3 else 2
            database.rawQuery(
                """
                SELECT e.stable_id, e.lemma, e.part_of_speech, e.ipa, e.folded_key
                FROM entries e
                WHERE ABS(LENGTH(e.folded_key) - ?) <= ?
                ORDER BY e.lemma
                LIMIT 1000
                """.trimIndent(),
                arrayOf(folded.length.toString(), threshold.toString()),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val distance = SearchNormalizer.levenshtein(folded, cursor.getString(4))
                    if (distance <= threshold) {
                        putRanked(matches, cursor.toSummary(MatchKind.APPROXIMATE), 10 + distance)
                    }
                }
            }
        }

        return matches.values
            .sortedWith(compareBy<RankedSummary> { it.rank }.thenBy { it.summary.lemma })
            .take(limit)
            .map { it.summary }
    }

    fun findExact(lemma: String): String? = database.rawQuery(
        "SELECT stable_id FROM entries WHERE lemma_key = ? ORDER BY id LIMIT 1",
        arrayOf(SearchNormalizer.canonical(lemma)),
    ).use { cursor ->
        if (cursor.moveToFirst()) externalId(cursor.getString(0)) else null
    }

    fun hasEntry(entryId: String): Boolean {
        val stableId = stableId(entryId) ?: return false
        return database.rawQuery(
            "SELECT 1 FROM entries WHERE stable_id = ?",
            arrayOf(stableId),
        ).use { it.moveToFirst() }
    }

    fun getEntry(entryId: String): DictionaryEntry? {
        val stableId = stableId(entryId) ?: return null
        val base = database.rawQuery(
            """
            SELECT id, stable_id, lemma, part_of_speech, ipa, etymology, inflection_kind
            FROM entries WHERE stable_id = ?
            """.trimIndent(),
            arrayOf(stableId),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            EntryBase(
                internalId = cursor.getLong(0),
                stableId = cursor.getString(1),
                lemma = cursor.getString(2),
                partOfSpeech = cursor.getString(3),
                ipa = cursor.nullableString(4),
                etymology = cursor.nullableString(5),
                inflectionKind = cursor.nullableString(6),
            )
        }

        val senses = mutableListOf<Sense>()
        database.rawQuery(
            """
            SELECT id, sense_order, definition, examples_json
            FROM senses WHERE entry_id = ? ORDER BY sense_order
            """.trimIndent(),
            arrayOf(base.internalId.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val senseId = cursor.getLong(0)
                val translations = mutableListOf<Translation>()
                database.rawQuery(
                    "SELECT language, term FROM translations WHERE sense_id = ? ORDER BY language, term",
                    arrayOf(senseId.toString()),
                ).use { translationCursor ->
                    while (translationCursor.moveToNext()) {
                        val term = translationCursor.getString(1)
                        translations += Translation(
                            language = translationCursor.getString(0),
                            term = term,
                            targetLemma = term,
                            targetEntryId = null,
                        )
                    }
                }
                senses += Sense(
                    order = cursor.getInt(1),
                    definition = cursor.getString(2),
                    examples = parseExamples(cursor.nullableString(3)),
                    translations = translations,
                )
            }
        }

        val forms = mutableListOf<WordForm>()
        database.rawQuery(
            "SELECT surface, label FROM forms WHERE entry_id = ? ORDER BY id",
            arrayOf(base.internalId.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                forms += WordForm(cursor.getString(0), cursor.getString(1))
            }
        }

        return DictionaryEntry(
            id = externalId(base.stableId),
            edition = descriptor.primaryEdition,
            language = descriptor.headwordLanguage,
            lemma = base.lemma,
            partOfSpeech = base.partOfSpeech,
            ipa = base.ipa,
            etymology = base.etymology,
            inflectionKind = base.inflectionKind,
            senses = senses,
            forms = forms,
            favorite = false,
        )
    }

    override fun close() {
        database.close()
    }

    private fun addEntryMatches(
        condition: String,
        args: Array<String>,
        kind: MatchKind,
        rank: Int,
        target: MutableMap<String, RankedSummary>,
    ) {
        database.rawQuery(
            """
            SELECT e.stable_id, e.lemma, e.part_of_speech, e.ipa
            FROM entries e WHERE $condition ORDER BY e.lemma LIMIT 50
            """.trimIndent(),
            args,
        ).use { cursor ->
            while (cursor.moveToNext()) putRanked(target, cursor.toSummary(kind), rank)
        }
    }

    private fun addFormMatches(
        condition: String,
        args: Array<String>,
        kind: MatchKind,
        rank: Int,
        target: MutableMap<String, RankedSummary>,
    ) {
        database.rawQuery(
            """
            SELECT e.stable_id, e.lemma, e.part_of_speech, e.ipa, f.surface
            FROM forms f JOIN entries e ON e.id = f.entry_id
            WHERE $condition ORDER BY e.lemma LIMIT 50
            """.trimIndent(),
            args,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                putRanked(target, cursor.toSummary(kind, cursor.getString(4)), rank)
            }
        }
    }

    private fun Cursor.toSummary(kind: MatchKind, matchedSurface: String? = null) = EntrySummary(
        id = externalId(getString(0)),
        edition = descriptor.primaryEdition,
        language = descriptor.headwordLanguage,
        lemma = getString(1),
        partOfSpeech = getString(2),
        ipa = nullableString(3),
        matchedSurface = matchedSurface,
        matchKind = kind,
        favorite = false,
    )

    private fun putRanked(target: MutableMap<String, RankedSummary>, summary: EntrySummary, rank: Int) {
        val existing = target[summary.id]
        if (existing == null || rank < existing.rank) target[summary.id] = RankedSummary(summary, rank)
    }

    private fun parseExamples(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun externalId(stableId: String) = "pack/${descriptor.packId}/$stableId"

    private fun stableId(entryId: String): String? {
        val prefix = "pack/${descriptor.packId}/"
        return entryId.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.takeIf { it.isNotBlank() }
    }

    private fun Cursor.nullableString(index: Int): String? = if (isNull(index)) null else getString(index)

    private fun escapeLike(value: String): String = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    private data class RankedSummary(val summary: EntrySummary, val rank: Int)

    private data class EntryBase(
        val internalId: Long,
        val stableId: String,
        val lemma: String,
        val partOfSpeech: String,
        val ipa: String?,
        val etymology: String?,
        val inflectionKind: String?,
    )
}
