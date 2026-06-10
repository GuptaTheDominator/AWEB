package com.aweb.browser.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Key/value store for app settings.
 *
 * Keys are defined as constants in [SettingsKeys].
 */
@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey
    @ColumnInfo(name = "key")
    val key: String,

    @ColumnInfo(name = "value")
    val value: String,
)

/**
 * Well-known setting key constants.
 *
 * Values are always stored as Strings; parse at read time.
 */
object SettingsKeys {
    const val MEMORY_MODE             = "memory_mode"           // conservative|balanced|performance
    const val MAX_RECENT_LIVE_TABS    = "max_recent_live_tabs"  // Int as string
    const val MAX_KEEP_ALIVE_TABS     = "max_keep_alive_tabs"   // Int as string
    const val DEFAULT_HOMEPAGE        = "default_homepage"
    const val DEFAULT_SEARCH_ENGINE   = "default_search_engine" // ddg|google|bing
    const val KEEP_SCREEN_AWAKE       = "keep_screen_awake"     // true|false
    const val STARTUP_WORKSPACE       = "startup_workspace"     // workspace UUID or "last"
}
