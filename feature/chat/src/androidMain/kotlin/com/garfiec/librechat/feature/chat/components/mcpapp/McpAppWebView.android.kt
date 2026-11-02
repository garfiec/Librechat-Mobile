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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import co.touchlab.kermit.Logger
import com.garfiec.librechat.feature.chat.components.web.safelyDestroyWebView

/** Reaches native through the listener object; native answers through its `onmessage`. */
private val ANDROID_PRELUDE = """
var __sb_bridge = window.${McpAppPage.BRIDGE_NAME};
function __sb_post(m) { __sb_bridge.postMessage(JSON.stringify(m)); }
__sb_bridge.onmessage = function (e) { window.__sb_fromHost(e.data); };
""".trimIndent()

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun McpAppWebView(page: McpAppPage, host: McpAppHost, modifier: Modifier) {
    val supported = remember { WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) }
    if (!supported) {
        LaunchedEffect(host) { host.bridgeUnavailable() }
        return
    }
    val html = remember(page) { McpAppRelayPage.inlinePage(page, ANDROID_PRELUDE) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportMultipleWindows(false)
                settings.javaScriptCanOpenWindowsAutomatically = false
                // Default onCreateWindow refuses every new window.
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    // The page is loaded once with loadDataWithBaseURL; nothing may replace it.
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        request.isForMainFrame

                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                        handler?.cancel()
                        Logger.w { "SSL error in MCP app WebView: ${error?.primaryError}" }
                    }
                }
                // Before the load, so the object exists when the relay's first line runs. Scoped to
                // the shell's synthetic origin: the App's opaque srcdoc frame never matches it.
                WebViewCompat.addWebMessageListener(
                    this,
                    McpAppPage.BRIDGE_NAME,
                    setOf(McpAppPage.ORIGIN),
                ) { _, message, _, isMainFrame, replyProxy ->
                    if (!isMainFrame) return@addWebMessageListener
                    host.send = { raw -> replyProxy.postMessage(raw) }
                    message.data?.let(host::onRelayMessage)
                }
                loadDataWithBaseURL(McpAppPage.BASE_URL, html, "text/html", "UTF-8", null)
            }
        },
        onRelease = { webView ->
            host.send = null
            WebViewCompat.removeWebMessageListener(webView, McpAppPage.BRIDGE_NAME)
            safelyDestroyWebView(webView)
        },
    )
}
