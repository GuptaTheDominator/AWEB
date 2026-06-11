package com.aweb.browser.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.mozilla.geckoview.GeckoSession
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles file chooser requests from web pages (e.g. <input type="file">).
 *
 * GeckoView fires [GeckoSession.ContentDelegate.onFilePickerRequest] when a
 * page requests a file upload. We launch the Android system file picker and
 * return the selected URIs back to Gecko via the callback.
 *
 * The launcher is registered in [MainActivity] via
 * [ActivityResultContracts.OpenMultipleDocuments] and injected here.
 */
@Singleton
class FileUploadHandler @Inject constructor() {

    companion object {
        private const val TAG = "FileUploadHandler"
    }

    // Pending callback — held while the file picker is open
    private var pendingCallback: GeckoSession.ContentDelegate.FilePickerCallback? = null

    // Event to signal the Activity to launch the picker
    private val _pickRequest = MutableSharedFlow<PickRequest>(extraBufferCapacity = 1)
    val pickRequest: SharedFlow<PickRequest> = _pickRequest

    data class PickRequest(
        val mimeTypes: Array<String>,
        val multiple : Boolean,
    )

    /**
     * Called from [GeckoSessionWrapper]'s content delegate when a file input is activated.
     */
    fun onFilePickerRequest(
        callback  : GeckoSession.ContentDelegate.FilePickerCallback,
        mimeTypes : Array<String>,
        multiple  : Boolean,
    ) {
        Log.d(TAG, "File picker requested: mimes=${mimeTypes.joinToString()} multiple=$multiple")
        pendingCallback = callback
        _pickRequest.tryEmit(PickRequest(mimeTypes, multiple))
    }

    /**
     * Called from the Activity after the user selects files (or cancels).
     * [uris] is null or empty if the user cancelled.
     */
    fun onFilesSelected(uris: List<Uri>?) {
        val cb = pendingCallback ?: return
        pendingCallback = null
        if (uris.isNullOrEmpty()) {
            Log.d(TAG, "File picker cancelled")
            cb.confirm(emptyArray())
        } else {
            Log.d(TAG, "File picker selected ${uris.size} file(s)")
            cb.confirm(uris.map { it.toString() }.toTypedArray())
        }
    }

    /** Cancel pending request — call on Activity destroy. */
    fun cancel() {
        pendingCallback?.confirm(emptyArray())
        pendingCallback = null
    }
}
