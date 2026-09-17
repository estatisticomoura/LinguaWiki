package org.linguawiki.offline.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WiktionaryNavigationTest {
    @Test
    fun `English Wiktionary opens modern Polish section`() {
        assertEquals("#Polish", wiktionaryLanguageSection("en", "pl"))
    }

    @Test
    fun `other language and edition combinations do not force a section`() {
        assertEquals("", wiktionaryLanguageSection("pl", "pl"))
        assertEquals("", wiktionaryLanguageSection("en", "pt"))
    }
}
