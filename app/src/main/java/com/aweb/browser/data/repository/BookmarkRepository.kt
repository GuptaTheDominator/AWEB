package com.aweb.browser.data.repository

import com.aweb.browser.data.db.BookmarkDao
import com.aweb.browser.data.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simple bookmarks store.
 *
 * Bookmarks are global across workspaces (not workspace-isolated) by design —
 * the same bookmark URL may be opened in any workspace the user chooses.
 *
 * If workspace-specific bookmarks are ever needed, add a nullable workspaceId
 * column to [BookmarkEntity] and filter accordingly.
 */
@Singleton
class BookmarkRepository @Inject constructor(
    private val dao: BookmarkDao,
) {
    val bookmarks: Flow<List<BookmarkEntity>> = dao.observeAll()

    suspend fun add(url: String, title: String): BookmarkEntity {
        val normalizedUrl = url.trim()
        require(normalizedUrl.isNotBlank()) { "Bookmark URL cannot be blank" }

        val now = System.currentTimeMillis()
        val existing = dao.getByUrl(normalizedUrl)
        val entity = BookmarkEntity(
            id        = existing?.id ?: UUID.randomUUID().toString(),
            url       = normalizedUrl,
            title     = title.ifBlank { normalizedUrl },
            createdAt = existing?.createdAt ?: now,
        )
        dao.insert(entity)
        return entity
    }

    suspend fun remove(id: String) = dao.deleteById(id)

    suspend fun removeByUrl(url: String) = dao.deleteByUrl(url.trim())

    suspend fun isBookmarked(url: String): Boolean = dao.existsByUrl(url.trim())

    suspend fun getAll(): List<BookmarkEntity> = dao.getAll()
}
