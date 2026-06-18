package com.aweb.browser.browser

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import com.aweb.browser.security.PrivacySanitizer
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.MediaSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Intercepts GeckoView permission requests and routes them to the UI.
 * Compiled against GeckoView nightly-omni 132.0.20240929094629.
 */
@Singleton
class BrowserPermissionHandler @Inject constructor() {

    companion object {
        private const val TAG = "BrowserPermissionHandler"
    }

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

    fun buildPermissionDelegate(context: Context): PermissionDelegate = object : PermissionDelegate {

        override fun onContentPermissionRequest(
            session : GeckoSession,
            perm    : PermissionDelegate.ContentPermission,
        ): GeckoResult<Int>? {
            Log.d(TAG, "Content permission: type=${perm.permission} origin=${PrivacySanitizer.redactUrl(perm.uri)}")
            return when (perm.permission) {
                PermissionDelegate.PERMISSION_GEOLOCATION -> {
                    // Return a GeckoResult that the UI resolves when the user taps Allow/Deny.
                    val result = GeckoResult<Int>()
                    val emitted = _requests.tryEmit(
                        PermissionRequest.LocationRequest(
                            origin   = perm.uri ?: "this site",
                            callback = object : PermissionDelegate.Callback {
                                override fun grant()  { result.complete(PermissionDelegate.ContentPermission.VALUE_ALLOW) }
                                override fun reject() { result.complete(PermissionDelegate.ContentPermission.VALUE_DENY) }
                            },
                        )
                    )
                    if (!emitted) result.complete(PermissionDelegate.ContentPermission.VALUE_DENY)
                    result
                }
                PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> {
                    val result = GeckoResult<Int>()
                    val emitted = _requests.tryEmit(
                        PermissionRequest.NotificationRequest(
                            origin   = perm.uri ?: "this site",
                            callback = object : PermissionDelegate.Callback {
                                override fun grant()  { result.complete(PermissionDelegate.ContentPermission.VALUE_ALLOW) }
                                override fun reject() { result.complete(PermissionDelegate.ContentPermission.VALUE_DENY) }
                            },
                        )
                    )
                    if (!emitted) result.complete(PermissionDelegate.ContentPermission.VALUE_DENY)
                    result
                }
                else -> null
            }
        }

        override fun onMediaPermissionRequest(
            session  : GeckoSession,
            uri      : String,
            video    : Array<out MediaSource>?,
            audio    : Array<out MediaSource>?,
            callback : PermissionDelegate.MediaCallback,
        ) {
            Log.d(TAG, "Media permission: uri=${PrivacySanitizer.redactUrl(uri)} video=${video != null} audio=${audio != null}")
            val emitted = _requests.tryEmit(
                PermissionRequest.MediaRequest(
                    origin   = uri,
                    hasVideo = video != null && video.isNotEmpty(),
                    hasAudio = audio != null && audio.isNotEmpty(),
                    callback = callback,
                )
            )
            if (!emitted) callback.reject()
        }
    }

    fun hasCameraPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    fun hasMicPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun requestDownloadConfirmation(url: String, filename: String, mimeType: String?, size: Long): Boolean =
        _requests.tryEmit(
            PermissionRequest.DownloadRequest(
                url = url, filename = filename, mimeType = mimeType, size = size
            )
        )
}
