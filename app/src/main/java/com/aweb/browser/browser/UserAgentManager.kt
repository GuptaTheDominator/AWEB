package com.aweb.browser.browser

import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Desktop / Mobile user-agent mode switching per tab.
 *
 * GeckoView's [GeckoSessionSettings.userAgentMode] controls this natively.
 * We track the current mode per session so [BrowserScreen] can show the
 * correct toggle icon and label.
 *
 * Toggling UA mode automatically reloads the page (called from [BrowserScreen]).
 */
@Singleton
class UserAgentManager @Inject constructor() {

    enum class UaMode { MOBILE, DESKTOP }

    // session ID → current mode  (ConcurrentHashMap for thread-safe access)
    private val modes = java.util.concurrent.ConcurrentHashMap<String, UaMode>()

    fun getMode(sessionId: String): UaMode =
        modes[sessionId] ?: UaMode.MOBILE

    fun toggle(session: GeckoSession): UaMode {
        val id      = session.hashCode().toString()
        val current = getMode(id)
        val next    = if (current == UaMode.MOBILE) UaMode.DESKTOP else UaMode.MOBILE

        session.settings.userAgentMode = when (next) {
            UaMode.DESKTOP -> GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
            UaMode.MOBILE  -> GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        }
        session.reload()

        modes[id] = next
        return next
    }

    fun remove(sessionId: String) = modes.remove(sessionId)
}
