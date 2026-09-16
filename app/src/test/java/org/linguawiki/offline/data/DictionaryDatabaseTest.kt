package org.linguawiki.offline.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DictionaryDatabaseTest {
    private lateinit var context: Context
    private lateinit var database: DictionaryDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("linguawiki.db")
        database = DictionaryDatabase(context)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase("linguawiki.db")
    }

    @Test
    fun `search resolves an English conjugated form`() {
        val results = database.search("en", "running")
        assertEquals("run", results.first().lemma)
        assertEquals(MatchKind.EXACT_FORM, results.first().matchKind)
    }

    @Test
    fun `search restores Portuguese diacritics`() {
        val results = database.search("pt", "coracao")
        assertEquals("coração", results.first().lemma)
        assertEquals(MatchKind.DIACRITIC, results.first().matchKind)
    }

    @Test
    fun `ambiguous Portuguese form returns both valid lemmas`() {
        val lemmas = database.search("pt", "fui").take(2).map { it.lemma }.toSet()
        assertEquals(setOf("ir", "ser"), lemmas)
    }

    @Test
    fun `search resolves a Polish form even when its diacritic is omitted`() {
        val result = database.search("pl", "prosze").first()
        assertEquals("prosić", result.lemma)
        assertEquals(MatchKind.DIACRITIC, result.matchKind)
    }

    @Test
    fun `non English entries can include an English Wiktionary reference`() {
        val references = database.getEnglishReferences("pl-milosc-n")
        assertEquals(1, references.size)
        assertEquals("en", references.first().edition)
        assertEquals("pl", references.first().language)
        assertTrue(references.first().senses.first().definition.startsWith("Love"))
    }

    @Test
    fun `entry preserves etymology when the source supplies it`() {
        assertTrue(database.getEntry("pl-milosc-n")?.etymology?.contains("prasłowiańskiego") == true)
    }

    @Test
    fun `English reference entries do not leak into the English dictionary search`() {
        assertTrue(database.search("en", "coração").isEmpty())
    }

    @Test
    fun `translations preserve the intended part of speech`() {
        val nounTarget = database.getEntry("en-love-n")
            ?.senses?.firstOrNull()?.translations?.firstOrNull()?.targetEntryId
        val verbTarget = database.getEntry("en-love-v")
            ?.senses?.firstOrNull()?.translations?.firstOrNull()?.targetEntryId

        assertEquals("pt-amor-n", nounTarget)
        assertEquals("pt-amar-v", verbTarget)
    }

    @Test
    fun `favorites and history persist locally`() {
        assertTrue(database.toggleFavorite("pt-coracao-n"))
        assertTrue(database.getEntry("pt-coracao-n")?.favorite == true)
        assertEquals("coração", database.favorites().first().lemma)

        database.recordHistory("pt-coracao-n")
        assertEquals("coração", database.history().first().lemma)
        database.clearHistory()
        assertFalse(database.history().isNotEmpty())
    }

    @Test
    fun `version 3 migration preserves favorites and adds etymology`() {
        database.close()
        context.deleteDatabase("linguawiki.db")
        context.openOrCreateDatabase("linguawiki.db", Context.MODE_PRIVATE, null).use { legacy ->
            legacy.execSQL(
                """
                CREATE TABLE entries (
                    id TEXT PRIMARY KEY, edition TEXT NOT NULL, language TEXT NOT NULL,
                    lemma TEXT NOT NULL, lemma_key TEXT NOT NULL, folded_key TEXT NOT NULL,
                    part_of_speech TEXT NOT NULL, ipa TEXT, inflection_kind TEXT
                )
                """.trimIndent(),
            )
            legacy.execSQL(
                "CREATE TABLE senses (id INTEGER PRIMARY KEY AUTOINCREMENT, entry_id TEXT NOT NULL, sense_order INTEGER NOT NULL, definition TEXT NOT NULL, example TEXT)",
            )
            legacy.execSQL(
                "CREATE TABLE translations (id INTEGER PRIMARY KEY AUTOINCREMENT, sense_id INTEGER NOT NULL, language TEXT NOT NULL, term TEXT NOT NULL, target_lemma TEXT, target_entry_id TEXT)",
            )
            legacy.execSQL(
                "CREATE TABLE forms (id INTEGER PRIMARY KEY AUTOINCREMENT, entry_id TEXT NOT NULL, surface TEXT NOT NULL, surface_key TEXT NOT NULL, folded_key TEXT NOT NULL, label TEXT NOT NULL)",
            )
            legacy.execSQL("CREATE TABLE favorites (entry_id TEXT PRIMARY KEY, added_at INTEGER NOT NULL)")
            legacy.execSQL("CREATE TABLE history (entry_id TEXT PRIMARY KEY, viewed_at INTEGER NOT NULL)")
            legacy.execSQL(
                "INSERT INTO entries VALUES ('pl-milosc-n', 'pl', 'pl', 'miłość', 'miłość', 'milosc', 'noun', '/ˈmi.wɔɕt͡ɕ/', 'declension')",
            )
            legacy.execSQL(
                "INSERT INTO senses(entry_id, sense_order, definition) VALUES ('pl-milosc-n', 1, 'test')",
            )
            legacy.execSQL("INSERT INTO favorites VALUES ('pl-milosc-n', 1)")
            legacy.version = 3
        }

        database = DictionaryDatabase(context)
        val migrated = database.getEntry("pl-milosc-n")

        assertTrue(migrated?.favorite == true)
        assertTrue(migrated?.etymology?.contains("prasłowiańskiego") == true)
    }
}
