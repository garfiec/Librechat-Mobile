package com.garfiec.librechat.feature.auth.screen

import android.annotation.SuppressLint
import android.net.Uri
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
import com.garfiec.librechat.feature.auth.oauth.REFRESH_TOKEN_COOKIE
import com.garfiec.librechat.feature.auth.oauth.SSO_ANDROID_USER_AGENT
import com.garfiec.librechat.feature.auth.oauth.SsoNavigationDecision
import com.garfiec.librechat.feature.auth.oauth.SsoNavigationGate
import com.garfiec.librechat.feature.auth.oauth.extractQueryParameter
import com.garfiec.librechat.feature.auth.oauth.extractRefreshTokenFromCookies

/**
 * `HttpOnly` is load-bearing, not decoration: the server sets `refreshToken` with it, and Chromium
 * refuses to overwrite an HttpOnly cookie from a value that lacks the attribute. Without it this
 * expiry never lands and a previous identity's token survives into the next sign-in.
 */
private fun expiredRefreshTokenCookie(serverUrl: String): String {
    val secure = if (serverUrl.startsWith("https://", ignoreCase = true)) "; Secure" else ""
    return "$REFRESH_TOKEN_COOKIE=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT; HttpOnly$secure"
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun SsoWebView(
    serverUrl: String,
    provider: String,
    onTokenCapture: (String) -> Unit,
    onOAuthError: (String?) -> Unit,
    onCaptureFail: () -> Unit,
    modifier: Modifier,
) {
    val currentOnTokenCapture by rememberUpdatedState(onTokenCapture)
    val currentOnOAuthError by rememberUpdatedState(onOAuthError)
    val currentOnCaptureFail by rememberUpdatedState(onCaptureFail)
    val gate = remember(serverUrl, provider) { SsoNavigationGate(serverUrl, provider) }
    val serverHost = remember(serverUrl) { Uri.parse(serverUrl).host.orEmpty() }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.userAgentString = SSO_ANDROID_USER_AGENT
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true

                // Its own latch, not the ViewModel's. stopLoading() from inside a WebViewClient
                // callback can still let a later onPageFinished fire for the stopped URL and
                // re-enter here while the first dispatch is on the stack.
                var finished = false

                fun finish(view: WebView, errorCode: String?) {
                    if (finished) return
                    finished = true
                    view.stopLoading()
                    val cookies = CookieManager.getInstance()
                    val token = extractRefreshTokenFromCookies(cookies.getCookie(serverUrl))
                    // Blank it so the half-rendered redirect target isn't left under the spinner.
                    view.loadUrl("about:blank")
                    when {
                        token != null -> {
                            cookies.setCookie(serverUrl, expiredRefreshTokenCookie(serverUrl))
                            currentOnTokenCapture(token)
                        }
                        errorCode != null -> currentOnOAuthError(errorCode)
                        else -> currentOnCaptureFail()
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        if (!request.isForMainFrame) return false
                        val url = request.url.toString()
                        if (gate.decide(url) != SsoNavigationDecision.STOP) return false
                        finish(view, extractQueryParameter(url, "error"))
                        return true
                    }

                    // Arms the gate for anything shouldOverrideUrlLoading misses, but it does NOT
                    // rescue the POST providers: Chromium delivers this once per COMMITTED
                    // navigation with the FINAL url, and Apple's and SAML's form_post callbacks
                    // answer with a 302, so the callback url itself never commits and is never
                    // seen here either. What ends those round-trips is captureIfTokenPresent at
                    // the commit of the redirect target — the post-callback page IS fetched, and
                    // only finish()'s stopLoading beats its scripts to the refresh endpoint.
                    // Arming from shouldInterceptRequest (which does see POST main-frame requests,
                    // off the UI thread, so callbackSeen would need to be @Volatile) would close
                    // it properly; that wants an Apple device to verify.
                    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        if (finished || url.startsWith("about:")) return
                        if (gate.decide(url) == SsoNavigationDecision.STOP) {
                            finish(view, extractQueryParameter(url, "error"))
                            return
                        }
                        captureIfTokenPresent(view, url)
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        captureIfTokenPresent(view, url)
                    }

                    // A main-frame load that never arrives ends the round-trip just as surely as
                    // one that does, and nothing else would report it: no navigation follows, so
                    // neither the gate nor the cookie probe ever fires again. Without these the
                    // screen keeps a dead Chromium error page with no spinner and no error pane —
                    // offline, DNS/TLS failure, a provider redirect to a scheme WebView cannot
                    // open (intent://, market://), or a 404 because the server has no such
                    // provider configured. finish() still reads the jar first, so a failure that
                    // lands after the callback already set the cookie is a capture, not a loss.
                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request.isForMainFrame) finish(view, null)
                    }

                    // Restricted to the server's own host, unlike onReceivedError. A status code
                    // is flow-meaningful at an identity provider — a 401 carrying a login page is
                    // an ordinary step — so only OUR end returning one (no such provider
                    // configured, reverse proxy not routing /oauth) is a dead end worth reporting.
                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: WebResourceResponse,
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        if (!request.isForMainFrame) return
                        if (!request.url.host.equals(serverHost, ignoreCase = true)) return
                        finish(view, null)
                    }

                    // Second, independent trigger. The gate's prefix match is defeated by a proxy
                    // that rewrites the origin, so a cookie that is simply there still wins.
                    private fun captureIfTokenPresent(view: WebView, url: String) {
                        if (finished || url.startsWith("about:")) return
                        val cookies = CookieManager.getInstance().getCookie(serverUrl)
                        if (extractRefreshTokenFromCookies(cookies) != null) {
                            finish(view, null)
                        }
                    }
                }

                val cookies = CookieManager.getInstance()
                cookies.setAcceptCookie(true)
                cookies.setAcceptThirdPartyCookies(this, true)
                val startUrl = "${serverUrl.trimEnd('/')}/oauth/$provider"
                // The load is issued from inside the clear's callback, because removeAllCookies is
                // asynchronous and that ordering is the only thing keeping a stale token from being
                // read back before the round-trip has even reached the provider.
                //
                // The whole jar, not just the server's refreshToken: the provider's own session
                // cookie is process-global and survives our sign-out, so leaving it lets the flow
                // re-select whoever was last signed in at the provider and complete as them with no
                // chooser and no error. Nothing else in this jar holds a session — the chat
                // WebViews render vendored local assets — and AndroidSwitchCacheCleaner already
                // wipes it whole on every account switch for the same reason.
                cookies.removeAllCookies { loadUrl(startUrl) }
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        },
    )
}
