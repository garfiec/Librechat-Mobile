package com.garfiec.librechat.feature.chat.components.mcpapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The platform web view an MCP App runs in: loads [page] at [McpAppPage.BASE_URL], installs a
 * bridge reachable only from that page's own main frame, and wires it to [host] both ways
 * (`host.send` out, `host.onRelayMessage` in). Torn down, with the bridge, when it leaves.
 *
 * - Android: `WebViewCompat.addWebMessageListener` scoped to [McpAppPage.ORIGIN]; never
 *   `addJavascriptInterface`, which is injected into every frame including the App's. Without
 *   `WEB_MESSAGE_LISTENER` it calls `host.bridgeUnavailable()` and loads nothing.
 * - iOS: the relay and its `WKScriptMessageHandler` live in a dedicated `WKContentWorld`, main frame
 *   only, and the handler also drops anything not from the main frame.
 *
 * Both refuse main-frame navigation off the page, new windows, and TLS errors.
 */
@Composable
expect fun McpAppWebView(page: McpAppPage, host: McpAppHost, modifier: Modifier = Modifier)
