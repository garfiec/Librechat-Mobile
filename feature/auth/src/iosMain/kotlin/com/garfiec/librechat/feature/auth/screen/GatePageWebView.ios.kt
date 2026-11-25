package com.garfiec.librechat.feature.auth.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.garfiec.librechat.core.ui.web.safariApplicationName
import com.garfiec.librechat.feature.auth.oauth.cookieAppliesTo
import com.garfiec.librechat.feature.auth.oauth.cookiePathAppliesTo
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.cValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSError
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.timeIntervalSinceNow
import platform.UIKit.UIDevice
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject

private const val NSURL_ERROR_CANCELLED = -999L
private const val COOKIE_READ_TIMEOUT_MS = 2_000L

private class GatePageCallbacks(val onSignedIn: (String) -> Unit, val onFailed: () -> Unit)

/**
 * Held by [remember]: `navigationDelegate` is a weak property and Kotlin/Native won't retain it.
 * Reads the callbacks off a [State] because WebKit calls in outside composition.
 */
@OptIn(ExperimentalForeignApi::class)
private class GatePageDelegate(
    serverUrl: String,
    private val scope: CoroutineScope,
    private val callbacks: State<GatePageCallbacks>,
) : NSObject(), WKNavigationDelegateProtocol {
    private val server = NSURL.URLWithString(serverUrl)
    private val serverPath = server?.path?.takeIf { it.isNotEmpty() } ?: "/"
    private val secureServer = serverUrl.startsWith("https://", ignoreCase = true)
    private var finished = false

    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val url = decidePolicyForNavigationAction.request.URL
        val mainFrame = decidePolicyForNavigationAction.targetFrame?.mainFrame == true
        if (url == null || !mainFrame || !reachesServer(url)) {
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
            return
        }
        // Back at the server: the gate has set its session. Nothing past this point is ours to load.
        decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
        finish(webView)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) {
        fail(withError)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) {
        fail(withError)
    }

    private fun fail(error: NSError) {
        if (finished || error.code == NSURL_ERROR_CANCELLED) return
        finished = true
        callbacks.value.onFailed()
    }

    private fun reachesServer(url: NSURL): Boolean {
        val target = server ?: return false
        return url.scheme.equals(target.scheme, ignoreCase = true) &&
            url.host.equals(target.host, ignoreCase = true) &&
            url.port?.intValue == target.port?.intValue
    }

    private fun finish(webView: WKWebView) {
        if (finished) return
        finished = true
        webView.stopLoading()
        scope.launch {
            val fetched = CompletableDeferred<List<*>?>()
            webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { fetched.complete(it) }
            val all = withTimeoutOrNull(COOKIE_READ_TIMEOUT_MS) { fetched.await() }
            val host = server?.host.orEmpty()
            val header = all.orEmpty().filterIsInstance<NSHTTPCookie>()
                .filter {
                    cookieAppliesTo(it.domain, host) && cookiePathAppliesTo(it.path, serverPath) &&
                        (!it.secure || secureServer) &&
                        (it.expiresDate?.timeIntervalSinceNow?.let { left -> left > 0 } != false)
                }
                .distinctBy { it.name }
                .joinToString("; ") { "${it.name}=${it.value}" }
            val captured = gateCookies(header)
            if (captured != null) callbacks.value.onSignedIn(captured) else callbacks.value.onFailed()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun GatePageWebView(
    portalUrl: String,
    serverUrl: String,
    onSignInComplete: (String) -> Unit,
    onFail: () -> Unit,
    modifier: Modifier,
) {
    val callbacks = rememberUpdatedState(GatePageCallbacks(onSignInComplete, onFail))
    val scope = rememberCoroutineScope()
    val delegate = remember(serverUrl) { GatePageDelegate(serverUrl, scope, callbacks) }

    UIKitView(
        modifier = modifier,
        factory = {
            // Non-persistent: starts empty and dies with the view, so no earlier session is reused.
            val config = WKWebViewConfiguration().apply {
                websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
                defaultWebpagePreferences.allowsContentJavaScript = true
                applicationNameForUserAgent = safariApplicationName(UIDevice.currentDevice.systemVersion)
            }
            val webView = WKWebView(frame = cValue { }, configuration = config)
            webView.navigationDelegate = delegate
            val url = NSURL.URLWithString(portalUrl)
            if (url != null) webView.loadRequest(NSURLRequest(uRL = url)) else scope.launch { callbacks.value.onFailed() }
            webView
        },
        onRelease = { webView ->
            webView.navigationDelegate = null
            webView.stopLoading()
        },
    )
}
