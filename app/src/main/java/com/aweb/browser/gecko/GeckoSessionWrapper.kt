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
 * Wraps a single [GeckoSession] and exposes observable browser state.
 * All GeckoView calls are wrapped in try-catch to prevent crashes propagating
 * to the UI layer.
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
        return try {
            val sb = GeckoSessionSettings.Builder()
                .usePrivateMode(false)
                .allowJavascript(true)
            if (contextId != null) sb.contextId(contextId)
            val s = GeckoSession(sb.build())
            s.navigationDelegate = buildNavigationDelegate()
            s.progressDelegate   = buildProgressDelegate()
            s.contentDelegate    = buildContentDelegate()
            if (permissionHandler != null && appContext != null) {
                try { s.permissionDelegate = permissionHandler.buildPermissionDelegate(appContext) }
                catch (e: Exception) { Log.w(TAG, "permissionDelegate setup failed: ${e.message}") }
            }
            s
        } catch (e: Exception) {
            Log.e(TAG, "createSession fallback: ${e.message}", e)
            // Return a bare session without delegates rather than crashing
            GeckoSession()
        }
    }

    fun open() {
        if (session.isOpen) return
        val ctx = appContext ?: run {
            Log.w(TAG, "open() called with null appContext — skipping")
            return
        }
        try {
            val rt = GeckoRuntimeManager.getOrInit(ctx)
            session.open(rt)
            Log.d(TAG, "Session opened contextId=$contextId")
        } catch (e: Exception) {
            Log.e(TAG, "Session open() failed: ${e.message}", e)
        }
    }

    fun close() {
        try {
            if (session.isOpen) {
                session.close()
                Log.d(TAG, "Session closed contextId=$contextId")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Session close() failed: ${e.message}")
        }
    }

    fun loadUrl(url: String) {
        try {
            open()
            session.loadUri(url)
        } catch (e: Exception) {
            Log.e(TAG, "loadUrl($url) failed: ${e.message}")
        }
    }

    fun goBack()      { try { session.goBack() }    catch (e: Exception) { Log.w(TAG, "goBack: ${e.message}") } }
    fun goForward()   { try { session.goForward() } catch (e: Exception) { Log.w(TAG, "goForward: ${e.message}") } }
    fun reload()      { try { session.reload() }    catch (e: Exception) { Log.w(TAG, "reload: ${e.message}") } }
    fun stopLoading() { try { session.stop() }      catch (e: Exception) { Log.w(TAG, "stop: ${e.message}") } }

    private fun buildNavigationDelegate() = object : NavigationDelegate {
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
        override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
            try { session.loadUri(uri) } catch (e: Exception) { Log.w(TAG, "onNewSession: ${e.message}") }
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
        ) {
            try { _isSecure.value = securityInfo.isSecure }
            catch (e: Exception) { Log.w(TAG, "onSecurityChange: ${e.message}") }
        }
    }

    private fun buildContentDelegate() = object : ContentDelegate {
        override fun onTitleChange(session: GeckoSession, title: String?) {
            _title.value = title ?: _url.value
        }
        override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
            try { onFullscreenChange?.invoke(fullScreen) }
            catch (e: Exception) { Log.w(TAG, "onFullScreen: ${e.message}") }
        }
        override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
            try {
                val url      = response.uri ?: return
                val mime     = response.headers["Content-Type"]?.split(";")?.first()?.trim()
                val size     = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
                val filename = response.headers["Content-Disposition"]
                    ?.let { cd ->
                        Regex("""filename\*?=(?:UTF-8'')?["']?([^"';\s]+)""", RegexOption.IGNORE_CASE)
                            .find(cd)?.groupValues?.getOrNull(1)
                    } ?: url.substringAfterLast('/').substringBefore('?')
                        .takeIf { it.isNotBlank() } ?: "download"

                if (appContext != null && downloadHandler != null) {
                    permissionHandler?.requestDownloadConfirmation(url, filename, mime, size)
                        ?: downloadHandler.enqueueDownload(appContext, url, filename, mime, size)
                }
            } catch (e: Exception) {
                Log.e(TAG, "onExternalResponse: ${e.message}")
            }
        }
        override fun onCloseRequest(session: GeckoSession) {}
    }
}
