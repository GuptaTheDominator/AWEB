package com.aweb.browser.browser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps GeckoView's find-in-page API (SessionFinder).
 * GeckoView 132: SessionFinder.find() returns GeckoResult<GeckoSession.FinderResult>.
 */
@Singleton
class FindInPageHandler @Inject constructor() {

    data class FindResult(
        val current: Int     = 0,
        val total  : Int     = 0,
        val found  : Boolean = false,
    )

    private val _isVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    private val _result = MutableStateFlow(FindResult())
    val result: StateFlow<FindResult> = _result.asStateFlow()

    private var currentSession: GeckoSession? = null

    fun attachSession(session: GeckoSession) { currentSession = session }

    fun show() { _isVisible.value = true }

    fun hide() {
        _isVisible.value = false
        clear()
    }

    fun find(query: String, forward: Boolean = true) {
        if (query.isBlank()) { clear(); return }
        val flags = if (forward) 0 else GeckoSession.FINDER_FIND_BACKWARDS
        currentSession?.finder?.find(query, flags)?.then { finderResult ->
            if (finderResult != null) {
                _result.value = FindResult(
                    current = finderResult.current,
                    total   = finderResult.total,
                    found   = finderResult.found,
                )
            }
            GeckoResult.fromValue(null)
        }
    }

    fun clear() {
        currentSession?.finder?.clear()
        _result.value = FindResult()
    }
}
