package com.aweb.browser.gecko

import android.content.Context
import android.util.Log
import com.aweb.browser.browser.BrowserPermissionHandler
import com.aweb.browser.browser.DownloadHandler
import com.aweb.browser.browser.FileUploadHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.mozilla.geckoview.*
import org.mozilla.geckoview.GeckoSession.*

/**
 * Wraps a single [GeckoSession] and exposes observable browser state
 * as [StateFlow]s so Compose can react to changes.
 *
 * Phase 8 additions:
 *  - DownloadHandler wired to ContentDelegate.onExternalResponse
 *  - BrowserPermissionHandler wired to PermissionDelegate
 *  - FileUploadHandler wired to ContentDelegate.onFilePickerRequest
 *  - Fullscreen signal forwarded via [onFullscreenChange] callback
 *  - SSL error / security state exposed via [isSecure]
 *
 * @param contextId Workspace isolation key. Null = no isolation (Phase 1 compat).
 */
class GeckoSessionWrapper(
    private val contextId         : String? = null,
    private val downloadHandler   : DownloadHandler? = null,
    private val permissionHandler : BrowserPermissionHandler? = null,
    private val fileUploadHandler : FileUploadHandler? = null,
    var onFullscreenChange        : ((Boolean) -> Unit)? = null,
    private val appContext        : Context? = null,
) {
    companion object {
        private const val TAG = "GeckoSessionWrapper"
    }

    // ── Observable state ──────────────────────────────────────────────────

    private val _url          = MutableStateFlow("")
    val url: StateFlow<String> get() = _url

    private val _title        = MutableStateFlow("New Tab")
    val title: StateFlow<String> get() = _title

    private val _progress     = MutableStateFlow(0)
    val progress: StateFlow<Int> get() = _progress

    private val _loading      = MutableStateFlow(false)
    val loading: StateFlow<Boolean> get() = _loading

    private val _canGoBack    = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> get() = _canGoBack

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> get() = _canGoForward

    private val _isSecure     = MutableStateFlow(false)
    val isSecure: StateFlow<Boolean> get() = _isSecure

    // ── Session ───────────────────────────────────────────────────────────

    val session: GeckoSession = createSession()

    private fun createSession(): GeckoSession {
        val sb = GeckoSessionSettings.Builder()
            .usePrivateMode(false)
            .allowJavascript(true)
        if (contextId != null) sb.contextId(contextId)
        val s = GeckoSession(sb.build())
        s.navigationDelegate = buildNavigationDelegate()
        s.progressDelegate   = buildProgressDelegate()
        s.contentDelegate    = buildContentDelegate()
        if (permissionHandler != null && appContext != null) {
            s.permissionDelegate = permissionHandler.buildPermissionDelegate(appContext)
        }
        return s
    }

    fun open() {
        if (!session.isOpen) {
            session.open(GeckoRuntimeManager.runtime)
            Log.d(TAG, "Session opened contextId=$contextId")
        }
    }

    fun close() {
        if (session.isOpen) {
            session.close()
            Log.d(TAG, "Session closed contextId=$contextId")
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────

    fun loadUrl(url: String) { open(); session.loadUri(url) }
    fun goBack()             { session.goBack() }
    fun goForward()          { session.goForward() }
    fun reload()             { session.reload() }
    fun stopLoading()        { session.stop() }

    // ── Delegates ─────────────────────────────────────────────────────────

    private fun buildNavigationDelegate() = object : NavigationDelegate {
        override fun onLocationChange(
            session : GeckoSession,
            url     : String?,
            perms   : MutableList<PermissionDelegate.ContentPermission>,
        ) { _url.value = url ?: "" }

        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
            _canGoBack.value = canGoBack
        }
        override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
            _canGoForward.value = canGoForward
        }
        override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
            session.loadUri(uri)
            return null
        }
    }

    private fun buildProgressDelegate() = object : ProgressDelegate {
        override fun onPageStart(session: GeckoSession, url: String) {
            _loading.value  = true
            _progress.value = 0
            _url.value      = url
        }
        override fun onPageStop(session: GeckoSession, success: Boolean) {
            _loading.value  = false
            _progress.value = 100
        }
        override fun onProgressChange(session: GeckoSession, progress: Int) {
            _progress.value = progress
        }
        override fun onSecurityChange(
            session      : GeckoSession,
            securityInfo : ProgressDelegate.SecurityInformation,
        ) { _isSecure.value = securityInfo.isSecure }
    }

    private fun buildContentDelegate() = object : ContentDelegate {
        override fun onTitleChange(session: GeckoSession, title: String?) {
            _title.value = title ?: _url.value
        }

        override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
            Log.d(TAG, "Fullscreen: $fullScreen")
            onFullscreenChange?.invoke(fullScreen)
        }

        override fun onExternalResponse(
            session  : GeckoSession,
            response : WebResponse,
        ) {
            val url      = response.uri ?: return
            val filename = response.filename ?: url.substringAfterLast('/')
            val mime     = response.headers["Content-Type"]?.split(";")?.first()?.trim()
            val size     = response.headers["Content-Length"]?.toLongOrNull() ?: -1L

            if (appContext != null && downloadHandler != null) {
                permissionHandler?.requestDownloadConfirmation(url, filename, mime, size)
                    ?: downloadHandler.enqueueDownload(appContext, url, filename, mime, size)
            }
        }

        override fun onFilePickerRequest(
            session  : GeckoSession,
            request  : ContentDelegate.FilePickerRequest,
        ): GeckoResult<ContentDelegate.FilePickerResponse>? {
            fileUploadHandler?.onFilePickerRequest(
                callback  = object : GeckoSession.ContentDelegate.FilePickerCallback {
                    override fun confirm(uris: Array<out String>?) {
                        // handled via activity result
                    }
                },
                mimeTypes = request.mimeTypes.toTypedArray(),
                multiple  = request.isMultiple,
            )
            return null
        }

        override fun onCloseRequest(session: GeckoSession) { /* Phase 3+ handles */ }
    }
}
