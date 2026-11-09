package com.garfiec.librechat.core.data.repository

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSDate
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPCookieStorage
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.WebKit.WKWebsiteDataStore

/**
 * iOS switch-cache clear: the shared cookie storage (holds the OAuth `refreshToken` cookie the
 * launcher reads back — cleared whole so the outgoing account's cookie can't be re-read under the
 * incoming one) and WKWebView website data (localStorage etc. behind inline artifacts, plus WK's own
 * cookies). WebKit requires main-thread access. Best-effort: a clear failure must never abort the
 * switch itself.
 */
class IosSwitchCacheCleaner : SwitchCacheCleaner {
    override suspend fun clearOnSwitch() {
        runCatching {
            withContext(Dispatchers.Main) {
                val storage = NSHTTPCookieStorage.sharedHTTPCookieStorage
                storage.cookies?.forEach { cookie ->
                    (cookie as? NSHTTPCookie)?.let { storage.deleteCookie(it) }
                }
                clearWebKitData()
            }
        }.onFailure { Logger.w(it) { "Switch cache clear failed" } }
    }

    // The shared cookie storage keeps what the sign-in response just set (gateway, proxy); the SSO
    // WebView's store is non-persistent, so there is no provider session in it to drop.
    override suspend fun clearOnIdentityChange() {
        runCatching { withContext(Dispatchers.Main) { clearWebKitData() } }
            .onFailure { Logger.w(it) { "Identity-change cache clear failed" } }
    }

    private suspend fun clearWebKitData() {
        // Deferred + timeout rather than suspending on the WebKit callback directly: a switch runs
        // this inside the closed SwitchGate under NonCancellable, so a completion handler WebKit never
        // invokes (its data-store process dying mid-operation) would otherwise never resume — the
        // gate never reopens and every request in the app parks forever. complete() is idempotent,
        // so a callback firing after the timeout is harmless.
        val done = CompletableDeferred<Unit>()
        WKWebsiteDataStore.defaultDataStore().removeDataOfTypes(
            dataTypes = WKWebsiteDataStore.allWebsiteDataTypes(),
            modifiedSince = NSDate.dateWithTimeIntervalSince1970(0.0),
        ) { done.complete(Unit) }
        if (withTimeoutOrNull(WEBKIT_CLEAR_TIMEOUT_MS) { done.await() } == null) {
            Logger.w { "Switch cache clear: WebKit data wipe timed out; continuing" }
        }
    }

    private companion object {
        /** Generous for a wipe that normally completes in tens of ms, small enough that a wedged
         *  WebKit can't stall the switch (and all parked requests) noticeably longer. */
        const val WEBKIT_CLEAR_TIMEOUT_MS = 3_000L
    }
}
