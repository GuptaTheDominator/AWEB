@file:Suppress("DEPRECATION")
package com.aweb.browser.lifecycle


import android.util.Log
import com.aweb.browser.data.entity.TabEntity
import com.aweb.browser.data.entity.WorkspaceEntity
import com.aweb.browser.data.repository.TabRepository
import com.aweb.browser.gecko.GeckoSessionWrapper
import com.aweb.browser.gecko.TabSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The automatic tab lifecycle brain for AWEB.
 *
 * Responsibilities:
 *  1. On every tab switch — promote new active tab, demote old one.
 *  2. Enforce [MemoryPolicy] — unload tabs beyond the live limits.
 *  3. React to Android memory pressure callbacks (onTrimMemory).
 *  4. Persist lifecycle state to Room after every transition.
 *
 * Priority ordering (highest first):
 *  1. ACTIVE tab           — always live
 *  2. KEEP_ALIVE tabs      — live up to policy.maxKeepAlive
 *  3. PINNED tabs          — treated as recent with higher eviction resistance
 *  4. RECENT tabs          — live up to policy.maxRecentLive, LRU order
 *  5. Everything else      — UNLOADED
 *
 * This class is the ONLY place that calls [TabSessionManager.unload].
 * The rest of the app calls [onTabSelected] / [onTabClosed] / [onMemoryPressure]
 * and lets this manager decide what to keep alive.
 */
@Singleton
class TabLifecycleManager @Inject constructor(
    private val tabRepo       : TabRepository,
    private val sessionManager: TabSessionManager,
) {
    companion object {
        private const val TAG = "TabLifecycleManager"
    }

    private val scope  = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val restoredWorkspaceIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    // Current policy — updated from Settings
    @Volatile var policy: MemoryPolicy = MemoryPolicy.BALANCED
        private set

    fun applyMemoryMode(key: String, maxKeepAlive: Int) {
        applyMemoryMode(key, maxRecentLive = null, maxKeepAlive = maxKeepAlive)
    }

    fun applyMemoryMode(key: String, maxRecentLive: Int?, maxKeepAlive: Int) {
        policy = MemoryPolicy.fromKey(
            key = key,
            maxKeepAlive = maxKeepAlive,
            maxRecentLive = maxRecentLive,
        )
        Log.i(TAG, "Memory policy updated: $policy")
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Called whenever the user selects a tab (or a new tab is opened).
     *
     * Steps:
     *  1. Promote [newActiveTab] to ACTIVE.
     *  2. Demote [previousActiveTab] to RECENT (keep session open).
     *  3. Run [rebalance] to enforce memory limits across [allTabs].
     */
    fun onTabSelected(
        newActiveTab      : TabEntity,
        previousActiveTab : TabEntity?,
        allTabs           : List<TabEntity>,
        workspace         : WorkspaceEntity,
    ) {
        scope.launch {
            Log.d(TAG, "Tab selected: '${newActiveTab.title}'")

            // 1. Promote new active — ensure session is live and Gecko-active
            sessionManager.getOrCreate(newActiveTab, workspace)
            sessionManager.setActive(newActiveTab.id, true)
            persistState(newActiveTab.id, TabLifecycleState.ACTIVE)

            // 2. Demote previous active → RECENT (session stays open, just paused)
            if (previousActiveTab != null && previousActiveTab.id != newActiveTab.id) {
                sessionManager.setActive(previousActiveTab.id, false)
                persistState(previousActiveTab.id, TabLifecycleState.RECENT)
            }

            // 3. Rebalance all other tabs
            rebalance(
                activeTabId = newActiveTab.id,
                allTabs     = allTabs,
                workspace   = workspace,
            )
        }
    }

    /**
     * Called when a tab is closed. Frees its session and rebalances.
     */
    fun onTabClosed(
        closedTab : TabEntity,
        allTabs   : List<TabEntity>,
        workspace : WorkspaceEntity,
    ) {
        scope.launch {
            Log.d(TAG, "Tab closed: '${closedTab.title}'")
            sessionManager.unload(closedTab.id)
            val remaining = allTabs.filter { it.id != closedTab.id }
            val newActive = remaining.firstOrNull { it.isActive } ?: remaining.firstOrNull()
            if (newActive != null) {
                rebalance(activeTabId = newActive.id, allTabs = remaining, workspace = workspace)
            }
        }
    }

    /**
     * Called from [Application.onTrimMemory] with Android's memory level.
     * Levels map to aggressiveness of unloading:
     *
     *  TRIM_MEMORY_UI_HIDDEN       → mild:   unload oldest recent tabs beyond policy
     *  TRIM_MEMORY_RUNNING_LOW     → medium: unload all recent tabs
     *  TRIM_MEMORY_RUNNING_CRITICAL→ high:   unload all except active + keep_alive
     *  TRIM_MEMORY_COMPLETE        → severe: unload everything except active
     */
    fun onMemoryPressure(level: Int, allTabs: List<TabEntity>, workspace: WorkspaceEntity) {
        scope.launch {
            Log.w(TAG, "Memory pressure level $level — trimming sessions")

            val activeTab = allTabs.firstOrNull { it.isActive } ?: allTabs.maxByOrNull { it.lastAccessed }

            val tabsToUnload: List<TabEntity> = when {
                level >= android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                    // Unload everything except the active tab (Bug 8: fallback to last accessed)
                    Log.w(TAG, "SEVERE: unloading all except active tab")
                    allTabs.filter {
                        it.id != activeTab?.id
                    }
                }
                level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                    // Unload all except active + keep_alive
                    Log.w(TAG, "CRITICAL: unloading all except active + keep_alive")
                    allTabs.filter {
                        it.id != activeTab?.id && !it.keepAlive
                    }
                }
                level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                    // Unload all RECENT sessions
                    Log.w(TAG, "LOW: unloading all recent sessions")
                    allTabs.filter {
                        it.lastLifecycleState == TabLifecycleState.RECENT.dbKey
                    }
                }
                else -> {
                    // Mild — just trim one oldest recent tab
                    allTabs
                        .filter { it.lastLifecycleState == TabLifecycleState.RECENT.dbKey }
                        .sortedBy { it.lastAccessed }
                        .take(1)
                }
            }

            tabsToUnload.forEach { tab ->
                if (sessionManager.get(tab.id) != null) {
                    sessionManager.unload(tab.id)
                    persistState(tab.id, TabLifecycleState.UNLOADED)
                    Log.d(TAG, "Unloaded tab '${tab.title}' due to memory pressure")
                }
            }
        }
    }

    /**
     * Restores tabs after app restart.
     *
     * Strategy:
     *  1. Create live session for the active tab only.
     *  2. Create live sessions for keep_alive tabs (up to policy.maxKeepAlive).
     *  3. Leave all other tabs unloaded — they reload lazily when selected.
     */
    fun onAppRestore(allTabs: List<TabEntity>, workspace: WorkspaceEntity) {
        if (!restoredWorkspaceIds.add(workspace.id)) return
        scope.launch {
            Log.i(TAG, "Restoring ${allTabs.size} tabs for workspace '${workspace.name}'")

            val activeTab = allTabs.firstOrNull { it.isActive }
            val keepAliveTabs = allTabs
                .filter { it.keepAlive && it.id != activeTab?.id }
                .take(policy.maxKeepAlive)

            activeTab?.let {
                sessionManager.getOrCreate(it, workspace)
                sessionManager.setActive(it.id, true)
                persistState(it.id, TabLifecycleState.ACTIVE)
                Log.d(TAG, "Restored active tab: '${it.title}'")
            }

            keepAliveTabs.forEach { tab ->
                sessionManager.getOrCreate(tab, workspace)
                persistState(tab.id, TabLifecycleState.KEEP_ALIVE)
                Log.d(TAG, "Restored keep_alive tab: '${tab.title}'")
            }

            // Mark everything else as unloaded in DB
            allTabs
                .filter { it.id != activeTab?.id && !keepAliveTabs.any { ka -> ka.id == it.id } }
                .forEach { tab -> persistState(tab.id, TabLifecycleState.UNLOADED) }
        }
    }

    // ── Core rebalance logic ──────────────────────────────────────────────

    /**
     * Enforces the memory policy across all tabs in a workspace.
     *
     * Called after every tab selection or tab close.
     *
     * Algorithm:
     *  Collect all live sessions.
     *  Separate into: active | keep_alive | pinned | recent | other.
     *  Keep active (always 1).
     *  Keep keep_alive up to policy.maxKeepAlive.
     *  Keep recent (pinned preferred) up to policy.maxRecentLive by lastAccessed desc.
     *  Unload everything else.
     */
    private suspend fun rebalance(
        activeTabId : String,
        allTabs     : List<TabEntity>,
        workspace   : WorkspaceEntity,
    ) {
        val liveSessions = allTabs.filter { sessionManager.get(it.id) != null }

        // Active — always protected
        val active = liveSessions.filter { it.id == activeTabId }

        // Keep alive candidates (user-flagged, not active)
        val keepAlive = liveSessions
            .filter { it.keepAlive && it.id != activeTabId }
            .sortedByDescending { it.lastAccessed }
            .take(policy.maxKeepAlive)

        val keepAliveIds = keepAlive.map { it.id }.toSet()

        // Recent candidates — pinned tabs resist eviction
        val recentCandidates = liveSessions
            .filter { it.id != activeTabId && it.id !in keepAliveIds }
            .sortedWith(
                compareByDescending<TabEntity> { it.isPinned }
                    .thenByDescending { it.lastAccessed }
            )

        val recentToKeep = recentCandidates.take(policy.maxRecentLive)
        val recentToKeepIds = recentToKeep.map { it.id }.toSet()

        // Anything left over gets unloaded
        val toUnload = liveSessions.filter {
            it.id != activeTabId &&
            it.id !in keepAliveIds &&
            it.id !in recentToKeepIds
        }

        // Apply state transitions
        keepAlive.forEach { tab ->
            sessionManager.setActive(tab.id, false)
            persistState(tab.id, TabLifecycleState.KEEP_ALIVE)
        }

        recentToKeep.forEach { tab ->
            sessionManager.setActive(tab.id, false)
            persistState(tab.id, TabLifecycleState.RECENT)
        }

        toUnload.forEach { tab ->
            sessionManager.unload(tab.id)
            persistState(tab.id, TabLifecycleState.UNLOADED)
            Log.d(TAG, "Rebalance: unloaded '${tab.title}'")
        }

        if (toUnload.isNotEmpty()) {
            Log.i(TAG, "Rebalance: kept ${active.size} active, " +
                "${keepAlive.size} keep_alive, ${recentToKeep.size} recent, " +
                "unloaded ${toUnload.size}")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private suspend fun persistState(tabId: String, state: TabLifecycleState) {
        tabRepo.updateLifecycleState(tabId, state.dbKey)
    }
}
