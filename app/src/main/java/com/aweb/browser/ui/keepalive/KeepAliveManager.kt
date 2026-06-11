package com.aweb.browser.ui.keepalive

import android.util.Log
import com.aweb.browser.data.entity.TabEntity
import com.aweb.browser.data.repository.TabRepository
import com.aweb.browser.gecko.TabSessionManager
import com.aweb.browser.lifecycle.TabLifecycleManager
import com.aweb.browser.lifecycle.TabLifecycleState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the Keep Alive tab feature end-to-end.
 *
 * Responsibilities:
 *  1. Toggle keep_alive flag in Room via [TabRepository].
 *  2. Enforce the max-keep-alive cap from [TabLifecycleManager.policy].
 *  3. Emit warning events when the user tries to exceed the cap.
 *  4. After a toggle, immediately instruct [TabLifecycleManager] to rebalance
 *     so the session priority is re-evaluated without waiting for the next
 *     tab switch.
 *  5. Expose [keepAliveWarning] as a StateFlow so the UI can show a
 *     snackbar / dialog without coupling the ViewModel to Compose directly.
 *
 * Keep Alive rules (mirrors plan Section 9):
 *  - Active tab is always highest priority.
 *  - Keep Alive tabs are next.
 *  - Pinned tabs resist eviction more than plain recent tabs.
 *  - Recent tabs kept only up to policy.maxRecentLive.
 *  - If Android kills the app, Keep Alive sessions are restored on next start
 *    (handled by TabLifecycleManager.onAppRestore).
 */
@Singleton
class KeepAliveManager @Inject constructor(
    private val tabRepo         : TabRepository,
    private val sessionManager  : TabSessionManager,
    private val lifecycleManager: TabLifecycleManager,
) {
    companion object {
        private const val TAG = "KeepAliveManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Warning events ────────────────────────────────────────────────────

    sealed class KeepAliveEvent {
        /** User tried to enable Keep Alive but the cap is reached. */
        data class CapExceeded(val cap: Int) : KeepAliveEvent()
        /** Keep Alive was successfully enabled. */
        data class Enabled(val tabTitle: String) : KeepAliveEvent()
        /** Keep Alive was successfully disabled. */
        data class Disabled(val tabTitle: String) : KeepAliveEvent()
    }

    private val _event = MutableStateFlow<KeepAliveEvent?>(null)
    val event: StateFlow<KeepAliveEvent?> = _event.asStateFlow()

    fun clearEvent() { _event.value = null }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Toggle Keep Alive for [tab] within [allTabs].
     *
     * If enabling: check cap first, emit [KeepAliveEvent.CapExceeded] if over.
     * If disabling: remove flag, let rebalance decide fate of the session.
     */
    fun toggle(
        tab     : TabEntity,
        allTabs : List<TabEntity>,
        workspace: com.aweb.browser.data.entity.WorkspaceEntity,
    ) {
        scope.launch {
            if (!tab.keepAlive) {
                // ── Enabling ──────────────────────────────────────────────
                val currentKeepAliveCount = allTabs.count { it.keepAlive }
                val cap = lifecycleManager.policy.maxKeepAlive

                if (currentKeepAliveCount >= cap) {
                    Log.w(TAG, "Keep Alive cap ($cap) reached — rejecting for '${tab.title}'")
                    _event.value = KeepAliveEvent.CapExceeded(cap)
                    return@launch
                }

                tabRepo.setKeepAlive(tab.id, true)
                Log.i(TAG, "Keep Alive ENABLED for '${tab.title}'")

                // Ensure the session is live immediately (don't wait for next tab switch)
                sessionManager.getOrCreate(tab, workspace)

                // Update lifecycle state in Room
                tabRepo.updateLifecycleState(tab.id, TabLifecycleState.KEEP_ALIVE.dbKey)

                // Rebalance so memory policy is re-enforced with the new keep-alive
                val updatedTabs = allTabs.map { if (it.id == tab.id) it.copy(keepAlive = true) else it }
                val activeTab   = updatedTabs.firstOrNull { it.isActive } ?: return@launch
                lifecycleManager.onTabSelected(
                    newActiveTab      = activeTab,
                    previousActiveTab = null,
                    allTabs           = updatedTabs,
                    workspace         = workspace,
                )

                _event.value = KeepAliveEvent.Enabled(tab.title)

            } else {
                // ── Disabling ─────────────────────────────────────────────
                tabRepo.setKeepAlive(tab.id, false)
                Log.i(TAG, "Keep Alive DISABLED for '${tab.title}'")

                // Update lifecycle state — it will now be subject to normal LRU
                tabRepo.updateLifecycleState(tab.id, TabLifecycleState.RECENT.dbKey)

                // Rebalance — may unload this tab if beyond recent limit
                val updatedTabs = allTabs.map { if (it.id == tab.id) it.copy(keepAlive = false) else it }
                val activeTab   = updatedTabs.firstOrNull { it.isActive } ?: return@launch
                lifecycleManager.onTabSelected(
                    newActiveTab      = activeTab,
                    previousActiveTab = null,
                    allTabs           = updatedTabs,
                    workspace         = workspace,
                )

                _event.value = KeepAliveEvent.Disabled(tab.title)
            }
        }
    }

    /**
     * Returns a summary of all currently keep-alive tabs across all tabs.
     * Used by the Keep Alive overview panel.
     */
    fun getKeepAliveTabs(allTabs: List<TabEntity>): List<TabEntity> =
        allTabs.filter { it.keepAlive }.sortedByDescending { it.lastAccessed }

    /**
     * Validates and enforces the Keep Alive cap after a settings change
     * (e.g. user reduced maxKeepAliveTabs from 3 → 1).
     *
     * Tabs beyond the new cap are un-marked oldest-first and their sessions
     * are left to the next rebalance cycle.
     */
    fun enforceCap(allTabs: List<TabEntity>, newCap: Int) {
        scope.launch {
            val keepAliveTabs = allTabs
                .filter { it.keepAlive }
                .sortedBy { it.lastAccessed }   // oldest first to remove
            val excess = keepAliveTabs.drop(newCap)
            excess.forEach { tab ->
                tabRepo.setKeepAlive(tab.id, false)
                tabRepo.updateLifecycleState(tab.id, TabLifecycleState.RECENT.dbKey)
                Log.i(TAG, "Cap enforcement: removed Keep Alive from '${tab.title}'")
            }
        }
    }
}
