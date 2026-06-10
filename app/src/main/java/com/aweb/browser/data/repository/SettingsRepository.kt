package com.aweb.browser.data.repository

import com.aweb.browser.data.db.AppSettingDao
import com.aweb.browser.data.entity.AppSettingEntity
import com.aweb.browser.data.entity.SettingsKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Typed wrapper around [AppSettingDao].
 *
 * All settings have a sensible default so the app works correctly
 * even before the user visits the Settings screen.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dao: AppSettingDao
) {

    // ── Memory mode ───────────────────────────────────────────────────────

    val memoryMode: Flow<MemoryMode> = dao.observe(SettingsKeys.MEMORY_MODE).map { raw ->
        MemoryMode.fromString(raw) ?: MemoryMode.BALANCED
    }

    suspend fun setMemoryMode(mode: MemoryMode) = set(SettingsKeys.MEMORY_MODE, mode.key)

    // ── Live tab limits ───────────────────────────────────────────────────

    val maxRecentLiveTabs: Flow<Int> = dao.observe(SettingsKeys.MAX_RECENT_LIVE_TABS).map {
        it?.toIntOrNull() ?: 2
    }

    suspend fun setMaxRecentLiveTabs(count: Int) =
        set(SettingsKeys.MAX_RECENT_LIVE_TABS, count.toString())

    val maxKeepAliveTabs: Flow<Int> = dao.observe(SettingsKeys.MAX_KEEP_ALIVE_TABS).map {
        it?.toIntOrNull() ?: 3
    }

    suspend fun setMaxKeepAliveTabs(count: Int) =
        set(SettingsKeys.MAX_KEEP_ALIVE_TABS, count.toString())

    // ── Homepage / search ─────────────────────────────────────────────────

    val defaultHomepage: Flow<String> = dao.observe(SettingsKeys.DEFAULT_HOMEPAGE).map {
        it ?: "https://duckduckgo.com"
    }

    suspend fun setDefaultHomepage(url: String) = set(SettingsKeys.DEFAULT_HOMEPAGE, url)

    val defaultSearchEngine: Flow<SearchEngine> =
        dao.observe(SettingsKeys.DEFAULT_SEARCH_ENGINE).map {
            SearchEngine.fromString(it) ?: SearchEngine.DUCKDUCKGO
        }

    suspend fun setDefaultSearchEngine(engine: SearchEngine) =
        set(SettingsKeys.DEFAULT_SEARCH_ENGINE, engine.key)

    // ── Screen awake ──────────────────────────────────────────────────────

    val keepScreenAwake: Flow<Boolean> = dao.observe(SettingsKeys.KEEP_SCREEN_AWAKE).map {
        it?.toBooleanStrictOrNull() ?: false
    }

    suspend fun setKeepScreenAwake(enabled: Boolean) =
        set(SettingsKeys.KEEP_SCREEN_AWAKE, enabled.toString())

    // ── Internal helpers ──────────────────────────────────────────────────

    private suspend fun set(key: String, value: String) {
        dao.set(AppSettingEntity(key = key, value = value))
    }
}

// ── Enums ─────────────────────────────────────────────────────────────────

enum class MemoryMode(val key: String, val label: String) {
    CONSERVATIVE("conservative", "Conservative"),
    BALANCED("balanced", "Balanced (Recommended)"),
    PERFORMANCE("performance", "Performance");

    companion object {
        fun fromString(s: String?) = values().find { it.key == s }
    }
}

enum class SearchEngine(val key: String, val label: String, val queryUrl: String) {
    DUCKDUCKGO("ddg",    "DuckDuckGo", "https://duckduckgo.com/?q="),
    GOOGLE    ("google", "Google",     "https://www.google.com/search?q="),
    BING      ("bing",   "Bing",       "https://www.bing.com/search?q=");

    fun buildSearchUrl(query: String) = queryUrl + query.trim().replace(" ", "+")

    companion object {
        fun fromString(s: String?) = values().find { it.key == s }
    }
}
