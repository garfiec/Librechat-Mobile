package com.garfiec.librechat.feature.chat.components.mcpapp

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import co.touchlab.kermit.Logger
import com.garfiec.librechat.feature.chat.components.web.configureLazyListWebView
import com.garfiec.librechat.feature.chat.components.web.safelyDestroyWebView

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal actual fun LegacyUiResourceWebView(page: LegacyUiPage, host: LegacyUiFrameHost, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                // In the thread's lazy list: see configureLazyListWebView.
                configureLazyListWebView(this)
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportMultipleWindows(false)
                settings.javaScriptCanOpenWindowsAutomatically = false
                isVerticalScrollBarEnabled = false
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        request.isForMainFrame

                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                        handler?.cancel()
                        Logger.w { "SSL error in MCP-UI resource WebView: ${error?.primaryError}" }
                    }
                }
                val bridge = RelayBridge.install(this, LegacyUiPage.BRIDGE_NAME, LegacyUiPage.ORIGIN, host::onFrameMessage)
                tag = bridge
                host.send = bridge::send
                loadDataWithBaseURL(
                    LegacyUiPage.BASE_URL,
                    LegacyUiRelayPage.inlinePage(page, bridge.prelude),
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        onRelease = { webView ->
            host.send = null
            (webView.tag as? RelayBridge)?.release()
            safelyDestroyWebView(webView)
        },
    )
}
