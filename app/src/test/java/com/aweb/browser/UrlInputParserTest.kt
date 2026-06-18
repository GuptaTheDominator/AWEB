package com.aweb.browser

import com.aweb.browser.browser.UrlInputParser
import com.aweb.browser.data.repository.SearchEngine
import org.junit.Assert.*
import org.junit.Test

class UrlInputParserTest {

    @Test fun `preserves explicit https URL`() {
        assertEquals(
            "https://example.com/path?q=1",
            UrlInputParser.normalize(" https://example.com/path?q=1 ", SearchEngine.DUCKDUCKGO),
        )
    }

    @Test fun `adds https to known bare domain`() {
        assertEquals(
            "https://example.com",
            UrlInputParser.normalize("example.com", SearchEngine.DUCKDUCKGO),
        )
    }

    @Test fun `adds https to localhost`() {
        assertEquals(
            "https://localhost:8080",
            UrlInputParser.normalize("localhost:8080", SearchEngine.DUCKDUCKGO),
        )
    }

    @Test fun `adds https to valid IPv4 address`() {
        assertEquals(
            "https://192.168.1.10:8080",
            UrlInputParser.normalize("192.168.1.10:8080", SearchEngine.DUCKDUCKGO),
        )
    }

    @Test fun `invalid IPv4 becomes search query`() {
        val url = UrlInputParser.normalize("999.999.999.999", SearchEngine.GOOGLE)
        assertTrue(url.startsWith("https://www.google.com/search?q="))
    }

    @Test fun `input containing spaces becomes search query`() {
        assertEquals(
            "https://duckduckgo.com/?q=example.com+login",
            UrlInputParser.normalize("example.com login", SearchEngine.DUCKDUCKGO),
        )
    }

    @Test fun `unknown dotted text becomes search query`() {
        val url = UrlInputParser.normalize("file.txt", SearchEngine.BING)
        assertEquals("https://www.bing.com/search?q=file.txt", url)
    }

    @Test fun `preserves about pages`() {
        assertEquals(
            "about:blank",
            UrlInputParser.normalize("about:blank", SearchEngine.DUCKDUCKGO),
        )
    }
}
