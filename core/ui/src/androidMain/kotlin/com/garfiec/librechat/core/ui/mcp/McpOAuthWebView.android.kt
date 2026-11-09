package com.garfiec.librechat.core.ui.mcp

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.garfiec.librechat.core.model.mcp.McpOAuthLaunch
import com.garfiec.librechat.core.model.mcp.McpOAuthOutcome
import com.garfiec.librechat.core.ui.web.SSO_ANDROID_USER_AGENT

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal actual fun McpOAuthWebView(
    launch: McpOAuthLaunch.InApp,
    onOutcome: (McpOAuthOutcome) -> Unit,
    onLocationChange: (McpOAuthPageLocation) -> Unit,
    modifier: Modifier,
) {
    val currentOnOutcome by rememberUpdatedState(onOutcome)
    val currentOnLocationChange by rememberUpdatedState(onLocationChange)
    val gate = remember(launch) { McpOAuthNavigationGate(launch.serverUrl, launch.authorizationUrl) }
    val origins = remember(launch) { mcpOAuthCookieOrigins(launch.serverUrl, launch.authorizationUrl) }
    val serverHosts = remember(origins) { origins.mapNotNull { Uri.parse(it).host?.lowercase() }.toSet() }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                // MATCH_PARENT or Chromium resolves CSS `100vh` to 0 (see SsoWebView.android.kt).
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.userAgentString = SSO_ANDROID_USER_AGENT
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowContentAccess = false
                settings.allowFileAccess = false

                // Its own latch: stopLoading() inside a client callback can still let a later
                // callback for the stopped load arrive.
                var finished = false

                fun finish(view: WebView, outcome: McpOAuthOutcome) {
                    if (finished) return
                    finished = true
                    view.stopLoading()
                    view.loadUrl("about:blank")
                    clearSignInCookies()
                    currentOnOutcome(outcome)
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (!request.isForMainFrame) return false
                        val outcome = gate.decide(request.url.toString()) ?: return false
                        finish(view, outcome)
                        return true
                    }

                    // A server redirect chain is not always offered to shouldOverrideUrlLoading;
                    // the start of the page it lands on is.
                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        if (finished || url.startsWith("about:")) return
                        gate.decide(url)?.let { finish(view, it) }
                    }

                    // And the committed url, in case neither saw it.
                    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        if (finished || url.startsWith("about:")) return
                        val outcome = gate.decide(url)
                        if (outcome != null) {
                            finish(view, outcome)
                            return
                        }
                        mcpOAuthPageLocation(url)?.let { currentOnLocationChange(it) }
                    }

                    // A main-frame load that never arrives would otherwise leave a dead error page.
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        super.onReceivedError(view, request, error)
                        if (request.isForMainFrame) finish(view, McpOAuthOutcome.Abandoned)
                    }

                    // Only the server's own status is a dead end: a provider answering 401 with a
                    // login page is an ordinary step.
                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: WebResourceResponse,
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        if (!request.isForMainFrame) return
                        if (request.url.host?.lowercase() !in serverHosts) return
                        finish(view, McpOAuthOutcome.Error(null))
                    }
                }

                val cookies = CookieManager.getInstance()
                cookies.setAcceptCookie(true)
                cookies.setAcceptThirdPartyCookies(this, true)
                val seeds = origins.flatMap { origin -> launch.seedCookies.map { origin to it } }

                // Every step is an async callback, and the load is issued from the last one, so
                // the provider's redirect back can never outrun the binding cookies. The jar is
                // wiped first for the reason SsoWebView wipes it: the provider's own session is
                // process-global and would otherwise sign the user in as whoever was there last.
                fun seedFrom(index: Int) {
                    if (index == seeds.size) {
                        // loadUrl is never offered to shouldOverrideUrlLoading.
                        val refused = gate.decide(launch.authorizationUrl)
                        if (refused != null) finish(this, refused) else loadUrl(launch.authorizationUrl)
                        return
                    }
                    val (origin, cookie) = seeds[index]
                    cookies.setCookie(origin, cookie) { seedFrom(index + 1) }
                }
                cookies.removeAllCookies { seedFrom(0) }
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
            clearSignInCookies()
        },
    )
}

/**
 * Drops what the sign-in left in the process-wide jar: the seeded `oauth_session` binding and the
 * provider's own session, which would otherwise outlive the flow. The whole jar, because the
 * provider's hosts aren't known and nothing else needs state there: the provider session SSO keeps
 * for a re-auth only spares a login form, account switching wipes the jar, and the other WebViews
 * load local content.
 */
private fun clearSignInCookies() {
    CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }
}
