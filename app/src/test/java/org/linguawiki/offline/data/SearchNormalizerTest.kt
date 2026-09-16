package org.linguawiki.offline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchNormalizerTest {
    @Test
    fun `fold removes Portuguese diacritics`() {
        assertEquals("coracao", SearchNormalizer.fold("Coração"))
        assertEquals("acoes", SearchNormalizer.fold("ações"))
    }

    @Test
    fun `fold handles Polish letters including stroked l`() {
        assertEquals("zolw", SearchNormalizer.fold("Żółw"))
        assertEquals("szczescie", SearchNormalizer.fold("szczęście"))
    }

    @Test
    fun `canonical preserves meaningful diacritics`() {
        assertEquals("łaska", SearchNormalizer.canonical("  ŁASKA "))
        assertTrue(SearchNormalizer.canonical("laska") != SearchNormalizer.canonical("łaska"))
    }

    @Test
    fun `edit distance detects ordinary misspelling`() {
        assertEquals(1, SearchNormalizer.levenshtein("przeprasam", "przepraszam"))
        assertEquals(1, SearchNormalizer.levenshtein("dicionaro", "dicionario"))
    }
}
