package com.aweb.browser

import com.aweb.browser.security.PrivacySanitizer
import org.junit.Assert.*
import org.junit.Test

class PrivacySanitizerTest {

    @Test fun `redacts full URL path and query from messages`() {
        val input = "Failed loading https://example.com/private/path?token=abc&user=me"
        val redacted = PrivacySanitizer.redact(input)
        assertEquals("Failed loading https://example.com/…", redacted)
        assertFalse(redacted.contains("token"))
        assertFalse(redacted.contains("private/path"))
    }

    @Test fun `redactUrl keeps only scheme host and port`() {
        assertEquals(
            "https://example.com:8443/…",
            PrivacySanitizer.redactUrl("https://example.com:8443/a/b?secret=1"),
        )
    }

    @Test fun `redacts standalone query values`() {
        val redacted = PrivacySanitizer.redact("Request failed ?token=abc&account=123")
        assertEquals("Request failed ?token=<redacted>&account=<redacted>", redacted)
    }

    @Test fun `invalid URL becomes generic redaction`() {
        assertEquals("<redacted-url>", PrivacySanitizer.redactUrl("https://"))
    }
}
