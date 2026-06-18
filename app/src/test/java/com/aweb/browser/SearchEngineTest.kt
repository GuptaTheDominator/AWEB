package com.aweb.browser

import com.aweb.browser.data.repository.SearchEngine
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [SearchEngine] URL building.
 */
class SearchEngineTest {

    @Test
    fun `DuckDuckGo builds correct search URL`() {
        val url = SearchEngine.DUCKDUCKGO.buildSearchUrl("hello world")
        assertEquals("https://duckduckgo.com/?q=hello+world", url)
    }

    @Test
    fun `Google builds correct search URL`() {
        val url = SearchEngine.GOOGLE.buildSearchUrl("kotlin coroutines")
        assertEquals("https://www.google.com/search?q=kotlin+coroutines", url)
    }

    @Test
    fun `Bing builds correct search URL`() {
        val url = SearchEngine.BING.buildSearchUrl("android jetpack")
        assertEquals("https://www.bing.com/search?q=android+jetpack", url)
    }

    @Test
    fun `fromString returns correct engine`() {
        assertEquals(SearchEngine.DUCKDUCKGO, SearchEngine.fromString("ddg"))
        assertEquals(SearchEngine.GOOGLE,     SearchEngine.fromString("google"))
        assertEquals(SearchEngine.BING,       SearchEngine.fromString("bing"))
    }

    @Test
    fun `fromString returns null for unknown key`() {
        assertNull(SearchEngine.fromString("yahoo"))
    }

    @Test
    fun `spaces in query are replaced with plus signs`() {
        val url = SearchEngine.DUCKDUCKGO.buildSearchUrl("a b c")
        assertTrue(url.contains("a+b+c"))
        assertFalse(url.contains(" "))
    }

    @Test
    fun `query is trimmed before building URL`() {
        val url = SearchEngine.GOOGLE.buildSearchUrl("  kotlin  ")
        assertTrue(url.contains("kotlin"))
    }
    @Test
    fun `special characters in query are URL encoded`() {
        val url = SearchEngine.GOOGLE.buildSearchUrl("a&b #tag")
        assertEquals("https://www.google.com/search?q=a%26b+%23tag", url)
    }

}
