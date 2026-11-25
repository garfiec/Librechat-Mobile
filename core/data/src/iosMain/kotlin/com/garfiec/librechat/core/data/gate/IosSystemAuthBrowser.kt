package com.garfiec.librechat.core.data.gate

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject

/**
 * The gate's sign-in in an `ASWebAuthenticationSession`. The response arrives at the loopback
 * listener, not at a URL scheme the session watches, so the session never completes on its own:
 * [close] cancels it once the code is in. A user cancel ends [open] the same way.
 */
internal class IosSystemAuthBrowser : SystemAuthBrowser {
    private var session: ASWebAuthenticationSession? = null
    private var done: CompletableDeferred<Unit>? = null

    /** Held strongly: the session keeps only a weak reference to its context provider. */
    private val anchor = object : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
        override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor =
            keyWindow() ?: UIWindow()
    }

    override suspend fun open(url: String) {
        val signal = CompletableDeferred<Unit>()
        withContext(Dispatchers.Main) {
            val target = checkNotNull(NSURL.URLWithString(url)) { "bad authorization URL" }
            val started = ASWebAuthenticationSession(
                uRL = target,
                callbackURLScheme = null,
                completionHandler = { _, _ -> signal.complete(Unit) },
            )
            // Shares Safari's cookies, so a gate session the user already has signs them straight in.
            started.prefersEphemeralWebBrowserSession = false
            started.presentationContextProvider = anchor
            session = started
            done = signal
            if (!started.start()) signal.complete(Unit)
        }
        try {
            signal.await()
        } finally {
            withContext(NonCancellable + Dispatchers.Main) {
                if (done === signal) {
                    session = null
                    done = null
                }
            }
        }
    }

    override fun close() {
        // ASWebAuthenticationSession must be driven from the main thread. [open] is released here, not
        // left to the completion handler, which a programmatic cancel() is not documented to call.
        platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) {
            session?.cancel()
            done?.complete(Unit)
        }
    }

    private fun keyWindow(): UIWindow? =
        UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
            .firstOrNull { it.isKeyWindow() }
}
