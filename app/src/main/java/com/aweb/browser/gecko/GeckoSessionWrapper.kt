package com.aweb.browser.gecko

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
import org.mozilla.geckoview.GeckoSession.ProgressDelegate
import org.mozilla.geckoview.GeckoSession.ContentDelegate
import org.mozilla.geckoview.GeckoSessionSettings

/**
 * Wraps a single [GeckoSession] and exposes observable browser state
 * as Kotlin [StateFlow]s so Compose can react to changes.
 *
 * Phase 1: single session, no contextId.
 * Phase 2: contextId added for workspace isolation.
 *
 * @param contextId  Optional GeckoView contextId for workspace isolation.
 *                   Pass null in Phase 1, a UUID string in Phase 2+.
 */
class GeckoSessionWrapper(
    private val contextId: String? = null
) {

    companion object {
        private const val TAG = "GeckoSessionWrapper"
    }

    // ── Observable state ──────────────────────────────────────────────────

    private val _url        = MutableStateFlow("")
    val url: StateFlow<String> get() = _url

    private val _title      = MutableStateFlow("New Tab")
    val title: StateFlow<String> get() = _title

    private val _progress   = MutableStateFlow(0)
    val progress: StateFlow<Int> get() = _progress

    private val _loading    = MutableStateFlow(false)
    val loading: StateFlow<Boolean> get() = _loading

    private val _canGoBack  = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> get() = _canGoBack

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> get() = _canGoForward

    // ── Session ───────────────────────────────────────────────────────────

    val session: GeckoSession = createSession()

    private fun createSession(): GeckoSession {
        val settingsBuilder = GeckoSessionSettings.Builder()
            .usePrivateMode(false)   // AWEB never uses incognito
            .allowJavascript(true)

        if (contextId != null) {
            settingsBuilder.contextId(contextId)
        }

        val s = GeckoSession(settingsBuilder.build())
        s.navigationDelegate = buildNavigationDelegate()
        s.progressDelegate   = buildProgressDelegate()
        s.contentDelegate    = buildContentDelegate()
        return s
    }

    fun open() {
        if (!session.isOpen) {
            session.open(GeckoRuntimeManager.runtime)
            Log.d(TAG, "Session opened (contextId=$contextId)")
        }
    }

    fun close() {
        if (session.isOpen) {
            session.close()
            Log.d(TAG, "Session closed (contextId=$contextId)")
        }
    }

    // ── Navigation API ────────────────────────────────────────────────────

    fun loadUrl(url: String) {
        open()
        session.loadUri(url)
    }

    fun goBack()    { session.goBack() }
    fun goForward() { session.goForward() }
    fun reload()    { session.reload() }
    fun stopLoading() { session.stop() }

    // ── Delegates ─────────────────────────────────────────────────────────

    private fun buildNavigationDelegate() = object : NavigationDelegate {
        override fun onLocationChange(
            session: GeckoSession,
            url: String?,
            perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>
        ) {
            _url.value = url ?: ""
        }

        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
            _canGoBack.value = canGoBack
        }

        override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
            _canGoForward.value = canGoForward
        }

        override fun onNewSession(
            session: GeckoSession,
            uri: String
        ): GeckoResult<GeckoSession>? {
            // Phase 1: open links in same session.
            // Phase 3+: open new tab.
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
            session: GeckoSession,
            securityInfo: ProgressDelegate.SecurityInformation
        ) {
            Log.d(TAG, "Security: isSecure=${securityInfo.isSecure}")
        }
    }

    private fun buildContentDelegate() = object : ContentDelegate {
        override fun onTitleChange(session: GeckoSession, title: String?) {
            _title.value = title ?: _url.value
        }

        override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
            // Phase 8: handle fullscreen video
            Log.d(TAG, "Fullscreen: $fullScreen")
        }

        override fun onCloseRequest(session: GeckoSession) {
            // Phase 3+: close tab
        }
    }
}
