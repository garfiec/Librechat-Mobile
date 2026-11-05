package com.garfiec.librechat.feature.chat.components.mcpapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier

/**
 * The page a legacy MCP-UI `text/html` resource runs in: an empty shell whose script puts the
 * resource in an inner `srcdoc` frame with `sandbox="allow-scripts"` (what web's `@mcp-ui/client`
 * renderer does once LibreChat strips `sandboxPermissions`), so the resource has an opaque origin,
 * no forms, popups or top navigation, and never sees the bridge. The relay forwards only messages
 * whose source is that frame, and native answers through `__sb_fromHost(json)`.
 *
 * Like web, no CSP is added: the resource may load what it likes inside its frame. The shell itself
 * loads nothing. Its origin is its own ([ORIGIN]), separate from the MCP App relay's.
 */
@Immutable
data class LegacyUiPage(val shellHtml: String, val relayScript: String) {
    companion object {
        const val ORIGIN = "https://mcp-ui.switchboard.invalid"
        const val BASE_URL = "$ORIGIN/"
        const val BRIDGE_NAME = "switchboardUi"
    }
}

internal object LegacyUiRelayPage {
    private const val HTML_TOKEN = "/*__RESOURCE_HTML__*/"

    fun build(html: String): LegacyUiPage = LegacyUiPage(
        shellHtml = SHELL,
        relayScript = RELAY.replace(HTML_TOKEN, McpAppRelayPage.inlineScriptString(html)),
    )

    /** The Android page: the shell with [prelude] and the relay inline at the end of its body. */
    fun inlinePage(page: LegacyUiPage, prelude: String): String =
        page.shellHtml.replace("<body></body>", "<body><script>$prelude\n${page.relayScript}</script></body>")

    private val SHELL = """
        <!doctype html>
        <html style="height:100%;margin:0">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>*{margin:0;padding:0;box-sizing:border-box}html,body{width:100%;height:100%;overflow:hidden;background:transparent}iframe{width:100%;height:100%;border:none;display:block;background:transparent}</style>
        </head>
        <body></body>
        </html>
    """.trimIndent()

    private val RELAY = """
(function () {
  'use strict';
  var frame = document.createElement('iframe');
  frame.setAttribute('sandbox', 'allow-scripts');
  frame.srcdoc = /*__RESOURCE_HTML__*/;
  document.body.appendChild(frame);
  window.addEventListener('message', function (event) {
    if (event.source !== frame.contentWindow) return;
    var data = event.data;
    if (typeof data === 'string') { try { data = JSON.parse(data); } catch (e) { return; } }
    if (!data || typeof data.type !== 'string') return;
    try { __sb_post(data); } catch (e) {}
  });
  window.__sb_fromHost = function (raw) {
    var msg;
    try { msg = typeof raw === 'string' ? JSON.parse(raw) : raw; } catch (e) { return; }
    if (frame.contentWindow) frame.contentWindow.postMessage(msg, '*');
  };
})();
""".trimStart()
}

/**
 * The platform web view for one legacy resource: loads [page] at [LegacyUiPage.BASE_URL] with a
 * bridge only that page's main frame reaches (as [McpAppWebView]), wired to [host] both ways. In
 * the thread's lazy list: Android draws it in a software layer, and both refuse main-frame
 * navigation and new windows.
 */
@Composable
internal expect fun LegacyUiResourceWebView(page: LegacyUiPage, host: LegacyUiFrameHost, modifier: Modifier = Modifier)
