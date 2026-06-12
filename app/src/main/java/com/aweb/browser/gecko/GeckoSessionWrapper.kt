package com.aweb.browser.gecko

import android.content.Context
import android.os.Handler
import android.os.Looper
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
 *
 * CRITICAL: GeckoSession() constructor AND GeckoSession.open() BOTH require
 * the Android Main thread (enforced by GeckoView internals via ThreadUtils).
 *
 * Design: the session is created LAZILY the first time [open] is called,
 * always on the Main thread. This prevents the crash:
 *   IllegalThreadStateException: Expected thread 2 ("main"), but running
 *   on thread N ("DefaultDispatcher-worker-N")
 */
class GeckoSessionWrapper(
    private val contextId: String? = null,
    private val downloadHandler: DownloadHandler? = null,
    private val permissionHandler: BrowserPermissionHandler? = null,
    private val fileUploadHandler: FileUploadHandler? = null,
    var onFullscreenChange: ((Boolean) -> Unit)? = null,
    private val appContext: Context? = null,
) {
    companion object { private const val TAG = "GeckoSessionWrapper" }

    private val mainHandler = Handler(Looper.getMainLooper())

    // StateFlows
    private val _url          = MutableStateFlow(""); val url: StateFlow<String> get() = _url
    private val _title        = MutableStateFlow("New Tab"); val title: StateFlow<String> get() = _title
    private val _progress     = MutableStateFlow(0); val progress: StateFlow<Int> get() = _progress
    private val _loading      = MutableStateFlow(false); val loading: StateFlow<Boolean> get() = _loading
    private val _canGoBack    = MutableStateFlow(false); val canGoBack: StateFlow<Boolean> get() = _canGoBack
    private val _canGoForward = MutableStateFlow(false); val canGoForward: StateFlow<Boolean> get() = _canGoForward
    private val _isSecure     = MutableStateFlow(false); val isSecure: StateFlow<Boolean> get() = _isSecure

    // ── LAZY session — only created on Main thread inside open() ─────────
    @Volatile private var _session: GeckoSession? = null

    /**
     * Returns the session if already created, or null.
     * Use [open] to create and open the session.
     */
    val session: GeckoSession?
        get() = _session

    /**
     * Creates and opens the GeckoSession. MUST run on the Main thread.
     * If called from a background thread, dispatches to Main automatically.
     * Safe to call multiple times — only executes once.
     */
    fun open() {
        if (_session?.isOpen == true) return

        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { open() }
            return
        }

        // We are on Main thread — safe to create GeckoSession
        if (_session == null) {
            _session = createSessionOnMain()
        }

        val sess = _session ?: return
        if (sess.isOpen) return

        val ctx = appContext ?: run { Log.w(TAG, "open: null appContext"); return }
        try {
            val rt = GeckoRuntimeManager.getOrInit(ctx)
            if (rt == null) {
                Log.w(TAG, "GeckoRuntime not ready — retry in 400ms")
                mainHandler.postDelayed({ open() }, 400)
                return
            }
            sess.open(rt)
            Log.d(TAG, "Session opened contextId=$contextId")
        } catch (e: Exception) {
            Log.e(TAG, "open() failed: ${e.message}", e)
        }
    }

    /**
     * Creates the GeckoSession. Called only from [open] which is already on Main.
     */
    private fun createSessionOnMain(): GeckoSession {
        return try {
            val sb = GeckoSessionSettings.Builder()
                .usePrivateMode(false)
                .allowJavascript(true)
            if (contextId != null) sb.contextId(contextId)
            val s = GeckoSession(sb.build())
            s.navigationDelegate = buildNavDelegate()
            s.progressDelegate   = buildProgressDelegate()
            s.contentDelegate    = buildContentDelegate()
            if (permissionHandler != null && appContext != null) {
                try { s.permissionDelegate = permissionHandler.buildPermissionDelegate(appContext) }
                catch (e: Exception) { Log.w(TAG, "permDelegate: ${e.message}") }
            }
            s
        } catch (e: Exception) {
            Log.e(TAG, "createSession failed: ${e.message}", e)
            // Last-resort fallback — still on Main, bare session
            GeckoSession()
        }
    }

    fun close() {
        try {
            if (_session?.isOpen == true) {
                _session?.close()
                Log.d(TAG, "Session closed")
            }
        } catch (e: Exception) { Log.w(TAG, "close: ${e.message}") }
    }

    val isOpen: Boolean get() = _session?.isOpen == true

    fun loadUrl(url: String) {
        if (_session?.isOpen != true) {
            open()
            mainHandler.postDelayed({ safeLoad(url) }, 500)
        } else safeLoad(url)
    }

    private fun safeLoad(url: String) {
        try { _session?.loadUri(url) } catch (e: Exception) { Log.e(TAG, "loadUri: ${e.message}") }
    }

    fun goBack()      { try { _session?.goBack() }    catch (e: Exception) { Log.w(TAG, "back: ${e.message}") } }
    fun goForward()   { try { _session?.goForward() } catch (e: Exception) { Log.w(TAG, "fwd: ${e.message}") } }
    fun reload()      { try { _session?.reload() }    catch (e: Exception) { Log.w(TAG, "reload: ${e.message}") } }
    fun stopLoading() { try { _session?.stop() }      catch (e: Exception) { Log.w(TAG, "stop: ${e.message}") } }

    private fun buildNavDelegate() = object : NavigationDelegate {
        override fun onLocationChange(session: GeckoSession, url: String?, perms: MutableList<PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) { _url.value = url ?: "" }
        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { _canGoBack.value = canGoBack }
        override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) { _canGoForward.value = canGoForward }
        override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
            try { session.loadUri(uri) } catch (e: Exception) { Log.w(TAG, "newSess: ${e.message}") }; return null
        }
    }

    private fun buildProgressDelegate() = object : ProgressDelegate {
        override fun onPageStart(session: GeckoSession, url: String) { _loading.value = true; _progress.value = 0; _url.value = url }
        override fun onPageStop(session: GeckoSession, success: Boolean) { _loading.value = false; _progress.value = 100 }
        override fun onProgressChange(session: GeckoSession, progress: Int) { _progress.value = progress }
        override fun onSecurityChange(session: GeckoSession, info: ProgressDelegate.SecurityInformation) {
            try { _isSecure.value = info.isSecure } catch (e: Exception) { Log.w(TAG, "sec: ${e.message}") }
        }
    }

    private fun buildContentDelegate() = object : ContentDelegate {
        override fun onTitleChange(session: GeckoSession, title: String?) { _title.value = title ?: _url.value }
        override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
            try { onFullscreenChange?.invoke(fullScreen) } catch (e: Exception) { Log.w(TAG, "fs: ${e.message}") }
        }
        override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
            try {
                val url = response.uri ?: return
                val mime = response.headers["Content-Type"]?.split(";")?.first()?.trim()
                val size = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
                val filename = response.headers["Content-Disposition"]?.let { cd ->
                    Regex("""filename\*?=(?:UTF-8'')?["']?([^"';\s]+)""", RegexOption.IGNORE_CASE).find(cd)?.groupValues?.getOrNull(1)
                } ?: url.substringAfterLast("/").substringBefore("?").takeIf { it.isNotBlank() } ?: "download"
                if (appContext != null && downloadHandler != null) {
                    permissionHandler?.requestDownloadConfirmation(url, filename, mime, size)
                        ?: downloadHandler.enqueueDownload(appContext, url, filename, mime, size)
                }
            } catch (e: Exception) { Log.e(TAG, "extResp: ${e.message}") }
        }
        override fun onCloseRequest(session: GeckoSession) {}
    }
}
