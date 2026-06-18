package com.aweb.browser.lifecycle

/**
 * Memory mode — controls how many live GeckoSessions AWEB keeps open.
 *
 * Mirrors [com.aweb.browser.data.repository.MemoryMode] but lives in the
 * lifecycle package so [TabLifecycleManager] has no dependency on the
 * data layer's enum.
 *
 * Synced from [SettingsRepository] via [TabLifecycleManager.applyMemoryMode].
 */
data class MemoryPolicy(
    val maxRecentLive   : Int,   // how many non-active, non-keepAlive sessions stay open
    val maxKeepAlive    : Int,   // cap on user-marked Keep Alive tabs
) {
    companion object {
        val CONSERVATIVE = MemoryPolicy(maxRecentLive = 0, maxKeepAlive = 2)
        val BALANCED     = MemoryPolicy(maxRecentLive = 2, maxKeepAlive = 3)
        val PERFORMANCE  = MemoryPolicy(maxRecentLive = 5, maxKeepAlive = 5)

        fun fromKey(
            key: String,
            maxKeepAlive: Int = 3,
            maxRecentLive: Int? = null,
        ): MemoryPolicy {
            val preset = when (key) {
                "conservative" -> CONSERVATIVE
                "performance"  -> PERFORMANCE
                else           -> BALANCED
            }
            return preset.copy(
                maxRecentLive = maxRecentLive ?: preset.maxRecentLive,
                maxKeepAlive = maxKeepAlive,
            )
        }
    }
}
