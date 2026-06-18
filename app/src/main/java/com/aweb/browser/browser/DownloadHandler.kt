package com.aweb.browser.browser

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.ContentDelegate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles file downloads triggered by GeckoView.
 *
 * GeckoView fires [ContentDelegate.onExternalResponse] for every response
 * that should be downloaded rather than displayed (e.g. application/zip,
 * application/pdf, binary blobs, etc.).
 */
@Singleton
class DownloadHandler @Inject constructor() {

    companion object {
        private const val TAG = "DownloadHandler"
        private val INVALID_FILENAME_CHARS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
        private const val MAX_FILENAME_LENGTH = 120
    }

    fun enqueueDownload(
        context  : Context,
        url      : String,
        filename : String,
        mimeType : String?,
        size     : Long = -1L,
    ): Long {
        val parsedUri = url.toUri()
        val scheme = parsedUri.scheme?.lowercase()
        require(scheme == "http" || scheme == "https") {
            "Unsupported download URL scheme"
        }

        val safeFilename = sanitiseFilename(url, filename, mimeType)
        val safeMime     = mimeType?.takeIf { it.isNotBlank() }
            ?: guessMimeType(safeFilename)
            ?: "application/octet-stream"

        Log.i(TAG, "Enqueuing download: ${safeFilename.take(32)} ($safeMime) size=$size")

        val request = DownloadManager.Request(parsedUri).apply {
            setTitle(safeFilename)
            setDescription("Downloading via AWEB")
            setMimeType(safeMime)
            setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeFilename)
            setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_WIFI or
                    DownloadManager.Request.NETWORK_MOBILE
            )
        }

        return (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager)
            .enqueue(request)
    }

    internal fun sanitiseFilename(url: String, hint: String, mimeType: String?): String {
        val candidate = sequenceOf(
            hint,
            Uri.parse(url).lastPathSegment ?: "",
            url.substringAfterLast('/').substringBefore('?').substringBefore('#'),
            "download",
        ).firstOrNull { it.isNotBlank() } ?: "download"

        var cleaned = Uri.decode(candidate)
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()
            .replace(INVALID_FILENAME_CHARS, "_")
            .replace(Regex("_+"), "_")
            .trim('.', ' ', '_')

        if (cleaned.isBlank() || cleaned == "." || cleaned == "..") cleaned = "download"

        if (!cleaned.contains('.')) {
            val ext = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                ?: guessMimeType(cleaned)?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                ?: "bin"
            cleaned = "$cleaned.$ext"
        }

        if (cleaned.length > MAX_FILENAME_LENGTH) {
            val ext = cleaned.substringAfterLast('.', missingDelimiterValue = "")
            cleaned = if (ext.isNotBlank() && ext.length < 12) {
                cleaned.take(MAX_FILENAME_LENGTH - ext.length - 1) + "." + ext
            } else cleaned.take(MAX_FILENAME_LENGTH)
        }

        return cleaned
    }

    private fun guessMimeType(filename: String): String? {
        val ext = filename.substringAfterLast('.', "")
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
    }
}
