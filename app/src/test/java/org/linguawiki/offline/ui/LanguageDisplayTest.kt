package org.linguawiki.offline.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.linguawiki.offline.data.OnlineEdition
import java.util.Locale

class LanguageDisplayTest {
    @Test
    fun `offline dictionary labels use representative flags and endonyms`() {
        assertEquals("🇬🇧 English", dictionaryDisplayLabel("en"))
        assertEquals("🇵🇹 Português", dictionaryDisplayLabel("pt"))
        assertEquals("🇵🇱 Polski", dictionaryDisplayLabel("pl"))
        assertEquals("🇻🇦 Latina", dictionaryDisplayLabel("la"))
        assertEquals("🇪🇸 Español", dictionaryDisplayLabel("es"))
    }

    @Test
    fun `online edition keeps its existing label and adds a sensible flag`() {
        val edition = OnlineEdition("pl", "Polski", "https://pl.wiktionary.org")
        assertEquals("🇵🇱 Polish · Polski", onlineEditionDisplayLabel(edition, Locale.ENGLISH))
    }

    @Test
    fun `online edition omits a disputed representative flag`() {
        assertNull(onlineFlagForLanguage("ku"))
    }
}
