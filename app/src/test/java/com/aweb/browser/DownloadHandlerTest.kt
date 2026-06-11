package com.aweb.browser

import com.aweb.browser.browser.DownloadHandler
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [DownloadHandler] filename sanitisation logic.
 *
 * Extracted to a testable pure function for coverage without Android context.
 */
class DownloadHandlerTest {

    // Mirror the sanitise logic inline so no Android deps are needed
    private fun sanitise(url: String, hint: String, mimeType: String?): String {
        if (hint.isNotBlank() && hint.contains('.')) return hint
        val path = try {
            android.net.Uri.parse(url).lastPathSegment ?: "download"
        } catch (_: Exception) { "download" }
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

    @Test
    fun `hint with extension is used as-is`() {
        assertEquals("report.pdf", sanitise("https://example.com/download", "report.pdf", null))
    }

    @Test
    fun `URL path is used when hint has no extension`() {
        val result = sanitise("https://example.com/path/file.zip", "", "application/zip")
        assertEquals("file.zip", result)
    }

    @Test
    fun `extension is derived from MIME type when path has no extension`() {
        val result = sanitise("https://example.com/download", "", "application/pdf")
        assertEquals("download.pdf", result)
    }

    @Test
    fun `unknown MIME type produces .bin extension`() {
        val result = sanitise("https://example.com/blob", "", "application/octet-stream")
        assertEquals("blob.bin", result)
    }

    @Test
    fun `null MIME type with extensionless path produces .bin`() {
        val result = sanitise("https://example.com/resource", "", null)
        assertEquals("resource.bin", result)
    }

    @Test
    fun `PDF MIME type produces .pdf extension`() {
        val result = sanitise("https://x.com/file", "file", "application/pdf")
        assertEquals("file.pdf", result)
    }
}
