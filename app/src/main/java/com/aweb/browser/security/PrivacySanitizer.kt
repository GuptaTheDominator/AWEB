package com.aweb.browser.security

import java.net.URI

/** Privacy helpers for logs and crash persistence. */
object PrivacySanitizer {
    private val urlRegex = Regex("""(?i)\bhttps?://[^\s)\]}'\"<>]+""")
    private val queryValueRegex = Regex("""([?&][^=&#\s]+)=([^&#\s]+)""")

    fun redact(input: String?): String = input
        ?.replace(urlRegex) { redactUrl(it.value) }
        ?.let { queryValueRegex.replace(it) { m -> "${m.groupValues[1]}=<redacted>" } }
        ?: ""

    fun redactUrl(input: String?): String {
        val raw = input?.trim().orEmpty()
        if (raw.isBlank()) return "<redacted-url>"
        return runCatching {
            val uri = URI(raw)
            val scheme = uri.scheme?.lowercase() ?: "https"
            val host = uri.host ?: return@runCatching "<redacted-url>"
            val port = if (uri.port >= 0) ":${uri.port}" else ""
            "$scheme://$host$port/…"
        }.getOrDefault("<redacted-url>")
    }
}
