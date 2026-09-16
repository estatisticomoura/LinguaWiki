package org.linguawiki.offline.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

class DictionaryDatabase(private val appContext: Context) :
    SQLiteOpenHelper(appContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE entries (
                id TEXT PRIMARY KEY,
                edition TEXT NOT NULL,
                language TEXT NOT NULL,
                lemma TEXT NOT NULL,
                lemma_key TEXT NOT NULL,
                folded_key TEXT NOT NULL,
                part_of_speech TEXT NOT NULL,
                ipa TEXT,
                etymology TEXT,
                inflection_kind TEXT
            )
            """.trimIndent(),
        )
        createUserCollectionTables(db)
        db.execSQL(
            """
            CREATE TABLE senses (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entry_id TEXT NOT NULL REFERENCES entries(id) ON DELETE CASCADE,
                sense_order INTEGER NOT NULL,
                definition TEXT NOT NULL,
                example TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE translations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sense_id INTEGER NOT NULL REFERENCES senses(id) ON DELETE CASCADE,
                language TEXT NOT NULL,
                term TEXT NOT NULL,
                target_lemma TEXT,
                target_entry_id TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE forms (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entry_id TEXT NOT NULL REFERENCES entries(id) ON DELETE CASCADE,
                surface TEXT NOT NULL,
                surface_key TEXT NOT NULL,
                folded_key TEXT NOT NULL,
                label TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE favorites (
                entry_id TEXT PRIMARY KEY REFERENCES entries(id) ON DELETE CASCADE,
                added_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE history (
                entry_id TEXT PRIMARY KEY REFERENCES entries(id) ON DELETE CASCADE,
                viewed_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX entries_dictionary_lemma ON entries(edition, language, lemma_key)")
        db.execSQL("CREATE INDEX entries_dictionary_folded ON entries(edition, language, folded_key)")
        db.execSQL("CREATE INDEX forms_surface ON forms(surface_key)")
        db.execSQL("CREATE INDEX forms_folded ON forms(folded_key)")
        seed(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion in 3..4 && newVersion >= 5) {
            if (oldVersion == 3) {
                db.execSQL("ALTER TABLE entries ADD COLUMN etymology TEXT")
                populateSeedEtymologies(db)
            }
            createUserCollectionTables(db)
            db.execSQL(
                """
                INSERT OR REPLACE INTO user_favorites(
                    entry_id, edition, language, lemma, part_of_speech, ipa, added_at
                )
                SELECT e.id, e.edition, e.language, e.lemma, e.part_of_speech, e.ipa, f.added_at
                FROM favorites f JOIN entries e ON e.id = f.entry_id
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT OR REPLACE INTO user_history(
                    entry_id, edition, language, lemma, part_of_speech, ipa, viewed_at
                )
                SELECT e.id, e.edition, e.language, e.lemma, e.part_of_speech, e.ipa, h.viewed_at
                FROM history h JOIN entries e ON e.id = h.entry_id
                """.trimIndent(),
            )
            return
        }

        // Older prototype schemas did not distinguish every source field safely.
        db.execSQL("DROP TABLE IF EXISTS history")
        db.execSQL("DROP TABLE IF EXISTS favorites")
        db.execSQL("DROP TABLE IF EXISTS forms")
        db.execSQL("DROP TABLE IF EXISTS translations")
        db.execSQL("DROP TABLE IF EXISTS senses")
        db.execSQL("DROP TABLE IF EXISTS entries")
        db.execSQL("DROP TABLE IF EXISTS user_history")
        db.execSQL("DROP TABLE IF EXISTS user_favorites")
        onCreate(db)
    }

    private fun createUserCollectionTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS user_favorites (
                entry_id TEXT PRIMARY KEY,
                edition TEXT NOT NULL,
                language TEXT NOT NULL,
                lemma TEXT NOT NULL,
                part_of_speech TEXT NOT NULL,
                ipa TEXT,
                added_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS user_history (
                entry_id TEXT PRIMARY KEY,
                edition TEXT NOT NULL,
                language TEXT NOT NULL,
                lemma TEXT NOT NULL,
                part_of_speech TEXT NOT NULL,
                ipa TEXT,
                viewed_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun populateSeedEtymologies(db: SQLiteDatabase) {
        val root = JSONObject(appContext.assets.open("seed_entries.json").bufferedReader().use { it.readText() })
        val entries = root.getJSONArray("entries")
        for (entryIndex in 0 until entries.length()) {
            val entry = entries.getJSONObject(entryIndex)
            val etymology = entry.optString("etymology").takeIf { it.isNotBlank() } ?: continue
            db.update(
                "entries",
                ContentValues().apply { put("etymology", etymology) },
                "id = ?",
                arrayOf(entry.getString("id")),
            )
        }
    }

    fun search(language: String, rawQuery: String, limit: Int = 30): List<EntrySummary> {
        val query = SearchNormalizer.canonical(rawQuery)
        if (query.isBlank()) return emptyList()
        val folded = SearchNormalizer.fold(query)
        val matches = linkedMapOf<String, RankedSummary>()
        val db = readableDatabase

        addEntryMatches(db, language, "e.lemma_key = ?", arrayOf(query), MatchKind.EXACT_LEMMA, 0, matches)
        addFormMatches(db, language, "f.surface_key = ?", arrayOf(query), MatchKind.EXACT_FORM, 1, matches)

        addEntryMatches(db, language, "e.folded_key = ?", arrayOf(folded), MatchKind.DIACRITIC, 2, matches)
        addFormMatches(db, language, "f.folded_key = ?", arrayOf(folded), MatchKind.DIACRITIC, 3, matches)

        val prefix = escapeLike(folded) + "%"
        addEntryMatches(db, language, "e.folded_key LIKE ? ESCAPE '\\'", arrayOf(prefix), MatchKind.PREFIX, 4, matches)
        addFormMatches(db, language, "f.folded_key LIKE ? ESCAPE '\\'", arrayOf(prefix), MatchKind.PREFIX, 5, matches)

        if (matches.size < 8 && folded.length >= 3) {
            val threshold = if (folded.length >= 8) 3 else 2
            db.rawQuery(
                """
                SELECT e.id, e.edition, e.language, e.lemma, e.part_of_speech, e.ipa,
                       EXISTS(SELECT 1 FROM user_favorites fav WHERE fav.entry_id = e.id) AS favorite,
                       e.folded_key
                FROM entries e
                WHERE e.edition = ? AND e.language = ?
                  AND ABS(LENGTH(e.folded_key) - ?) <= ?
                ORDER BY e.lemma
                LIMIT 800
                """.trimIndent(),
                arrayOf(language, language, folded.length.toString(), threshold.toString()),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val candidate = cursor.getString(7)
                    val distance = SearchNormalizer.levenshtein(folded, candidate)
                    if (distance <= threshold) {
                        val summary = cursor.toEntrySummary(MatchKind.APPROXIMATE)
                        putRanked(matches, summary, 10 + distance)
                    }
                }
            }
        }

        return matches.values
            .sortedWith(compareBy<RankedSummary> { it.rank }.thenBy { it.summary.lemma })
            .take(limit)
            .map { it.summary }
    }

    fun findExact(language: String, lemma: String): String? {
        readableDatabase.rawQuery(
            """
            SELECT id FROM entries
            WHERE edition = ? AND language = ? AND lemma_key = ?
            ORDER BY id LIMIT 1
            """.trimIndent(),
            arrayOf(language, language, SearchNormalizer.canonical(lemma)),
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    fun hasEntry(entryId: String): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM entries WHERE id = ?",
        arrayOf(entryId),
    ).use { it.moveToFirst() }

    fun getEntry(entryId: String): DictionaryEntry? {
        val db = readableDatabase
        val base = db.rawQuery(
            """
            SELECT e.id, e.edition, e.language, e.lemma, e.part_of_speech, e.ipa, e.etymology,
                   e.inflection_kind,
                   EXISTS(SELECT 1 FROM user_favorites fav WHERE fav.entry_id = e.id) AS favorite
            FROM entries e WHERE e.id = ?
            """.trimIndent(),
            arrayOf(entryId),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            EntryBase(
                id = cursor.getString(0),
                edition = cursor.getString(1),
                language = cursor.getString(2),
                lemma = cursor.getString(3),
                partOfSpeech = cursor.getString(4),
                ipa = cursor.nullableString(5),
                etymology = cursor.nullableString(6),
                inflectionKind = cursor.nullableString(7),
                favorite = cursor.getInt(8) == 1,
            )
        }

        val senses = mutableListOf<Sense>()
        db.rawQuery(
            "SELECT id, sense_order, definition, example FROM senses WHERE entry_id = ? ORDER BY sense_order",
            arrayOf(entryId),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val senseId = cursor.getLong(0)
                val translations = mutableListOf<Translation>()
                db.rawQuery(
                    "SELECT language, term, target_lemma, target_entry_id FROM translations WHERE sense_id = ? ORDER BY language, term",
                    arrayOf(senseId.toString()),
                ).use { translationCursor ->
                    while (translationCursor.moveToNext()) {
                        translations += Translation(
                            language = translationCursor.getString(0),
                            term = translationCursor.getString(1),
                            targetLemma = translationCursor.nullableString(2),
                            targetEntryId = translationCursor.nullableString(3),
                        )
                    }
                }
                senses += Sense(
                    order = cursor.getInt(1),
                    definition = cursor.getString(2),
                    examples = listOfNotNull(cursor.nullableString(3)),
                    translations = translations,
                )
            }
        }

        val forms = mutableListOf<WordForm>()
        db.rawQuery(
            "SELECT surface, label FROM forms WHERE entry_id = ? ORDER BY id",
            arrayOf(entryId),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                forms += WordForm(cursor.getString(0), cursor.getString(1))
            }
        }

        return DictionaryEntry(
            id = base.id,
            edition = base.edition,
            language = base.language,
            lemma = base.lemma,
            partOfSpeech = base.partOfSpeech,
            ipa = base.ipa,
            etymology = base.etymology,
            inflectionKind = base.inflectionKind,
            senses = senses,
            forms = forms,
            favorite = base.favorite,
        )
    }

    fun getEnglishReferences(entryId: String): List<DictionaryEntry> {
        val ids = mutableListOf<String>()
        readableDatabase.rawQuery(
            """
            SELECT reference.id
            FROM entries source
            JOIN entries reference
              ON reference.edition = 'en'
             AND reference.language = source.language
             AND reference.lemma_key = source.lemma_key
            WHERE source.id = ?
              AND source.edition != 'en'
              AND reference.id != source.id
            ORDER BY CASE WHEN reference.part_of_speech = source.part_of_speech THEN 0 ELSE 1 END,
                     reference.id
            LIMIT 4
            """.trimIndent(),
            arrayOf(entryId),
        ).use { cursor ->
            while (cursor.moveToNext()) ids += cursor.getString(0)
        }
        return ids.mapNotNull(::getEntry)
    }

    fun isFavorite(entryId: String): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM user_favorites WHERE entry_id = ?",
        arrayOf(entryId),
    ).use { it.moveToFirst() }

    fun toggleFavorite(entry: DictionaryEntry): Boolean {
        val db = writableDatabase
        val exists = db.rawQuery(
            "SELECT 1 FROM user_favorites WHERE entry_id = ?",
            arrayOf(entry.id),
        ).use { it.moveToFirst() }
        if (exists) {
            db.delete("user_favorites", "entry_id = ?", arrayOf(entry.id))
            return false
        }
        db.insertOrThrow(
            "user_favorites",
            null,
            collectionValues(entry).apply { put("added_at", System.currentTimeMillis()) },
        )
        return true
    }

    fun toggleFavorite(entryId: String): Boolean = getEntry(entryId)?.let(::toggleFavorite) ?: false

    fun favorites(): List<EntrySummary> = listFromCollection("user_favorites", "added_at")

    fun recordHistory(entry: DictionaryEntry) {
        writableDatabase.insertWithOnConflict(
            "user_history",
            null,
            collectionValues(entry).apply { put("viewed_at", System.currentTimeMillis()) },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun recordHistory(entryId: String) {
        getEntry(entryId)?.let(::recordHistory)
    }

    fun history(): List<EntrySummary> = listFromCollection("user_history", "viewed_at")

    fun clearHistory() {
        writableDatabase.delete("user_history", null, null)
    }

    private fun listFromCollection(table: String, timestampColumn: String): List<EntrySummary> {
        val results = mutableListOf<EntrySummary>()
        readableDatabase.rawQuery(
            """
            SELECT c.entry_id, c.edition, c.language, c.lemma, c.part_of_speech, c.ipa,
                   EXISTS(SELECT 1 FROM user_favorites fav WHERE fav.entry_id = c.entry_id) AS favorite
            FROM $table c
            ORDER BY c.$timestampColumn DESC
            """.trimIndent(),
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) results += cursor.toEntrySummary(MatchKind.EXACT_LEMMA)
        }
        return results
    }

    private fun collectionValues(entry: DictionaryEntry) = ContentValues().apply {
        put("entry_id", entry.id)
        put("edition", entry.edition)
        put("language", entry.language)
        put("lemma", entry.lemma)
        put("part_of_speech", entry.partOfSpeech)
        put("ipa", entry.ipa)
    }

    private fun addEntryMatches(
        db: SQLiteDatabase,
        language: String,
        condition: String,
        conditionArgs: Array<String>,
        kind: MatchKind,
        rank: Int,
        target: MutableMap<String, RankedSummary>,
    ) {
        db.rawQuery(
            """
            SELECT e.id, e.edition, e.language, e.lemma, e.part_of_speech, e.ipa,
                   EXISTS(SELECT 1 FROM user_favorites fav WHERE fav.entry_id = e.id) AS favorite
            FROM entries e
            WHERE e.edition = ? AND e.language = ? AND $condition
            ORDER BY e.lemma LIMIT 50
            """.trimIndent(),
            arrayOf(language, language, *conditionArgs),
        ).use { cursor ->
            while (cursor.moveToNext()) putRanked(target, cursor.toEntrySummary(kind), rank)
        }
    }

    private fun addFormMatches(
        db: SQLiteDatabase,
        language: String,
        condition: String,
        conditionArgs: Array<String>,
        kind: MatchKind,
        rank: Int,
        target: MutableMap<String, RankedSummary>,
    ) {
        db.rawQuery(
            """
            SELECT e.id, e.edition, e.language, e.lemma, e.part_of_speech, e.ipa,
                   EXISTS(SELECT 1 FROM user_favorites fav WHERE fav.entry_id = e.id) AS favorite,
                   f.surface
            FROM forms f JOIN entries e ON e.id = f.entry_id
            WHERE e.edition = ? AND e.language = ? AND $condition
            ORDER BY e.lemma LIMIT 50
            """.trimIndent(),
            arrayOf(language, language, *conditionArgs),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val summary = cursor.toEntrySummary(kind).copy(matchedSurface = cursor.getString(7))
                putRanked(target, summary, rank)
            }
        }
    }

    private fun putRanked(target: MutableMap<String, RankedSummary>, summary: EntrySummary, rank: Int) {
        val existing = target[summary.id]
        if (existing == null || rank < existing.rank) target[summary.id] = RankedSummary(summary, rank)
    }

    private fun Cursor.toEntrySummary(kind: MatchKind): EntrySummary = EntrySummary(
        id = getString(0),
        edition = getString(1),
        language = getString(2),
        lemma = getString(3),
        partOfSpeech = getString(4),
        ipa = nullableString(5),
        matchKind = kind,
        favorite = getInt(6) == 1,
    )

    private fun Cursor.nullableString(index: Int): String? = if (isNull(index)) null else getString(index)

    private fun escapeLike(value: String): String = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    private fun seed(db: SQLiteDatabase) {
        val root = JSONObject(appContext.assets.open("seed_entries.json").bufferedReader().use { it.readText() })
        val entries = root.getJSONArray("entries")
        db.beginTransaction()
        try {
            for (entryIndex in 0 until entries.length()) {
                val entry = entries.getJSONObject(entryIndex)
                val entryId = entry.getString("id")
                val lemma = entry.getString("lemma")
                val language = entry.getString("language")
                db.insertOrThrow(
                    "entries",
                    null,
                    ContentValues().apply {
                        put("id", entryId)
                        put("edition", entry.optString("edition").takeIf { it.isNotBlank() } ?: language)
                        put("language", language)
                        put("lemma", lemma)
                        put("lemma_key", SearchNormalizer.canonical(lemma))
                        put("folded_key", SearchNormalizer.fold(lemma))
                        put("part_of_speech", entry.getString("partOfSpeech"))
                        put("ipa", entry.optString("ipa").takeIf { it.isNotBlank() })
                        put("etymology", entry.optString("etymology").takeIf { it.isNotBlank() })
                        put("inflection_kind", entry.optString("inflectionKind").takeIf { it.isNotBlank() })
                    },
                )

                val senses = entry.getJSONArray("senses")
                for (senseIndex in 0 until senses.length()) {
                    val sense = senses.getJSONObject(senseIndex)
                    val senseId = db.insertOrThrow(
                        "senses",
                        null,
                        ContentValues().apply {
                            put("entry_id", entryId)
                            put("sense_order", senseIndex + 1)
                            put("definition", sense.getString("definition"))
                            put("example", sense.optString("example").takeIf { it.isNotBlank() })
                        },
                    )
                    val translations = sense.optJSONArray("translations") ?: continue
                    for (translationIndex in 0 until translations.length()) {
                        val translation = translations.getJSONObject(translationIndex)
                        db.insertOrThrow(
                            "translations",
                            null,
                            ContentValues().apply {
                                put("sense_id", senseId)
                                put("language", translation.getString("language"))
                                put("term", translation.getString("term"))
                                put("target_lemma", translation.optString("targetLemma").takeIf { it.isNotBlank() })
                                put("target_entry_id", translation.optString("targetEntryId").takeIf { it.isNotBlank() })
                            },
                        )
                    }
                }

                val forms = entry.optJSONArray("forms") ?: continue
                for (formIndex in 0 until forms.length()) {
                    val form = forms.getJSONObject(formIndex)
                    val surface = form.getString("surface")
                    db.insertOrThrow(
                        "forms",
                        null,
                        ContentValues().apply {
                            put("entry_id", entryId)
                            put("surface", surface)
                            put("surface_key", SearchNormalizer.canonical(surface))
                            put("folded_key", SearchNormalizer.fold(surface))
                            put("label", form.getString("label"))
                        },
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private data class RankedSummary(val summary: EntrySummary, val rank: Int)

    private data class EntryBase(
        val id: String,
        val edition: String,
        val language: String,
        val lemma: String,
        val partOfSpeech: String,
        val ipa: String?,
        val etymology: String?,
        val inflectionKind: String?,
        val favorite: Boolean,
    )

    companion object {
        private const val DATABASE_NAME = "linguawiki.db"
        private const val DATABASE_VERSION = 5
    }
}
