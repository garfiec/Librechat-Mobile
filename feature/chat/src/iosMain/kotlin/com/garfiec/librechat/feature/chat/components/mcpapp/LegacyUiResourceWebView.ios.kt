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

/** Its own isolated world, apart from the MCP App relay's. */
private const val CONTENT_WORLD = "switchboard-mcp-ui"

private val IOS_PRELUDE = """
function __sb_post(m) { window.webkit.messageHandlers.${LegacyUiPage.BRIDGE_NAME}.postMessage(JSON.stringify(m)); }
""".trimIndent()

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun LegacyUiResourceWebView(page: LegacyUiPage, host: LegacyUiFrameHost, modifier: Modifier) {
    val handler = remember(host) { LegacyFrameMessageHandler(host) }
    val navigation = remember(host) { LegacyShellNavigationPolicy() }
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
            controller.addScriptMessageHandler(handler, contentWorld = world, name = LegacyUiPage.BRIDGE_NAME)
            val config = WKWebViewConfiguration().apply {
                userContentController = controller
                defaultWebpagePreferences.allowsContentJavaScript = true
                websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
            }
            val webView = WKWebView(frame = cValue { }, configuration = config)
            webView.setOpaque(false)
            webView.backgroundColor = background
            webView.scrollView.backgroundColor = background
            webView.scrollView.scrollEnabled = false
            webView.navigationDelegate = navigation
            host.send = { raw ->
                webView.evaluateJavaScript(
                    "window.__sb_fromHost(" + McpAppRelayPage.inlineScriptString(raw) + ")",
                    inFrame = null,
                    inContentWorld = world,
                    completionHandler = null,
                )
            }
            webView.loadHTMLString(page.shellHtml, baseURL = NSURL(string = LegacyUiPage.BASE_URL))
            webView
        },
        update = { webView ->
            webView.backgroundColor = background
            webView.scrollView.backgroundColor = background
        },
        onRelease = { webView ->
            host.send = null
            webView.stopLoading()
            webView.configuration.userContentController
                .removeScriptMessageHandlerForName(LegacyUiPage.BRIDGE_NAME, contentWorld = WKContentWorld.worldWithName(CONTENT_WORLD))
            webView.navigationDelegate = null
        },
    )
}

private class LegacyFrameMessageHandler(private val host: LegacyUiFrameHost) : NSObject(), WKScriptMessageHandlerProtocol {
    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage,
    ) {
        if (!didReceiveScriptMessage.frameInfo.mainFrame) return
        val body = didReceiveScriptMessage.body as? String ?: return
        host.onFrameMessage(body)
    }
}

/** The shell loads once at its own base URL; the resource's frame navigates as its sandbox permits. */
private class LegacyShellNavigationPolicy : NSObject(), WKNavigationDelegateProtocol {
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
        } else if (!shellLoaded && action.request.URL?.absoluteString?.startsWith(LegacyUiPage.ORIGIN) == true) {
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
