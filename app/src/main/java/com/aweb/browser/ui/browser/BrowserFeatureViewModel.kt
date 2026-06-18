package com.aweb.browser.ui.browser

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aweb.browser.browser.*
import com.aweb.browser.data.entity.BookmarkEntity
import com.aweb.browser.data.repository.BookmarkRepository
import com.aweb.browser.gecko.GeckoSessionWrapper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel driving all Phase 8 browser features:
 *  - Bookmarks (add, remove, observe, isBookmarked)
 *  - Find-in-page (show, hide, find, clear)
 *  - Desktop/Mobile UA toggle
 *  - Fullscreen management
 *  - Permission / download request event relay
 *
 * Lives at the same scope as [TabViewModel] — both are hiltViewModel() in BrowserScreen.
 */
@HiltViewModel
class BrowserFeatureViewModel @Inject constructor(
    @ApplicationContext private val context : Context,
    private val bookmarkRepo    : BookmarkRepository,
    private val findHandler     : FindInPageHandler,
    private val uaManager       : UserAgentManager,
    private val fullscreenHandler: FullscreenHandler,
    private val permHandler     : BrowserPermissionHandler,
    private val downloadHandler : DownloadHandler,
    private val fileUploadHandler: FileUploadHandler,
) : ViewModel() {

    // ── Bookmarks ─────────────────────────────────────────────────────────

    val bookmarks: StateFlow<List<BookmarkEntity>> =
        bookmarkRepo.bookmarks.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isBookmarked = MutableStateFlow(false)
    val isBookmarked: StateFlow<Boolean> = _isBookmarked.asStateFlow()

    fun checkBookmark(url: String) {
        viewModelScope.launch { _isBookmarked.value = bookmarkRepo.isBookmarked(url) }
    }

    fun toggleBookmark(url: String, title: String) {
        viewModelScope.launch {
            if (_isBookmarked.value) {
                bookmarkRepo.getAll().firstOrNull { it.url == url }?.let {
                    bookmarkRepo.remove(it.id)
                }
                _isBookmarked.value = false
            } else {
                bookmarkRepo.add(url, title)
                _isBookmarked.value = true
            }
        }
    }

    fun deleteBookmark(bm: BookmarkEntity) {
        viewModelScope.launch { bookmarkRepo.remove(bm.id) }
    }

    // ── Find in page ──────────────────────────────────────────────────────

    val findVisible: StateFlow<Boolean>                  = findHandler.isVisible
    val findResult : StateFlow<FindInPageHandler.FindResult> = findHandler.result

    fun showFind()          = findHandler.show()
    fun hideFind()          = findHandler.hide()
    fun find(q: String, fwd: Boolean = true) = findHandler.find(q, fwd)
    fun attachFindSession(session: org.mozilla.geckoview.GeckoSession) =
        findHandler.attachSession(session)

    // ── UA mode ───────────────────────────────────────────────────────────

    fun syncUaModeForSession(session: org.mozilla.geckoview.GeckoSession?) {
        // No-op, managed by TabViewModel + Room now
    }

    // ── Fullscreen ────────────────────────────────────────────────────────

    val isFullscreen: StateFlow<Boolean> = fullscreenHandler.isFullscreen

    fun enterFullscreen(activity: Activity) = fullscreenHandler.enterFullscreen(activity)
    fun exitFullscreen(activity: Activity)  = fullscreenHandler.exitFullscreen(activity)

    // ── Permission requests ───────────────────────────────────────────────

    val permissionRequests: SharedFlow<BrowserPermissionHandler.PermissionRequest> =
        permHandler.requests

    fun grantMedia(
        req       : BrowserPermissionHandler.PermissionRequest.MediaRequest,
        camGranted: Boolean,
        micGranted: Boolean,
    ) {
        // GeckoView 132: MediaCallback.grant(videoDeviceId: String?, audioDeviceId: String?)
        val missingVideo = req.hasVideo && !camGranted
        val missingAudio = req.hasAudio && !micGranted
        if (missingVideo || missingAudio) {
            req.callback.reject()
            return
        }
        req.callback.grant(
            if (req.hasVideo) "" else null,   // empty string = first available device
            if (req.hasAudio) "" else null,
        )
    }

    fun denyMedia(req: BrowserPermissionHandler.PermissionRequest.MediaRequest) {
        req.callback.reject()
    }

    // ── Downloads ─────────────────────────────────────────────────────────

    fun confirmDownload(req: BrowserPermissionHandler.PermissionRequest.DownloadRequest) {
        downloadHandler.enqueueDownload(
            context  = context,
            url      = req.url,
            filename = req.filename,
            mimeType = req.mimeType,
            size     = req.size,
        )
    }

    // ── File upload ───────────────────────────────────────────────────────

    val filePickRequests: SharedFlow<FileUploadHandler.PickRequest> =
        fileUploadHandler.pickRequest

    fun onFilesSelected(uris: List<android.net.Uri>?) =
        fileUploadHandler.onFilesSelected(uris)

    // ── Panel visibility state ─────────────────────────────────────────────

    private val _showBookmarks = MutableStateFlow(false)
    val showBookmarks: StateFlow<Boolean> = _showBookmarks.asStateFlow()

    fun openBookmarks()  { _showBookmarks.value = true }
    fun closeBookmarks() { _showBookmarks.value = false }
}
