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
    private const val FIT_TOKEN = "/*__FIT_FRAME__*/"

    /**
     * The inline page is exactly its view's height, which the host sizes from the resource's
     * reports. The [fullView] page instead grows the frame to each reported height (web's
     * `autoResizeIframe`) and scrolls, since the view there is only a screen tall.
     */
    fun build(html: String, fullView: Boolean = false): LegacyUiPage = LegacyUiPage(
        shellHtml = if (fullView) SCROLLING_SHELL else SHELL,
        // The resource goes in last, so a token in its HTML is never replaced.
        relayScript = RELAY
            .replace(FIT_TOKEN, fullView.toString())
            .replace(HTML_TOKEN, McpAppRelayPage.inlineScriptString(html)),
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

    private val SCROLLING_SHELL = """
        <!doctype html>
        <html style="margin:0">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>*{margin:0;padding:0;box-sizing:border-box}html,body{width:100%;background:transparent}iframe{width:100%;height:100vh;border:none;display:block;background:transparent}</style>
        </head>
        <body></body>
        </html>
    """.trimIndent()

    private val RELAY = """
(function () {
  'use strict';
  var fitFrame = /*__FIT_FRAME__*/;
  var frame = document.createElement('iframe');
  frame.setAttribute('sandbox', 'allow-scripts');
  frame.srcdoc = /*__RESOURCE_HTML__*/;
  document.body.appendChild(frame);
  window.addEventListener('message', function (event) {
    if (event.source !== frame.contentWindow) return;
    var data = event.data;
    if (typeof data === 'string') { try { data = JSON.parse(data); } catch (e) { return; } }
    if (!data || typeof data.type !== 'string') return;
    if (fitFrame && data.type === 'ui-size-change' && data.payload) {
      var h = Number(data.payload.height);
      if (isFinite(h) && h > 0) frame.style.height = Math.ceil(h) + 'px';
    }
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
 * bridge only that page's main frame reaches (as [McpAppWebView]), wired to [host] both ways. Both
 * refuse main-frame navigation and new windows. In the thread's lazy list Android draws it in a
 * software layer and neither scrolls; the [fullView] one draws in hardware and scrolls.
 */
@Composable
internal expect fun LegacyUiResourceWebView(
    page: LegacyUiPage,
    host: LegacyUiFrameHost,
    modifier: Modifier = Modifier,
    fullView: Boolean = false,
)

/**
 * Whether an inline resource is held to a share of the window. Android draws inline web views in a
 * software layer, which paints nothing once it is much taller than the screen; where the cap holds,
 * a taller resource offers its full view instead.
 */
internal expect val legacyUiInlineCapped: Boolean
