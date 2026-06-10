package com.aweb.browser.ui

import androidx.lifecycle.ViewModel
import com.aweb.browser.gecko.GeckoSessionWrapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * ViewModel for [BrowserScreen].
 *
 * Phase 1: owns a single [GeckoSessionWrapper].
 * Phase 3+: delegates to TabSessionManager for multi-tab lifecycle.
 *
 * Survives configuration changes (rotation), ensuring the GeckoSession
 * stays alive across recompositions.
 */
@HiltViewModel
class BrowserViewModel @Inject constructor() : ViewModel() {

    private val sessionWrapper = GeckoSessionWrapper(contextId = null)

    // Expose browser state as Flows consumed by BrowserScreen
    val url: StateFlow<String>        = sessionWrapper.url
    val title: StateFlow<String>      = sessionWrapper.title
    val progress: StateFlow<Int>      = sessionWrapper.progress
    val loading: StateFlow<Boolean>   = sessionWrapper.loading
    val canGoBack: StateFlow<Boolean> = sessionWrapper.canGoBack
    val canGoForward: StateFlow<Boolean> = sessionWrapper.canGoForward

    // Expose raw session so GeckoView composable can attach to it
    val session get() = sessionWrapper.session

    init {
        sessionWrapper.open()
        sessionWrapper.loadUrl(DEFAULT_HOME)
    }

    fun loadUrl(url: String) {
        val safeUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else if (url.contains(".") && !url.contains(" ")) {
            "https://$url"
        } else {
            // Treat as DuckDuckGo search query
            "https://duckduckgo.com/?q=${url.trim().replace(" ", "+")}"
        }
        sessionWrapper.loadUrl(safeUrl)
    }

    fun goBack()    = sessionWrapper.goBack()
    fun goForward() = sessionWrapper.goForward()
    fun reload()    = sessionWrapper.reload()
    fun stop()      = sessionWrapper.stopLoading()

    override fun onCleared() {
        super.onCleared()
        // Do NOT close the session when ViewModel is cleared for config change.
        // Only close it when the app truly exits.
        // sessionWrapper.close()
    }

    companion object {
        private const val DEFAULT_HOME = "https://duckduckgo.com"
    }
}
