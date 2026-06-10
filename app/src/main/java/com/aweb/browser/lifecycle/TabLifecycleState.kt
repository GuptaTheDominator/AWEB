package com.aweb.browser.lifecycle

/**
 * The four possible states a tab session can be in at runtime.
 *
 * These mirror the plan (Section 6) and the DB values in [TabEntity.lastLifecycleState].
 *
 *  ACTIVE     — currently visible tab. One per app at a time.
 *  RECENT     — recently visited, session kept alive but paused (setActive false).
 *               Count limited by MemoryMode.maxRecentLive.
 *  KEEP_ALIVE — user-flagged. Session kept alive at high priority.
 *               Count limited by Settings.maxKeepAliveTabs.
 *  UNLOADED   — no live GeckoSession. Tab exists only in Room.
 *               Reloads from stored URL when selected.
 */
enum class TabLifecycleState(val dbKey: String) {
    ACTIVE    ("active"),
    RECENT    ("recent"),
    KEEP_ALIVE("keep_alive"),
    UNLOADED  ("unloaded");

    companion object {
        fun fromDb(key: String) = entries.firstOrNull { it.dbKey == key } ?: UNLOADED
    }
}
