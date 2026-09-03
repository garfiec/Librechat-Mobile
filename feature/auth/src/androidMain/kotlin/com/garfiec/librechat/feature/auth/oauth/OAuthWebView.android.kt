package com.garfiec.librechat.feature.auth.oauth

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.viewinterop.AndroidView

// TCHAT: whole file. See OAuthWebView.kt's kdoc for why this replaces the old Custom-Tab launch.
@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun OAuthWebView(
    startUrl: String,
    serverUrl: String,
    modifier: Modifier,
    onComplete: () -> Unit,
) {
    var isLoading by remember { mutableStateOf(true) }
    // rememberUpdatedState so the WebViewClient (built once, in `factory`) always calls the latest
    // lambda without needing to be recreated -- recreating it would reload the page from scratch.
    val currentOnComplete by rememberUpdatedState(onComplete)

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    // Guards against firing twice: onPageFinished can run more than once for the
                    // same logical navigation (e.g. a page that loads a sub-resource that also
                    // completes), and this must only ever react to the first true reading.
                    var completed = false

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            // Checked here, not onPageStarted: onPageStarted can report the
                            // pre-redirect URL, before the response (and its Set-Cookie header)
                            // has actually been received. onPageFinished's url reflects the final,
                            // fully-loaded destination after any server-side redirect chain, so the
                            // refreshToken cookie is guaranteed to already be in CookieManager by
                            // the time this checks for it.
                            if (!completed && url != null && isOAuthReturnUrl(serverUrl, url)) {
                                completed = true
                                CookieManager.getInstance().flush()
                                currentOnComplete()
                            }
                        }
                    }

                    loadUrl(startUrl)
                }
            },
        )
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}
