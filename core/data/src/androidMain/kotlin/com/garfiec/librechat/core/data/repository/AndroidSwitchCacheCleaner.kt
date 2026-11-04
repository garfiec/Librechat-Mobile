package com.garfiec.librechat.core.data.repository

import android.webkit.CookieManager
import android.webkit.WebStorage
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.network.client.CookieHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android switch-cache clear: WebView storage (localStorage/IndexedDB behind inline artifacts) and
 * the LibreChat auth cookies in the process-global WebView cookie jar. Android cannot enumerate the
 * jar, so the auth cookies are expired by name at each of [serverUrls] — the outgoing and incoming
 * (or, on a remove-last teardown, every roster) server. Wiping the jar whole would also kill the
 * identity provider's session cookie, the only thing that lets an expired-session re-sign-in
 * complete without the password; whole-jar wipes are reserved for the add-account sign-in WebView.
 * Best-effort: a clear failure must never abort the switch itself.
 */
class AndroidSwitchCacheCleaner : SwitchCacheCleaner {
    override suspend fun clearOnSwitch(serverUrls: Set<String>) {
        runCatching {
            withContext(Dispatchers.Main) {
                WebStorage.getInstance().deleteAllData()
                val cookies = CookieManager.getInstance()
                for (serverUrl in serverUrls) {
                    CookieHelper.authCookieNames.forEach { name ->
                        cookies.setCookie(serverUrl, CookieHelper.expiredAuthCookie(serverUrl, name))
                    }
                }
                cookies.flush()
            }
        }.onFailure { Logger.w(it) { "Switch cache clear failed" } }
    }
}
