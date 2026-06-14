package com.aweb.browser.browser

import android.util.Log
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

    companion object { private const val TAG = "FindInPageHandler" }

    data class FindResult(
        val current: Int     = 0,
        val total  : Int     = 0,
        val found  : Boolean = false,
    )

    private val _isVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    private val _result = MutableStateFlow(FindResult())
    val result: StateFlow<FindResult> = _result.asStateFlow()

    @Volatile private var currentSession: GeckoSession? = null

    fun attachSession(session: GeckoSession) { currentSession = session }

    fun show() { _isVisible.value = true }

    fun hide() {
        _isVisible.value = false
        clear()
    }

    /**
     * FIX (Bug 6): The previous .then { } block returned GeckoResult.fromValue<Void>(null)
     * which is incompatible with the GeckoResult<GeckoSession.FinderResult> chain in
     * GeckoView 132 and caused a ClassCastException at runtime.
     *
     * Correct pattern: use .then { result -> GeckoResult.fromValue(result) } only if
     * chaining is needed, or just use .accept { } (fire-and-forget) which is the right
     * API for updating UI state from a GeckoResult without returning another result.
     */
    fun find(query: String, forward: Boolean = true) {
        if (query.isBlank()) { clear(); return }
        val sess = currentSession ?: return
        val flags = if (forward) 0 else GeckoSession.FINDER_FIND_BACKWARDS
        try {
            sess.finder.find(query, flags)?.accept { finderResult ->
                if (finderResult != null) {
                    _result.value = FindResult(
                        current = finderResult.current,
                        total   = finderResult.total,
                        found   = finderResult.found,
                    )
                } else {
                    _result.value = FindResult(found = false)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "find($query): ${e.message}")
        }
    }

    fun clear() {
        try { currentSession?.finder?.clear() } catch (_: Exception) {}
        _result.value = FindResult()
    }
}
