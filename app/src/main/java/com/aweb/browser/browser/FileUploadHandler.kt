package com.aweb.browser.browser

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles file upload requests from web pages (<input type="file">).
 *
 * GeckoView surfaces these through PromptDelegate.onFilePrompt(). We keep the
 * pending Gecko prompt/result here, ask Compose to launch the Android document
 * picker, and complete the GeckoResult when the Activity result returns.
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

    private data class PendingFilePrompt(
        val context: Context,
        val prompt : GeckoSession.PromptDelegate.FilePrompt,
        val result : GeckoResult<GeckoSession.PromptDelegate.PromptResponse>,
    )

    private val lock = Any()
    private var pendingPrompt: PendingFilePrompt? = null

    private val _pickRequest = MutableSharedFlow<PickRequest>(extraBufferCapacity = 1)
    val pickRequest: SharedFlow<PickRequest> = _pickRequest

    fun onFilePrompt(
        context: Context,
        prompt : GeckoSession.PromptDelegate.FilePrompt,
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        val appContext = context.applicationContext

        synchronized(lock) {
            pendingPrompt?.let { old ->
                runCatching { old.result.complete(old.prompt.dismiss()) }
            }
            pendingPrompt = PendingFilePrompt(appContext, prompt, result)
        }

        val mimeTypes = prompt.mimeTypes
            ?.takeIf { it.isNotEmpty() }
            ?: arrayOf("*/*")
        val multiple = prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE

        Log.d(TAG, "Gecko file prompt: mimes=${mimeTypes.joinToString()} multiple=$multiple")

        if (!_pickRequest.tryEmit(PickRequest(mimeTypes, multiple))) {
            Log.w(TAG, "Could not emit file picker request; dismissing prompt")
            synchronized(lock) { pendingPrompt = null }
            result.complete(prompt.dismiss())
        }

        return result
    }

    /**
     * Manual/UI-triggered file picker hook. This does not complete a Gecko
     * prompt; it only emits a picker request for future extension use.
     */
    fun requestFilePicker(mimeTypes: Array<String> = arrayOf("*/*"), multiple: Boolean = false) {
        Log.d(TAG, "Manual file picker requested: mimes=${mimeTypes.joinToString()} multiple=$multiple")
        _pickRequest.tryEmit(PickRequest(mimeTypes, multiple))
    }

    /** Called from the Activity after the user selects or cancels files. */
    fun onFilesSelected(uris: List<Uri>?) {
        val pending = synchronized(lock) {
            pendingPrompt.also { pendingPrompt = null }
        }

        if (pending == null) {
            Log.d(TAG, "File picker result with no pending Gecko prompt")
            return
        }

        try {
            val response = if (uris.isNullOrEmpty()) {
                Log.d(TAG, "File picker cancelled")
                pending.prompt.dismiss()
            } else if (pending.prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE) {
                Log.d(TAG, "File picker selected ${uris.size} file(s)")
                pending.prompt.confirm(pending.context, uris.toTypedArray())
            } else {
                Log.d(TAG, "File picker selected one file")
                pending.prompt.confirm(pending.context, uris.first())
            }
            pending.result.complete(response)
        } catch (e: Exception) {
            Log.e(TAG, "Completing file prompt failed: ${e.message}", e)
            runCatching { pending.result.complete(pending.prompt.dismiss()) }
        }
    }

    fun cancel() {
        val pending = synchronized(lock) {
            pendingPrompt.also { pendingPrompt = null }
        }
        pending?.let { runCatching { it.result.complete(it.prompt.dismiss()) } }
    }
}
