package com.aweb.browser.browser

import com.aweb.browser.data.repository.SearchEngine
import java.net.IDN

/**
 * Pure JVM omnibox parser used by the browser toolbar.
 *
 * It intentionally avoids Android APIs so the URL/search decision tree can be
 * unit-tested without instrumentation.
 */
object UrlInputParser {

    private val commonTlds = setOf(
        "com", "org", "net", "in", "co", "io", "dev", "app", "ai", "gov", "edu", "mil",
        "info", "biz", "me", "xyz", "site", "online", "store", "tech", "blog", "news", "tv",
        "us", "uk", "ca", "au", "de", "fr", "jp", "cn", "br", "ru", "za", "eu", "id", "sg",
        "cloud", "page", "shop", "pro", "live", "world", "today", "company", "email", "media",
    )

    fun normalize(input: String, searchEngine: SearchEngine): String {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return searchEngine.buildSearchUrl("")

        return when {
            hasSupportedScheme(trimmed) -> trimmed
            looksLikeDomain(trimmed) -> "https://$trimmed"
            else -> searchEngine.buildSearchUrl(trimmed)
        }
    }

    fun looksLikeDomain(input: String): Boolean {
        val value = input.trim()
        if (value.isBlank() || value.any { it.isWhitespace() } || value.contains("://")) return false

        val host = value
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore('@')
            .substringBefore(':')
            .lowercase()

        if (host == "localhost") return true
        if (isValidIpv4(host)) return true

        val asciiHost = runCatching { IDN.toASCII(host) }.getOrNull() ?: return false
        val labels = asciiHost.split('.')
        if (labels.size < 2 || labels.any { it.isBlank() }) return false
        if (labels.any { label ->
                label.length > 63 || label.startsWith('-') || label.endsWith('-') ||
                    !label.all { it.isLetterOrDigit() || it == '-' }
            }
        ) return false

        val tld = labels.last()
        return tld in commonTlds || (tld.length == 2 && tld.all { it.isLetter() })
    }

    private fun hasSupportedScheme(value: String): Boolean =
        value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("about:", ignoreCase = true) ||
            value.startsWith("file://", ignoreCase = true)

    private fun isValidIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            part.isNotBlank() && part.length <= 3 && part.all { it.isDigit() } &&
                part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }
}
