package org.linguawiki.offline.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflinePackDatabaseTest {
    private lateinit var file: File
    private lateinit var pack: OfflinePackDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        file = File(context.cacheDir, "offline-pack-test.sqlite").also { it.delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE entries(id INTEGER PRIMARY KEY, stable_id TEXT, lemma TEXT, lemma_key TEXT, folded_key TEXT, part_of_speech TEXT, ipa TEXT, etymology TEXT, inflection_kind TEXT)",
            )
            db.execSQL(
                "CREATE TABLE senses(id INTEGER PRIMARY KEY, entry_id INTEGER, sense_order INTEGER, definition TEXT, examples_json TEXT)",
            )
            db.execSQL(
                "CREATE TABLE translations(id INTEGER PRIMARY KEY, sense_id INTEGER, language TEXT, term TEXT)",
            )
            db.execSQL(
                "CREATE TABLE forms(id INTEGER PRIMARY KEY, entry_id INTEGER, surface TEXT, surface_key TEXT, folded_key TEXT, label TEXT)",
            )
            db.execSQL(
                "INSERT INTO entries VALUES(1, 'poder-verb-1', 'poder', 'poder', 'poder', 'verb', '/pu.ˈdeɾ/', 'Do latim posse.', 'conjugation')",
            )
            db.execSQL(
                "INSERT INTO senses VALUES(10, 1, 1, 'ter capacidade', '[\"Eu posso ajudar.\",\"Pudesse eu voltar.\"]')",
            )
            db.execSQL("INSERT INTO translations VALUES(20, 10, 'en', 'can')")
            db.execSQL(
                "INSERT INTO forms VALUES(30, 1, 'pudesse', 'pudesse', 'pudesse', 'singular, 1.ª pessoa, subjuntivo')",
            )
            db.execSQL(
                "INSERT INTO entries VALUES(2, 'coracao-noun-1', 'coração', 'coração', 'coracao', 'noun', NULL, NULL, 'declension')",
            )
        }
        pack = OfflinePackDatabase(file, descriptor)
    }

    @After
    fun tearDown() {
        pack.close()
        file.delete()
    }

    @Test
    fun `conjugated form resolves to its lemma`() {
        val result = pack.search("pudesse").first()
        assertEquals("poder", result.lemma)
        assertEquals("pudesse", result.matchedSurface)
        assertEquals(MatchKind.EXACT_FORM, result.matchKind)
    }

    @Test
    fun `missing diacritic finds the accented lemma`() {
        val result = pack.search("coracao").first()
        assertEquals("coração", result.lemma)
        assertEquals(MatchKind.DIACRITIC, result.matchKind)
    }

    @Test
    fun `entry exposes examples translations forms and attribution identity`() {
        val id = pack.findExact("poder")
        assertEquals("pack/pt-pt/poder-verb-1", id)
        val entry = pack.getEntry(id!!)
        assertEquals(2, entry?.senses?.first()?.examples?.size)
        assertEquals("can", entry?.senses?.first()?.translations?.first()?.term)
        assertEquals("pudesse", entry?.forms?.first()?.surface)
        assertTrue(entry?.etymology?.contains("latim") == true)
    }

    private companion object {
        val descriptor = OfflinePackDescriptor(
            packId = "pt-pt",
            version = "test",
            headwordLanguage = "pt",
            primaryEdition = "pt",
            displayName = "Português",
            downloadBytes = 1,
            installedBytes = 1,
            entryCount = 2,
            senseCount = 1,
            translationCount = 1,
            formCount = 1,
            sha256 = "test",
            url = "https://example.invalid/test.gz",
            licenses = listOf("CC BY-SA 4.0"),
        )
    }
}
