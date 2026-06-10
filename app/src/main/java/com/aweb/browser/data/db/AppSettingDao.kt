package com.aweb.browser.data.db

import androidx.room.*
import com.aweb.browser.data.entity.AppSettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSettingDao {

    @Query("SELECT * FROM app_settings WHERE key = :key LIMIT 1")
    suspend fun get(key: String): AppSettingEntity?

    @Query("SELECT value FROM app_settings WHERE key = :key LIMIT 1")
    fun observe(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(setting: AppSettingEntity)

    @Query("DELETE FROM app_settings WHERE key = :key")
    suspend fun delete(key: String)
}
