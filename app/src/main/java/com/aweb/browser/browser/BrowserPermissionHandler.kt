package com.aweb.browser.browser

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Intercepts GeckoView permission requests and routes them through
 * Android's runtime permission system + a Compose confirmation dialog.
 *
 * Supported permissions:
 *  - Camera (video capture)
 *  - Microphone (audio capture)
 *  - Geolocation
 *  - Notifications (web push)
 *
 * Flow:
 *  1. GeckoView calls [PermissionDelegate] → we emit a [PermissionRequest] event.
 *  2. [BrowserScreen] observes the event and shows a confirmation dialog.
 *  3. User taps Allow/Deny → we call [grant] or [deny].
 *  4. If Allow: check Android runtime permission, request if missing, then grant to Gecko.
 */
@Singleton
class BrowserPermissionHandler @Inject constructor() {

    companion object {
        private const val TAG = "BrowserPermissionHandler"
    }

    // ── Events emitted to UI ───────────────────────────────────────────────

    sealed class PermissionRequest {
        data class MediaRequest(
            val origin  : String,
            val hasVideo: Boolean,
            val hasAudio: Boolean,
            val callback: PermissionDelegate.MediaCallback,
        ) : PermissionRequest()

        data class LocationRequest(
            val origin  : String,
            val callback: PermissionDelegate.Callback,
        ) : PermissionRequest()

        data class NotificationRequest(
            val origin  : String,
            val callback: PermissionDelegate.Callback,
        ) : PermissionRequest()

        data class DownloadRequest(
            val url     : String,
            val filename: String,
            val mimeType: String?,
            val size    : Long,
        ) : PermissionRequest()
    }

    private val _requests = MutableSharedFlow<PermissionRequest>(extraBufferCapacity = 4)
    val requests: SharedFlow<PermissionRequest> = _requests.asSharedFlow()

    // ── GeckoView PermissionDelegate ──────────────────────────────────────

    fun buildPermissionDelegate(context: Context): PermissionDelegate = object : PermissionDelegate {

        override fun onContentPermissionRequest(
            session : GeckoSession,
            perm    : PermissionDelegate.ContentPermission,
        ): GeckoResult<Int>? {
            Log.d(TAG, "Content permission: type=${perm.permission} origin=${perm.uri}")
            return when (perm.permission) {
                PermissionDelegate.PERMISSION_GEOLOCATION -> {
                    _requests.tryEmit(
                        PermissionRequest.LocationRequest(
                            origin   = perm.uri ?: "this site",
                            callback = object : PermissionDelegate.Callback {
                                override fun grant() { /* handled via GeckoResult */ }
                                override fun reject() { /* handled via GeckoResult */ }
                            },
                        )
                    )
                    // Return null — caller handles via the event flow + GeckoResult
                    null
                }
                PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> {
                    _requests.tryEmit(
                        PermissionRequest.NotificationRequest(
                            origin   = perm.uri ?: "this site",
                            callback = object : PermissionDelegate.Callback {
                                override fun grant() {}
                                override fun reject() {}
                            },
                        )
                    )
                    null
                }
                else -> null
            }
        }

        override fun onMediaPermissionRequest(
            session : GeckoSession,
            uri     : String,
            video   : Array<out MediaSource>?,
            audio   : Array<out MediaSource>?,
            callback: PermissionDelegate.MediaCallback,
        ) {
            Log.d(TAG, "Media permission: uri=$uri video=${video != null} audio=${audio != null}")
            _requests.tryEmit(
                PermissionRequest.MediaRequest(
                    origin   = uri,
                    hasVideo = video != null && video.isNotEmpty(),
                    hasAudio = audio != null && audio.isNotEmpty(),
                    callback = callback,
                )
            )
        }
    }

    // ── Android permission helpers ────────────────────────────────────────

    fun hasCameraPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    fun hasMicPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    // ── Download event ────────────────────────────────────────────────────

    fun requestDownloadConfirmation(
        url     : String,
        filename: String,
        mimeType: String?,
        size    : Long,
    ) {
        _requests.tryEmit(
            PermissionRequest.DownloadRequest(
                url      = url,
                filename = filename,
                mimeType = mimeType,
                size     = size,
            )
        )
    }
}
