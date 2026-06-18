package com.aweb.browser.gecko

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.aweb.browser.browser.BrowserPermissionHandler
import com.aweb.browser.browser.DownloadHandler
import com.aweb.browser.browser.FileUploadHandler
import com.aweb.browser.browser.UserAgentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mozilla.geckoview.*
import org.mozilla.geckoview.GeckoSession.*

/**
 * Main-thread-safe wrapper around a GeckoSession.
 *
 * The wrapper owns one GeckoSession at a time. Closing a wrapper is permanent;
 * LRU unloads remove it from TabSessionManager and a fresh wrapper is created
 * if the tab is selected again.
 */
class GeckoSessionWrapper(
    private val contextId: String? = null,
    private val downloadHandler: DownloadHandler? = null,
    private val permissionHandler: BrowserPermissionHandler? = null,
    private val fileUploadHandler: FileUploadHandler? = null,
    private val initialUaMode: UserAgentManager.UaMode = UserAgentManager.UaMode.MOBILE,
    var onFullscreenChange: ((Boolean) -> Unit)? = null,
    private val appContext: Context? = null,
    var onNewTabRequested: ((String) -> Unit)? = null,
) {
    companion object {
        private const val TAG = "GeckoSessionWrapper"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scopeJob: Job = SupervisorJob()
    private val scope = CoroutineScope(scopeJob + Dispatchers.Main.immediate)
    @Volatile private var isDestroyed = false

    // ── StateFlows ────────────────────────────────────────────────────────
    private val _url          = MutableStateFlow(""); val url: StateFlow<String> get() = _url
    private val _title        = MutableStateFlow("New Tab"); val title: StateFlow<String> get() = _title
    private val _progress     = MutableStateFlow(0); val progress: StateFlow<Int> get() = _progress
    private val _loading      = MutableStateFlow(false); val loading: StateFlow<Boolean> get() = _loading
    private val _canGoBack    = MutableStateFlow(false); val canGoBack: StateFlow<Boolean> get() = _canGoBack
    private val _canGoForward = MutableStateFlow(false); val canGoForward: StateFlow<Boolean> get() = _canGoForward
    private val _isSecure     = MutableStateFlow(false); val isSecure: StateFlow<Boolean> get() = _isSecure

    private val _sessionState = MutableStateFlow<GeckoSession?>(null)
    val sessionFlow: StateFlow<GeckoSession?> = _sessionState.asStateFlow()

    /** The GeckoSession, or null if not yet opened. */
    val session: GeckoSession? get() = _sessionState.value

    val isOpen: Boolean get() = _sessionState.value?.isOpen == true

    private var _openInFlight = false
    private var _pendingUrl: String? = null

    init {
        scope.launch {
            GeckoRuntimeManager.isReady.collect { ready ->
                if (ready) open()
            }
        }
    }

    fun open() {
        if (isDestroyed) return
        if (_sessionState.value?.isOpen == true) return

        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { open() }
            return
        }

        if (_openInFlight) return
        _openInFlight = true

        try {
            val ctx = appContext ?: run {
                Log.w(TAG, "open() called with null appContext")
                return
            }

            if (_sessionState.value == null) {
                _sessionState.value = createSessionOnMain()
            }

            val sess = _sessionState.value ?: return
            if (sess.isOpen) return

            val rt = GeckoRuntimeManager.getOrInit(ctx)
            if (rt == null) {
                Log.w(TAG, "GeckoRuntime not ready during open()")
                return
            }

            sess.open(rt)
            Log.d(TAG, "Session opened contextId=$contextId")

            _pendingUrl?.let {
                Log.i(TAG, "Processing pending URL: $it")
                sess.loadUri(it)
                _pendingUrl = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "open() failed: ${e.message}", e)
        } finally {
            _openInFlight = false
        }
    }

    fun close() {
        isDestroyed = true
        scopeJob.cancel()
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { closeSessionOnMain() }
            return
        }
        closeSessionOnMain()
    }

    private fun closeSessionOnMain() {
        try {
            _sessionState.value?.let { if (it.isOpen) it.close() }
            _sessionState.value = null
            Log.d(TAG, "Session closed")
        } catch (e: Exception) {
            Log.w(TAG, "close: ${e.message}")
        }
    }

    fun setActive(active: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { setActive(active) }
            return
        }
        try { session?.setActive(active) }
        catch (e: Exception) { Log.w(TAG, "setActive($active): ${e.message}") }
    }

    fun loadUrl(url: String) {
        if (isDestroyed) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { loadUrl(url) }
            return
        }
        val sess = _sessionState.value
        if (sess?.isOpen == true) {
            try {
                sess.loadUri(url)
            } catch (e: Exception) {
                Log.e(TAG, "loadUri($url): ${e.message}")
            }
        } else {
            Log.d(TAG, "Session not open, queueing URL: $url")
            _pendingUrl = url
            open()
        }
    }

    fun goBack()      { mainHandler.post { try { session?.goBack() }    catch (e: Exception) { Log.w(TAG, "back: ${e.message}") } } }
    fun goForward()   { mainHandler.post { try { session?.goForward() } catch (e: Exception) { Log.w(TAG, "fwd: ${e.message}") } } }
    fun reload()      { mainHandler.post { try { session?.reload() }    catch (e: Exception) { Log.w(TAG, "reload: ${e.message}") } } }
    fun stopLoading() { mainHandler.post { try { session?.stop() }      catch (e: Exception) { Log.w(TAG, "stop: ${e.message}") } } }

    private fun createSessionOnMain(): GeckoSession {
        return try {
            val sb = GeckoSessionSettings.Builder()
                .usePrivateMode(false)
                .allowJavascript(true)
                .userAgentMode(if (initialUaMode == UserAgentManager.UaMode.DESKTOP)
                    GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                    else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)

            if (contextId != null) sb.contextId(contextId)
            val s = GeckoSession(sb.build())
            s.navigationDelegate = buildNavDelegate()
            s.progressDelegate   = buildProgressDelegate()
            s.contentDelegate    = buildContentDelegate()
            if (fileUploadHandler != null && appContext != null) {
                try { s.promptDelegate = buildPromptDelegate(appContext) }
                catch (e: Exception) { Log.w(TAG, "promptDelegate: ${e.message}") }
            }
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

    private fun buildNavDelegate() = object : NavigationDelegate {
        override fun onLocationChange(s: GeckoSession, url: String?, p: MutableList<PermissionDelegate.ContentPermission>, h: Boolean) {
            _url.value = url ?: ""
        }
        override fun onCanGoBack(s: GeckoSession, c: Boolean) { _canGoBack.value = c }
        override fun onCanGoForward(s: GeckoSession, c: Boolean) { _canGoForward.value = c }
        override fun onLoadRequest(s: GeckoSession, request: NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
            if (request.target == NavigationDelegate.TARGET_WINDOW_NEW && request.uri.isNotBlank()) {
                mainHandler.post { onNewTabRequested?.invoke(request.uri) }
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }
            return null
        }
        override fun onNewSession(s: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
            mainHandler.post { onNewTabRequested?.invoke(uri) }
            return null
        }
    }

    private fun buildPromptDelegate(ctx: Context) = object : PromptDelegate {
        override fun onFilePrompt(
            session: GeckoSession,
            prompt : PromptDelegate.FilePrompt,
        ): GeckoResult<PromptDelegate.PromptResponse> =
            fileUploadHandler?.onFilePrompt(ctx, prompt) ?: GeckoResult.fromValue(prompt.dismiss())

        override fun onPopupPrompt(
            session: GeckoSession,
            prompt : PromptDelegate.PopupPrompt,
        ): GeckoResult<PromptDelegate.PromptResponse> {
            val uri = prompt.targetUri
            if (!uri.isNullOrBlank()) {
                mainHandler.post { onNewTabRequested?.invoke(uri) }
                return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY))
            }
            return GeckoResult.fromValue(prompt.dismiss())
        }
    }

    private fun buildProgressDelegate() = object : ProgressDelegate {
        override fun onPageStart(s: GeckoSession, url: String) { _loading.value = true; _progress.value = 0; _url.value = url }
        override fun onPageStop(s: GeckoSession, success: Boolean) { _loading.value = false; _progress.value = 100 }
        override fun onProgressChange(s: GeckoSession, progress: Int) { _progress.value = progress }
        override fun onSecurityChange(
            s: GeckoSession,
            info: GeckoSession.ProgressDelegate.SecurityInformation,
        ) { _isSecure.value = info.isSecure }
    }

    private fun buildContentDelegate() = object : ContentDelegate {
        override fun onTitleChange(s: GeckoSession, title: String?) { _title.value = title ?: _url.value }
        override fun onFullScreen(s: GeckoSession, fs: Boolean) { onFullscreenChange?.invoke(fs) }
        override fun onExternalResponse(s: GeckoSession, response: WebResponse) {
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
        }
    }
}
