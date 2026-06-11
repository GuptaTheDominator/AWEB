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
 *
 * We hand these straight to Android's [DownloadManager] which:
 *  - Shows a system progress notification.
 *  - Survives app restarts.
 *  - Saves the file to the user's Downloads folder.
 *
 * The user is always asked to confirm via [DownloadConfirmEvent] before
 * the download starts — [BrowserPermissionHandler] surfaces the dialog.
 */
@Singleton
class DownloadHandler @Inject constructor() {

    companion object {
        private const val TAG = "DownloadHandler"
    }

    /**
     * Called from [GeckoSessionWrapper] when Gecko signals a download.
     * Immediately enqueues to [DownloadManager] and returns the download ID.
     */
    fun enqueueDownload(
        context  : Context,
        url      : String,
        filename : String,
        mimeType : String?,
        size     : Long = -1L,
    ): Long {
        val safeFilename = sanitiseFilename(url, filename, mimeType)
        val safeMime     = mimeType?.takeIf { it.isNotBlank() }
            ?: guessMimeType(safeFilename)
            ?: "application/octet-stream"

        Log.i(TAG, "Enqueuing download: $safeFilename ($safeMime) size=$size")

        val request = DownloadManager.Request(url.toUri()).apply {
            setTitle(safeFilename)
            setDescription("Downloading via AWEB")
            setMimeType(safeMime)
            setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeFilename)
            // Allow download over WiFi and mobile data
            setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_WIFI or
                DownloadManager.Request.NETWORK_MOBILE
            )
        }

        return (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager)
            .enqueue(request)
    }

    private fun sanitiseFilename(url: String, hint: String, mimeType: String?): String {
        // If hint already looks like a good filename, use it
        if (hint.isNotBlank() && hint.contains('.')) return hint

        // Derive from URL path
        val path = Uri.parse(url).lastPathSegment ?: "download"
        if (path.contains('.')) return path

        // Append extension from MIME type
        val ext = mimeType?.let {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(it)
        } ?: "bin"
        return "$path.$ext"
    }

    private fun guessMimeType(filename: String): String? {
        val ext = filename.substringAfterLast('.', "")
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
    }
}
