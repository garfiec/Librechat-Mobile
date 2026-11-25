package com.garfiec.librechat.feature.auth.screen

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.garfiec.librechat.core.ui.web.SSO_ANDROID_USER_AGENT

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal actual fun GatePageWebView(
    portalUrl: String,
    serverUrl: String,
    onSignInComplete: (String) -> Unit,
    onFail: () -> Unit,
    modifier: Modifier,
) {
    val currentOnSignedIn by rememberUpdatedState(onSignInComplete)
    val currentOnFailed by rememberUpdatedState(onFail)
    val server = Uri.parse(serverUrl)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                // Without explicit MATCH_PARENT, Chromium resolves CSS `100vh` to 0.
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.userAgentString = SSO_ANDROID_USER_AGENT
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                var finished = false

                fun finish(view: WebView) {
                    if (finished) return
                    finished = true
                    view.stopLoading()
                    val cookies = CookieManager.getInstance()
                    val captured = gateCookies(cookies.getCookie(serverUrl))
                    view.loadUrl("about:blank")
                    // The jar is process-wide and the SSO web view starts from it too.
                    cookies.removeAllCookies(null)
                    if (captured != null) currentOnSignedIn(captured) else currentOnFailed()
                }

                fun reachesServer(url: Uri): Boolean =
                    url.scheme.equals(server.scheme, ignoreCase = true) &&
                        url.host.equals(server.host, ignoreCase = true) &&
                        url.port == server.port

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (!request.isForMainFrame || !reachesServer(request.url)) return false
                        finish(view)
                        return true
                    }

                    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        if (!finished && reachesServer(Uri.parse(url))) finish(view)
                    }

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        super.onReceivedError(view, request, error)
                        if (request.isForMainFrame && !finished) {
                            finished = true
                            CookieManager.getInstance().removeAllCookies(null)
                            currentOnFailed()
                        }
                    }
                }
                // Start from an empty jar, and load only once the clear has landed: setCookie and
                // removeAllCookies are asynchronous.
                CookieManager.getInstance().removeAllCookies { loadUrl(portalUrl) }
            }
        },
    )
}
