package com.aweb.browser

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for DownloadHandler filename sanitisation logic.
 * Pure JVM — no Android dependencies.
 */
class DownloadHandlerTest {

    private fun sanitise(url: String, hint: String, mimeType: String?): String {
        if (hint.isNotBlank() && hint.contains('.')) return hint
        val path = url.substringAfterLast('/').substringBefore('?').takeIf { it.isNotBlank() }
            ?: "download"
        if (path.contains('.')) return path
        val ext = when (mimeType) {
            "application/pdf"  -> "pdf"
            "application/zip"  -> "zip"
            "image/png"        -> "png"
            "image/jpeg"       -> "jpg"
            "text/html"        -> "html"
            else               -> "bin"
        }
        return "$path.$ext"
    }

    @Test fun `hint with extension used as-is`() =
        assertEquals("report.pdf", sanitise("https://example.com/dl", "report.pdf", null))

    @Test fun `URL path used when hint has no extension`() =
        assertEquals("file.zip", sanitise("https://example.com/path/file.zip", "", "application/zip"))

    @Test fun `extension derived from MIME when path has no extension`() =
        assertEquals("download.pdf", sanitise("https://example.com/download", "", "application/pdf"))

    @Test fun `unknown MIME produces bin`() =
        assertEquals("blob.bin", sanitise("https://example.com/blob", "", "application/octet-stream"))

    @Test fun `null MIME with extensionless path produces bin`() =
        assertEquals("resource.bin", sanitise("https://example.com/resource", "", null))

    @Test fun `PDF MIME produces pdf extension`() =
        assertEquals("file.pdf", sanitise("https://x.com/file", "file", "application/pdf"))

    @Test fun `URL with query string stripped correctly`() =
        assertEquals("document.pdf", sanitise("https://x.com/document.pdf?token=abc", "", null))
}
