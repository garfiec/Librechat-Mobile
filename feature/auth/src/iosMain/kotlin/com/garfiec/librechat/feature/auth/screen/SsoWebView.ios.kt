package com.garfiec.librechat.feature.auth.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.garfiec.librechat.feature.auth.oauth.REFRESH_TOKEN_COOKIE
import com.garfiec.librechat.feature.auth.oauth.SsoNavigationDecision
import com.garfiec.librechat.feature.auth.oauth.SsoNavigationGate
import com.garfiec.librechat.feature.auth.oauth.cookieAppliesTo
import com.garfiec.librechat.feature.auth.oauth.extractQueryParameter
import com.garfiec.librechat.feature.auth.oauth.safariApplicationName
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.cValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSError
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.UIKit.UIDevice
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject

/**
 * WKHTTPCookieStore reads cross-process and can lag a Set-Cookie the navigation has already
 * committed. Because the redirect is cancelled rather than raced, there is time to look twice.
 */
private const val COOKIE_READ_ATTEMPTS = 2
private const val COOKIE_RETRY_DELAY_MS = 150L
private const val COOKIE_READ_TIMEOUT_MS = 2_000L

/**
 * `NSURLErrorCancelled`. WebKit reports it whenever a provisional navigation is superseded — a
 * redirect starting the next one, or this delegate's own `stopLoading()` — which is routine in an
 * OAuth chain rather than a failure. Reporting it would abort sign-ins that are going fine.
 *
 * The policy cancel in `decidePolicyForNavigationAction` is a different error (`WebKitErrorDomain`
 * 102) and needs no code of its own: that path latches `finished` before WebKit reports anything.
 */
private const val NSURL_ERROR_CANCELLED = -999L

private class SsoCallbacks(
    val onTokenCapture: (String) -> Unit,
    val onOAuthError: (String?) -> Unit,
    val onCaptureFail: () -> Unit,
)

/**
 * Held by [remember] for the composition's lifetime. `navigationDelegate` is a weak Obj-C property
 * and Kotlin/Native will not retain the object for us, so an anonymous delegate assigned inside
 * `factory` can be collected mid-flow and the sign-in simply stops responding.
 *
 * It reads the callbacks off a [State] rather than capturing them, because these methods run on
 * WebKit's callback and not in composition — captured lambdas would be frozen at first composition.
 */
@OptIn(ExperimentalForeignApi::class)
private class SsoNavigationDelegate(
    serverUrl: String,
    provider: String,
    private val scope: CoroutineScope,
    private val callbacks: State<SsoCallbacks>,
) : NSObject(), WKNavigationDelegateProtocol {

    private val gate = SsoNavigationGate(serverUrl, provider)
    private val serverHost: String = NSURL.URLWithString(serverUrl)?.host.orEmpty()
    private var finished = false

    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val action = decidePolicyForNavigationAction
        val url = action.request.URL?.absoluteString
        if (url == null || action.targetFrame?.mainFrame != true) {
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
            return
        }
        if (gate.decide(url) != SsoNavigationDecision.STOP) {
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
            return
        }
        // Cancelling here means the post-callback page is never fetched, so the web app's
        // silentRefresh() cannot rotate the token out from under us.
        decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
        finish(webView, extractQueryParameter(url, "error"))
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        captureIfTokenPresent(webView)
    }

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didReceiveServerRedirectForProvisionalNavigation: WKNavigation?,
    ) {
        captureIfTokenPresent(webView)
    }

    // A main-frame load that never arrives ends the round-trip too, and nothing else would report
    // it: no navigation follows, so neither the gate nor the cookie probe fires again, and the
    // screen keeps a dead WebKit error page with no spinner and no error pane. finish() still
    // reads the jar first, so a failure landing after the callback set the cookie is a capture.
    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFailProvisionalNavigation: WKNavigation?,
        withError: NSError,
    ) {
        failLoad(webView, withError)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) {
        failLoad(webView, withError)
    }

    private fun failLoad(webView: WKWebView, error: NSError) {
        if (error.code == NSURL_ERROR_CANCELLED) return
        finish(webView, null)
    }

    /**
     * Second, independent trigger: a proxy that rewrites the origin defeats the gate's prefix.
     *
     * This and [finish] both read `finished` around a suspension point, but every one of them
     * runs on the main dispatcher, so they interleave sequentially rather than racing.
     */
    private fun captureIfTokenPresent(webView: WKWebView) {
        if (finished) return
        scope.launch {
            val token = readRefreshToken(webView, attempts = 1) ?: return@launch
            if (finished) return@launch
            finished = true
            webView.stopLoading()
            callbacks.value.onTokenCapture(token)
        }
    }

    private fun finish(webView: WKWebView, errorCode: String?) {
        if (finished) return
        finished = true
        webView.stopLoading()
        scope.launch {
            val token = readRefreshToken(webView, attempts = COOKIE_READ_ATTEMPTS)
            when {
                token != null -> callbacks.value.onTokenCapture(token)
                errorCode != null -> callbacks.value.onOAuthError(errorCode)
                else -> callbacks.value.onCaptureFail()
            }
        }
    }

    private suspend fun readRefreshToken(webView: WKWebView, attempts: Int): String? =
        withContext(Dispatchers.Main) {
            repeat(attempts) { attempt ->
                if (attempt > 0) delay(COOKIE_RETRY_DELAY_MS)
                val cookies = CompletableDeferred<List<*>?>()
                webView.configuration.websiteDataStore.httpCookieStore
                    .getAllCookies { cookies.complete(it) }
                val fetched = withTimeoutOrNull(COOKIE_READ_TIMEOUT_MS) { cookies.await() }
                refreshTokenFrom(fetched)?.let { return@withContext it }
            }
            null
        }

    private fun refreshTokenFrom(cookies: List<*>?): String? =
        cookies
            ?.filterIsInstance<NSHTTPCookie>()
            ?.firstOrNull { it.name == REFRESH_TOKEN_COOKIE && cookieAppliesTo(it.domain, serverHost) }
            ?.value
            ?.takeIf { it.isNotEmpty() }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun SsoWebView(
    serverUrl: String,
    provider: String,
    onTokenCapture: (String) -> Unit,
    onOAuthError: (String?) -> Unit,
    onCaptureFail: () -> Unit,
    modifier: Modifier,
) {
    val callbacks = rememberUpdatedState(
        SsoCallbacks(onTokenCapture, onOAuthError, onCaptureFail),
    )
    val scope = rememberCoroutineScope()
    val delegate = remember(serverUrl, provider) {
        SsoNavigationDelegate(serverUrl, provider, scope, callbacks)
    }

    UIKitView(
        modifier = modifier,
        factory = {
            // A non-persistent store is created fresh here and dies with the view, so there is no
            // stale cookie to clear and no provider session to re-select. Android has to ask for
            // the same guarantee with removeAllCookies; here it is structural.
            val config = WKWebViewConfiguration().apply {
                websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
                defaultWebpagePreferences.allowsContentJavaScript = true
                // Appended to WebKit's own prefix rather than replacing the agent, so the device,
                // idiom and OS it reports stay the real ones. See [safariApplicationName].
                applicationNameForUserAgent =
                    safariApplicationName(UIDevice.currentDevice.systemVersion)
            }
            val webView = WKWebView(frame = cValue { }, configuration = config)
            webView.navigationDelegate = delegate
            val startUrl = "${serverUrl.trimEnd('/')}/oauth/$provider"
            val url = NSURL.URLWithString(startUrl)
            if (url != null) {
                webView.loadRequest(NSURLRequest(uRL = url))
            } else {
                // Nothing loads, so no delegate callback can ever fire. Report the dead end here
                // or the screen sits blank forever.
                scope.launch { callbacks.value.onCaptureFail() }
            }
            webView
        },
        onRelease = { webView ->
            webView.navigationDelegate = null
            webView.stopLoading()
        },
    )
}
