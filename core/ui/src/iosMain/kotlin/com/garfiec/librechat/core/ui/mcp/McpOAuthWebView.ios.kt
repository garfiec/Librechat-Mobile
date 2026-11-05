package com.garfiec.librechat.core.ui.mcp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.garfiec.librechat.core.model.mcp.McpOAuthLaunch
import com.garfiec.librechat.core.model.mcp.McpOAuthOutcome
import com.garfiec.librechat.core.ui.web.safariApplicationName
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.cValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSError
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.UIKit.UIDevice
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKNavigationResponse
import platform.WebKit.WKNavigationResponsePolicy
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject

/** `NSURLErrorCancelled`: a superseded provisional load, routine in an OAuth redirect chain. */
private const val NSURL_ERROR_CANCELLED = -999L
private const val COOKIE_SET_TIMEOUT_MS = 2_000L

/**
 * Held by [remember]: `navigationDelegate` is weak and Kotlin/Native will not retain it. Reads the
 * callback off a [State] because WebKit calls it outside composition.
 */
@OptIn(ExperimentalForeignApi::class)
private class McpOAuthNavigationDelegate(
    private val gate: McpOAuthNavigationGate,
    private val serverHosts: Set<String>,
    private val onOutcome: State<(McpOAuthOutcome) -> Unit>,
    private val onLocationChange: State<(McpOAuthPageLocation) -> Unit>,
) : NSObject(), WKNavigationDelegateProtocol {

    private var finished = false

    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val action = decidePolicyForNavigationAction
        val url = action.request.URL?.absoluteString
        val outcome = if (url != null && action.targetFrame?.mainFrame == true) gate.decide(url) else null
        if (outcome == null) {
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
            return
        }
        decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
        finish(webView, outcome)
    }

    // Only the server's own error status is a dead end; a provider's 401 login page is a step.
    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationResponse: WKNavigationResponse,
        decisionHandler: (WKNavigationResponsePolicy) -> Unit,
    ) {
        decisionHandler(WKNavigationResponsePolicy.WKNavigationResponsePolicyAllow)
        val response = decidePolicyForNavigationResponse.response as? NSHTTPURLResponse ?: return
        if (!decidePolicyForNavigationResponse.forMainFrame) return
        val host = response.URL?.host?.lowercase() ?: return
        if (response.statusCode >= 400 && host in serverHosts) finish(webView, McpOAuthOutcome.Error(null))
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didCommitNavigation: WKNavigation?) {
        if (finished) return
        val location = webView.URL?.absoluteString?.let(::mcpOAuthPageLocation) ?: return
        onLocationChange.value(location)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) {
        if (withError.code != NSURL_ERROR_CANCELLED) finish(webView, McpOAuthOutcome.Abandoned)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) {
        if (withError.code != NSURL_ERROR_CANCELLED) finish(webView, McpOAuthOutcome.Abandoned)
    }

    fun finish(webView: WKWebView, outcome: McpOAuthOutcome) {
        if (finished) return
        finished = true
        webView.stopLoading()
        onOutcome.value(outcome)
    }
}

/** Parses one `Set-Cookie` value for [origin] the way the network stack would have stored it. */
private fun cookiesFor(origin: String, setCookie: String): List<NSHTTPCookie> {
    val url = NSURL.URLWithString(origin) ?: return emptyList()
    return NSHTTPCookie.cookiesWithResponseHeaderFields(mapOf<Any?, Any?>("Set-Cookie" to setCookie), url)
        .filterIsInstance<NSHTTPCookie>()
}

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun McpOAuthWebView(
    launch: McpOAuthLaunch.InApp,
    onOutcome: (McpOAuthOutcome) -> Unit,
    onLocationChange: (McpOAuthPageLocation) -> Unit,
    modifier: Modifier,
) {
    val currentOnOutcome = rememberUpdatedState(onOutcome)
    val currentOnLocationChange = rememberUpdatedState(onLocationChange)
    val scope: CoroutineScope = rememberCoroutineScope()
    val origins = remember(launch) { mcpOAuthCookieOrigins(launch.serverUrl, launch.authorizationUrl) }
    val delegate = remember(launch) {
        McpOAuthNavigationDelegate(
            gate = McpOAuthNavigationGate(launch.serverUrl, launch.authorizationUrl),
            serverHosts = origins.mapNotNull { NSURL.URLWithString(it)?.host?.lowercase() }.toSet(),
            onOutcome = currentOnOutcome,
            onLocationChange = currentOnLocationChange,
        )
    }

    UIKitView(
        modifier = modifier,
        factory = {
            // A fresh non-persistent store: nothing stale to clear, no provider session to reuse,
            // and the seeded cookies die with the view.
            val config = WKWebViewConfiguration().apply {
                websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
                defaultWebpagePreferences.allowsContentJavaScript = true
                applicationNameForUserAgent = safariApplicationName(UIDevice.currentDevice.systemVersion)
            }
            val webView = WKWebView(frame = cValue { }, configuration = config)
            webView.navigationDelegate = delegate
            val startUrl = NSURL.URLWithString(launch.authorizationUrl)
            if (startUrl == null) {
                scope.launch { delegate.finish(webView, McpOAuthOutcome.Abandoned) }
                return@UIKitView webView
            }
            // The store exists only with the view, and WKHTTPCookieStore applies a cookie
            // asynchronously: the load waits for every completion, or the callback can arrive
            // before the binding cookie does.
            val store = webView.configuration.websiteDataStore.httpCookieStore
            val cookies = origins.flatMap { origin -> launch.seedCookies.flatMap { cookiesFor(origin, it) } }
            scope.launch {
                cookies.forEach { cookie ->
                    val set = CompletableDeferred<Unit>()
                    store.setCookie(cookie) { set.complete(Unit) }
                    withTimeoutOrNull(COOKIE_SET_TIMEOUT_MS) { set.await() }
                }
                webView.loadRequest(NSURLRequest(uRL = startUrl))
            }
            webView
        },
        onRelease = { webView ->
            webView.navigationDelegate = null
            webView.stopLoading()
        },
    )
}
