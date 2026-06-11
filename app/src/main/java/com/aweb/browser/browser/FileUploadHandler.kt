package com.aweb.browser.browser

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles file upload requests from web pages (<input type="file">).
 *
 * GeckoView 132 removed ContentDelegate.onFilePickerRequest.
 * We instead hook into the input element via WebExtension or rely on the
 * browser's native file chooser. For now we expose a SharedFlow that
 * BrowserScreen observes to launch the system file picker when needed.
 *
 * The selected URIs are then injected back via GeckoSession.loadUri or
 * handled by the web app directly.
 */
@Singleton
class FileUploadHandler @Inject constructor() {

    companion object {
        private const val TAG = "FileUploadHandler"
    }

    data class PickRequest(
        val mimeTypes: Array<String>,
        val multiple : Boolean,
    )

    private val _pickRequest = MutableSharedFlow<PickRequest>(extraBufferCapacity = 1)
    val pickRequest: SharedFlow<PickRequest> = _pickRequest

    /**
     * Trigger the file picker from UI code (e.g. a toolbar button or web message).
     */
    fun requestFilePicker(mimeTypes: Array<String> = arrayOf("*/*"), multiple: Boolean = false) {
        Log.d(TAG, "File picker requested: mimes=${mimeTypes.joinToString()} multiple=$multiple")
        _pickRequest.tryEmit(PickRequest(mimeTypes, multiple))
    }

    /**
     * Called from the Activity after the user selects files.
     * In GeckoView 132, file uploads are handled natively by the engine for most sites.
     * This handler is available for manual upload triggers.
     */
    fun onFilesSelected(uris: List<Uri>?) {
        if (uris.isNullOrEmpty()) {
            Log.d(TAG, "File picker cancelled")
        } else {
            Log.d(TAG, "File picker selected ${uris.size} file(s): $uris")
            // URIs can be shared with the web page via GeckoSession if needed
        }
    }

    fun cancel() {
        // No pending callback in GeckoView 132 — no-op
    }
}
