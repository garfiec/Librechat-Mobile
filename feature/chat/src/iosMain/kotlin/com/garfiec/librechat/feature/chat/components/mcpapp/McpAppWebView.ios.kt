package com.garfiec.librechat.feature.chat.components.mcpapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import platform.Foundation.NSURL
import platform.UIKit.UIColor
import platform.WebKit.WKContentWorld
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject

/** The isolated world the relay and its message handler live in; the page's own world can't see either. */
private const val CONTENT_WORLD = "switchboard-mcp-app"

private val IOS_PRELUDE = """
function __sb_post(m) { window.webkit.messageHandlers.${McpAppPage.BRIDGE_NAME}.postMessage(JSON.stringify(m)); }
""".trimIndent()

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun McpAppWebView(page: McpAppPage, host: McpAppHost, modifier: Modifier) {
    val handler = remember(host) { RelayMessageHandler(host) }
    val navigation = remember(host) { RelayNavigationPolicy() }
    // Compose clears its canvas where a UIKit view sits and the view shows through from below, so a
    // transparent web view over a transparent page let the chat under the dialog draw through the
    // App. The panel's surface goes on the view itself, as Android paints it behind its WebView.
    val surface = MaterialTheme.colorScheme.surface
    val background = remember(surface) { surface.toUIColor() }
    UIKitView(
        modifier = modifier,
        factory = {
            val world = WKContentWorld.worldWithName(CONTENT_WORLD)
            val controller = WKUserContentController()
            controller.addUserScript(
                WKUserScript(
                    source = IOS_PRELUDE + "\n" + page.relayScript,
                    injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentEnd,
                    forMainFrameOnly = true,
                    inContentWorld = world,
                ),
            )
            controller.addScriptMessageHandler(handler, contentWorld = world, name = McpAppPage.BRIDGE_NAME)
            val config = WKWebViewConfiguration().apply {
                userContentController = controller
                defaultWebpagePreferences.allowsContentJavaScript = true
                websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
            }
            val webView = WKWebView(frame = cValue { }, configuration = config)
            webView.setOpaque(false)
            webView.backgroundColor = background
            webView.scrollView.backgroundColor = background
            webView.navigationDelegate = navigation
            host.send = { raw ->
                webView.evaluateJavaScript(
                    "window.__sb_fromHost(" + McpAppRelayPage.inlineScriptString(raw) + ")",
                    inFrame = null,
                    inContentWorld = world,
                    completionHandler = null,
                )
            }
            webView.loadHTMLString(page.shellHtml, baseURL = NSURL(string = McpAppPage.BASE_URL))
            webView
        },
        update = { webView ->
            webView.backgroundColor = background
            webView.scrollView.backgroundColor = background
        },
        onRelease = { webView ->
            host.send = null
            webView.stopLoading()
            // The controller retains its handler, which holds the host.
            webView.configuration.userContentController
                .removeScriptMessageHandlerForName(McpAppPage.BRIDGE_NAME, contentWorld = WKContentWorld.worldWithName(CONTENT_WORLD))
            webView.navigationDelegate = null
        },
    )
}

/** Hands the relay's messages to the host; anything not from the shell's main frame is dropped. */
private class RelayMessageHandler(private val host: McpAppHost) : NSObject(), WKScriptMessageHandlerProtocol {
    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage,
    ) {
        if (!didReceiveScriptMessage.frameInfo.mainFrame) return
        val body = didReceiveScriptMessage.body as? String ?: return
        host.onRelayMessage(body)
    }
}

/**
 * Lets the shell load once, at its own base URL, and never again; subframes (the App's srcdoc
 * frame, frames its CSP allows) navigate as the sandbox permits. `navigationDelegate` is weak:
 * the composable holds this for the web view's lifetime.
 */
private class RelayNavigationPolicy : NSObject(), WKNavigationDelegateProtocol {
    private var shellLoaded = false

    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val action = decidePolicyForNavigationAction
        val mainFrame = action.targetFrame?.mainFrame ?: true
        val allow = if (!mainFrame) {
            true
        } else if (!shellLoaded && action.request.URL?.absoluteString?.startsWith(McpAppPage.ORIGIN) == true) {
            shellLoaded = true
            true
        } else {
            false
        }
        decisionHandler(
            if (allow) WKNavigationActionPolicy.WKNavigationActionPolicyAllow else WKNavigationActionPolicy.WKNavigationActionPolicyCancel,
        )
    }
}

private fun Color.toUIColor(): UIColor =
    UIColor.colorWithRed(red.toDouble(), green.toDouble(), blue.toDouble(), alpha.toDouble())
