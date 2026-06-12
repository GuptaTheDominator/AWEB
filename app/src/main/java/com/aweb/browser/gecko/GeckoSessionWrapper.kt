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
 * Wraps a single [GeckoSession] with thread-safe lazy creation.
 *
 * RULES:
 *  1. GeckoSession() constructor requires the Main thread.
 *  2. GeckoSession.open() requires the Main thread.
 *  3. GeckoSession.loadUri() requires the Main thread.
 *  4. Only ONE open() call may be in-flight at any time.
 *
 * All three are guaranteed here via:
 *  - Lazy _session created inside open() which enforces Main.
 *  - @Volatile _openInFlight flag prevents double-open races.
 *  - loadUrl() always posts safeLoad to mainHandler (Main thread).
 *  - TabSessionManager.getOrCreate() posts the initial load via loadUrl().
 */
class GeckoSessionWrapper(
    private val contextId: String? = null,
    private val downloadHandler: DownloadHandler? = null,
    private val permissionHandler: BrowserPermissionHandler? = null,
    private val fileUploadHandler: FileUploadHandler? = null,
    var onFullscreenChange: ((Boolean) -> Unit)? = null,
    private val appContext: Context? = null,
) {
    companion object {
        private const val TAG = "GeckoSessionWrapper"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    // ── StateFlows ────────────────────────────────────────────────────────
    private val _url          = MutableStateFlow(""); val url: StateFlow<String> get() = _url
    private val _title        = MutableStateFlow("New Tab"); val title: StateFlow<String> get() = _title
    private val _progress     = MutableStateFlow(0); val progress: StateFlow<Int> get() = _progress
    private val _loading      = MutableStateFlow(false); val loading: StateFlow<Boolean> get() = _loading
    private val _canGoBack    = MutableStateFlow(false); val canGoBack: StateFlow<Boolean> get() = _canGoBack
    private val _canGoForward = MutableStateFlow(false); val canGoForward: StateFlow<Boolean> get() = _canGoForward
    private val _isSecure     = MutableStateFlow(false); val isSecure: StateFlow<Boolean> get() = _isSecure

    // ── Lazy session — created only on Main thread inside open() ──────────
    @Volatile private var _session: GeckoSession? = null

    /** The GeckoSession, or null if not yet opened. */
    val session: GeckoSession? get() = _session

    val isOpen: Boolean get() = _session?.isOpen == true

    // Guard: prevents multiple concurrent open() calls
    @Volatile private var _openInFlight = false

    /**
     * Opens the session. Must ultimately run on the Main thread.
     * Safe to call from any thread — auto-dispatches to Main.
     * Safe to call multiple times — only opens once.
     */
    fun open() {
        // If already open, nothing to do
        if (_session?.isOpen == true) return

        // Dispatch to Main thread if not already there
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { open() }
            return
        }

        // We are on Main. Guard against double-open race.
        if (_openInFlight) return
        _openInFlight = true

        try {
            val ctx = appContext ?: run {
                Log.w(TAG, "open() called with null appContext")
                _openInFlight = false
                return
            }

            // Create session if it doesn't exist yet
            if (_session == null) {
                _session = createSessionOnMain()
            }

            val sess = _session ?: run {
                _openInFlight = false
                return
            }

            // If already open (race), done
            if (sess.isOpen) {
                _openInFlight = false
                return
            }

            // Get or init GeckoRuntime
            val rt = GeckoRuntimeManager.getOrInit(ctx)
            if (rt == null) {
                Log.w(TAG, "GeckoRuntime not ready — retry in 500ms")
                _openInFlight = false
                mainHandler.postDelayed({ open() }, 500)
                return
            }

            sess.open(rt)
            Log.d(TAG, "Session opened contextId=$contextId")
        } catch (e: Exception) {
            Log.e(TAG, "open() failed: ${e.message}", e)
        } finally {
            _openInFlight = false
        }
    }

    fun close() {
        try {
            _session?.let { if (it.isOpen) it.close() }
            Log.d(TAG, "Session closed")
        } catch (e: Exception) {
            Log.w(TAG, "close: ${e.message}")
        }
    }

    /**
     * Loads a URL. Always safe to call from any thread.
     * If the session isn't open yet, opens it first then loads.
     * loadUri() is posted to Main to ensure thread safety.
     */
    fun loadUrl(url: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            // Dispatch entirely to Main
            mainHandler.post { loadUrl(url) }
            return
        }
        // We are on Main thread
        if (_session?.isOpen != true) {
            // Session not open yet — open first, then load
            if (!_openInFlight) open()
            // Schedule load after open completes (open() is synchronous on Main,
            // so if open() succeeded the session is open now; if it needed to
            // wait for GeckoRuntime, postDelayed will retry)
            if (_session?.isOpen == true) {
                safeLoad(url)
            } else {
                // GeckoRuntime wasn't ready; retry after a moment
                mainHandler.postDelayed({ loadUrl(url) }, 600)
            }
        } else {
            safeLoad(url)
        }
    }

    private fun safeLoad(url: String) {
        // Must be on Main thread
        try {
            _session?.loadUri(url)
        } catch (e: Exception) {
            Log.e(TAG, "loadUri($url): ${e.message}")
        }
    }

    fun goBack()      { mainHandler.post { try { _session?.goBack() }    catch (e: Exception) { Log.w(TAG, "back: ${e.message}") } } }
    fun goForward()   { mainHandler.post { try { _session?.goForward() } catch (e: Exception) { Log.w(TAG, "fwd: ${e.message}") } } }
    fun reload()      { mainHandler.post { try { _session?.reload() }    catch (e: Exception) { Log.w(TAG, "reload: ${e.message}") } } }
    fun stopLoading() { mainHandler.post { try { _session?.stop() }      catch (e: Exception) { Log.w(TAG, "stop: ${e.message}") } } }

    // ── Session factory ───────────────────────────────────────────────────

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
            Log.e(TAG, "createSessionOnMain fallback: ${e.message}", e)
            GeckoSession()
        }
    }

    // ── Delegates ─────────────────────────────────────────────────────────

    private fun buildNavDelegate() = object : NavigationDelegate {
        override fun onLocationChange(session: GeckoSession, url: String?,
            perms: MutableList<PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
            _url.value = url ?: ""
        }
        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { _canGoBack.value = canGoBack }
        override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) { _canGoForward.value = canGoForward }
        override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
            mainHandler.post { try { session.loadUri(uri) } catch (e: Exception) { Log.w(TAG, "newSess: ${e.message}") } }
            return null
        }
    }

    private fun buildProgressDelegate() = object : ProgressDelegate {
        override fun onPageStart(session: GeckoSession, url: String) {
            _loading.value = true; _progress.value = 0; _url.value = url
        }
        override fun onPageStop(session: GeckoSession, success: Boolean) {
            _loading.value = false; _progress.value = 100
        }
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
                    Regex("""filename\*?=(?:UTF-8'')?["']?([^"';\s]+)""", RegexOption.IGNORE_CASE)
                        .find(cd)?.groupValues?.getOrNull(1)
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
