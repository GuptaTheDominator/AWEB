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
 * Wraps a single [GeckoSession] and exposes observable browser state as [StateFlow]s.
 * Compiled against GeckoView nightly-omni 132.0.20240929094629.
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

    fun loadUrl(url: String) { open(); session.loadUri(url) }
    fun goBack()             { session.goBack() }
    fun goForward()          { session.goForward() }
    fun reload()             { session.reload() }
    fun stopLoading()        { session.stop() }

    private fun buildNavigationDelegate() = object : NavigationDelegate {
        // GeckoView 132: onLocationChange(session, url, perms, hasUserGesture: Boolean)
        override fun onLocationChange(
            session        : GeckoSession,
            url            : String?,
            perms          : MutableList<PermissionDelegate.ContentPermission>,
            hasUserGesture : Boolean,
        ) { _url.value = url ?: "" }

        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
            _canGoBack.value = canGoBack
        }
        override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
            _canGoForward.value = canGoForward
        }
        override fun onNewSession(
            session : GeckoSession,
            uri     : String,
        ): GeckoResult<GeckoSession>? {
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

        // GeckoView 132: WebResponse has .uri and .headers — no .filename field
        override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
            val url      = response.uri ?: return
            val mime     = response.headers["Content-Type"]?.split(";")?.first()?.trim()
            val size     = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
            val filename = response.headers["Content-Disposition"]
                ?.let { cd ->
                    Regex("""filename\*?=(?:UTF-8'')?["']?([^"';\s]+)""", RegexOption.IGNORE_CASE)
                        .find(cd)?.groupValues?.getOrNull(1)
                } ?: url.substringAfterLast('/').substringBefore('?').takeIf { it.isNotBlank() }
                ?: "download"

            if (appContext != null && downloadHandler != null) {
                permissionHandler?.requestDownloadConfirmation(url, filename, mime, size)
                    ?: downloadHandler.enqueueDownload(appContext, url, filename, mime, size)
            }
        }

        override fun onCloseRequest(session: GeckoSession) { /* handled by tab close */ }
    }
}
