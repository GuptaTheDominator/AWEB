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
        val now = System.currentTimeMillis()
        val entity = BookmarkEntity(
            id        = UUID.randomUUID().toString(),
            url       = url,
            title     = title.ifBlank { url },
            createdAt = now,
        )
        dao.insert(entity)
        return entity
    }

    suspend fun remove(id: String) = dao.deleteById(id)

    suspend fun isBookmarked(url: String): Boolean = dao.existsByUrl(url)

    suspend fun getAll(): List<BookmarkEntity> = dao.getAll()
}
