package com.garfiec.librechat.feature.auth.oauth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

// TCHAT: whole file. See OAuthWebView.kt's kdoc for why this replaces ASWebAuthenticationSession.
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun OAuthWebView(
    startUrl: String,
    serverUrl: String,
    modifier: Modifier,
    onComplete: () -> Unit,
) {
    var isLoading by remember { mutableStateOf(true) }
    // rememberUpdatedState so the navigation delegate (built once, in `factory`) always calls the
    // latest lambda without needing to be recreated -- recreating it would reload the page.
    val currentOnComplete by rememberUpdatedState(onComplete)

    Box(modifier = modifier.fillMaxSize()) {
        UIKitView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                // Deliberately the default configuration: it uses WKWebsiteDataStore.defaultDataStore(),
                // the persistent store that synchronizes with NSHTTPCookieStorage.sharedHTTPCookieStorage
                // -- the store IosOAuthLauncher reads from. An ephemeral data store would isolate
                // cookies right back into a jar nothing else can see, reproducing the exact bug this
                // whole file exists to fix.
                val config = WKWebViewConfiguration().apply {
                    defaultWebpagePreferences.allowsContentJavaScript = true
                }
                val webView = WKWebView(frame = cValue { }, configuration = config)

                // Guards against firing twice: didFinishNavigation can run more than once for the
                // same logical navigation, and this must only ever react to the first true reading.
                var completed = false

                webView.navigationDelegate = object : NSObject(), WKNavigationDelegateProtocol {
                    override fun webView(webView: WKWebView, didStartProvisionalNavigation: WKNavigation?) {
                        isLoading = true
                    }

                    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                        isLoading = false
                        // Checked here, not didStartProvisionalNavigation: the latter can report a
                        // pre-redirect URL, before the response (and its Set-Cookie header) has
                        // actually been received. didFinishNavigation's webView.URL reflects the
                        // final, fully-loaded destination after any server-side redirect chain, so
                        // the refreshToken cookie is guaranteed to already be set by the time this
                        // checks for it.
                        val currentUrl = webView.URL?.absoluteString
                        if (!completed && currentUrl != null && isOAuthReturnUrl(serverUrl, currentUrl)) {
                            completed = true
                            currentOnComplete()
                        }
                    }
                }

                val url = NSURL.URLWithString(startUrl)
                if (url != null) {
                    webView.loadRequest(NSURLRequest.requestWithURL(url))
                }
                webView
            },
        )
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}
