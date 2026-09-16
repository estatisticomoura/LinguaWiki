package org.linguawiki.offline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WiktionarySuggestionClientTest {
    private val client = WiktionarySuggestionClient()

    @Test
    fun `parser preserves API order and identifies an approximate match`() {
        val response = """
            {
              "query": {
                "prefixsearch": [
                  {"title": "power"},
                  {"title": "powered"},
                  {"title": "power"}
                ]
              }
            }
        """.trimIndent()

        val suggestions = client.parsePrefixResponse(response, "pwoer")

        assertEquals(listOf("power", "powered"), suggestions.map { it.title })
        assertTrue(suggestions.all { it.approximate })
    }

    @Test
    fun `missing diacritics still count as a prefix match`() {
        val response = """
            {"query":{"prefixsearch":[{"title":"miłość"},{"title":"miłościwy"}]}}
        """.trimIndent()

        val suggestions = client.parsePrefixResponse(response, "milosc")

        assertFalse(suggestions.first().approximate)
    }

    @Test
    fun `endpoint encodes Unicode and stays on official HTTPS Wiktionary`() {
        val edition = OnlineEdition("pl", "polski", "https://pl.wiktionary.org")
        val endpoint = client.buildEndpoint(edition, "miłość", 12)

        assertEquals("https", endpoint.protocol)
        assertEquals("pl.wiktionary.org", endpoint.host)
        assertTrue(endpoint.query.contains("pssearch=mi%C5%82o%C5%9B%C4%87"))
        assertTrue(endpoint.query.contains("pslimit=12"))
    }

    @Test
    fun `endpoint rejects a host outside Wiktionary`() {
        val edition = OnlineEdition("bad", "bad", "https://example.org")
        assertThrows(IllegalArgumentException::class.java) {
            client.buildEndpoint(edition, "word", 12)
        }
    }

    @Test
    fun `fallback parser combines valid title variants spelling suggestion and search results`() {
        val response = """
            {
              "query": {
                "pages": [
                  {"title":"miłość"},
                  {"title":"milosc", "missing":true}
                ],
                "searchinfo": {"suggestion":"miłości"},
                "search": [
                  {"title":"miłosny"},
                  {"title":"miłość"}
                ]
              }
            }
        """.trimIndent()

        val suggestions = client.parseFallbackResponse(response, "milosc")

        assertEquals(listOf("miłość", "miłości", "miłosny"), suggestions.map { it.title })
        assertFalse(suggestions.first().approximate)
    }

    @Test
    fun `diacritic variants include Polish and Portuguese candidates`() {
        assertTrue("miłość" in client.diacriticVariants("milosc"))
        assertTrue("coração" in client.diacriticVariants("coracao"))
    }

    @Test
    fun `fallback endpoint requests search suggestions and explicit variants together`() {
        val edition = OnlineEdition("pt", "português", "https://pt.wiktionary.org")
        val endpoint = client.buildFallbackEndpoint(
            edition = edition,
            query = "coracao",
            limit = 12,
            variants = listOf("coração"),
        )

        assertTrue(endpoint.query.contains("list=search"))
        assertTrue(endpoint.query.contains("srinfo=suggestion"))
        assertTrue(endpoint.query.contains("titles=cora%C3%A7%C3%A3o"))
    }
}
