package com.garfiec.librechat.feature.chat.components.mcpapp

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.mcp.McpAppCspDeclaration
import com.garfiec.librechat.core.model.mcp.buildMcpAppCspPolicy
import kotlinx.serialization.json.JsonPrimitive

/**
 * The page an App runs inside: an empty shell plus the relay script, a port of the inner-frame
 * half of upstream's `client/public/mcp-sandbox.html` (registered in `scripts/mirrors.json`).
 *
 * The web loads that proxy from a dedicated origin and talks to it with `postMessage`; it only
 * ever talks to `window.parent` and its `frame-ancestors` would not list us, so the app ships its
 * own. Here the "parent" is native code: the relay posts through a bridge the platform installs
 * for the shell's origin only ([ORIGIN]), and native delivers with `__sb_fromHost(json)`.
 *
 * The App itself runs in an inner `srcdoc` frame with an opaque origin (`sandbox` from
 * [McpAppProtocol.SANDBOX_TOKENS], never `allow-same-origin`), so neither the bridge nor the relay's
 * globals reach it. Its View CSP goes in as a `<meta>` ahead of its own markup, with a nonce
 * heartbeat; the relay forwards only messages whose source is that frame and only while the nonce
 * attests, exactly as `mcp-sandbox.html` does. Nothing is fetched to build the page: it names no
 * script or stylesheet, so there is nothing to vendor.
 */
@Immutable
data class McpAppPage(
    /** The shell document, carrying the loader CSP. Load it with [BASE_URL] as its base. */
    val shellHtml: String,
    /** The relay; runs after the shell's body exists, with a platform prelude defining `__sb_post`. */
    val relayScript: String,
) {
    companion object {
        /** A synthetic origin (`.invalid` never resolves) that only the shell document has. */
        const val ORIGIN = "https://mcp-app.switchboard.invalid"
        const val BASE_URL = "$ORIGIN/"

        /** The name of the native bridge object (Android) or message handler (iOS). */
        const val BRIDGE_NAME = "switchboardHost"
    }
}

internal object McpAppRelayPage {

    fun build(csp: McpAppCspDeclaration): McpAppPage {
        val loaderCsp = buildMcpAppCspPolicy(csp, proxyLoader = true)
        val viewCsp = buildMcpAppCspPolicy(csp, proxyLoader = false)
        val shell = """
            <!doctype html>
            <html style="height:100%;margin:0">
            <head>
            <meta charset="utf-8">
            <meta http-equiv="Content-Security-Policy" content="${escapeAttribute(loaderCsp)}">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>*{margin:0;padding:0;box-sizing:border-box}html,body{width:100%;height:100%;overflow:hidden;background:transparent}iframe{width:100%;height:100%;border:none;display:block;background:transparent}</style>
            </head>
            <body></body>
            </html>
        """.trimIndent()
        return McpAppPage(shellHtml = shell, relayScript = RELAY_SCRIPT.replace(VIEW_CSP_TOKEN, inlineScriptString(viewCsp)))
    }

    /** The Android page: the shell with the relay inline at the end of its body. */
    fun inlinePage(page: McpAppPage, prelude: String): String =
        page.shellHtml.replace("<body></body>", "<body><script>$prelude\n${page.relayScript}</script></body>")

    /** Upstream `serializeInlineScriptString`: a JS string literal safe inside a `<script>`. */
    fun inlineScriptString(value: String): String {
        val quoted = JsonPrimitive(value).toString()
        val out = StringBuilder(quoted.length)
        for (c in quoted) {
            when (c) {
                '<', '>', '&', ' ', ' ' -> out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    private fun escapeAttribute(value: String): String =
        value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

    private const val VIEW_CSP_TOKEN = "/*__VIEW_CSP__*/"

    /**
     * The relay. `__sb_post(msg)` (platform prelude) reaches native; native calls
     * `__sb_fromHost(json)`. Everything else is `mcp-sandbox.html`'s logic with the parent window
     * replaced by native. Permission delegation (`allow`) is what native put in the resource-ready
     * `permissions`: the declared ones it can grant, never camera or microphone.
     */
    private val RELAY_SCRIPT = """
(function () {
  'use strict';
  var VIEW_CSP = /*__VIEW_CSP__*/;
  var SANDBOX_PREFIX = 'ui/notifications/sandbox-';
  var ATTEST_METHOD = 'ui/notifications/sandbox-frame-attest';
  var CHALLENGE_METHOD = 'ui/notifications/sandbox-frame-challenge';
  var DETACH_METHOD = 'ui/notifications/sandbox-frame-detach';
  var RESOURCE_READY_METHOD = 'ui/notifications/sandbox-resource-ready';
  var PROXY_READY_METHOD = 'ui/notifications/sandbox-proxy-ready';
  var ATTEST_INTERVAL_MS = 400;
  var ATTEST_TTL_MS = 1200;
  var READY_RETRY_MS = 500;
  var READY_MAX_RETRIES = 20;
  var MAX_PENDING_MESSAGES = 50;
  var DEFAULT_SANDBOX = 'allow-scripts allow-forms';
  var ALLOWED_SANDBOX_TOKENS = ['allow-scripts', 'allow-forms', 'allow-pointer-lock'];

  var innerFrame = null;
  var resourceApplied = false;
  var readyInterval = null;
  var readyRetries = 0;
  var attestNonce = null;
  var pendingChallenge = null;
  var attestedAt = 0;
  var pendingToInner = [];
  var pendingToHost = [];

  function postToHost(msg) {
    try { __sb_post(msg); } catch (e) {}
  }

  function mintNonce() {
    var bytes = new Uint8Array(16);
    crypto.getRandomValues(bytes);
    var hex = '';
    for (var i = 0; i < bytes.length; i += 1) {
      hex += ('0' + bytes[i].toString(16)).slice(-2);
    }
    return hex;
  }

  function frameUsable() { return !!innerFrame && !!innerFrame.contentWindow; }
  function attestFresh() { return Date.now() - attestedAt < ATTEST_TTL_MS; }

  function enqueue(queue, msg) {
    if (queue.length >= MAX_PENDING_MESSAGES) { queue.shift(); }
    queue.push(msg);
  }

  function flushPending() {
    var toInner = pendingToInner;
    var toHost = pendingToHost;
    pendingToInner = [];
    pendingToHost = [];
    for (var i = 0; i < toInner.length; i += 1) { innerFrame.contentWindow.postMessage(toInner[i], '*'); }
    for (var j = 0; j < toHost.length; j += 1) { postToHost(toHost[j]); }
  }

  function discardPending() { pendingToInner = []; pendingToHost = []; }

  function stopReadyInterval() {
    if (readyInterval) { clearInterval(readyInterval); readyInterval = null; }
  }

  function releaseInnerFrame() {
    discardPending();
    if (innerFrame) { innerFrame.remove(); innerFrame = null; }
  }

  function notifyReady() {
    postToHost({ jsonrpc: '2.0', method: PROXY_READY_METHOD, params: {} });
    readyInterval = setInterval(function () {
      if (innerFrame || readyRetries >= READY_MAX_RETRIES) { stopReadyInterval(); return; }
      readyRetries += 1;
      postToHost({ jsonrpc: '2.0', method: PROXY_READY_METHOD, params: {} });
    }, READY_RETRY_MS);
  }

  window.__sb_fromHost = function (raw) {
    var msg;
    try { msg = typeof raw === 'string' ? JSON.parse(raw) : raw; } catch (e) { return; }
    if (!msg || msg.jsonrpc !== '2.0') return;
    if (msg.method === RESOURCE_READY_METHOD) {
      stopReadyInterval();
      createInnerFrame(msg.params || {});
      return;
    }
    if (!frameUsable()) return;
    if (!attestFresh()) { enqueue(pendingToInner, msg); return; }
    innerFrame.contentWindow.postMessage(msg, '*');
  };

  window.addEventListener('message', function (event) {
    if (!innerFrame || event.source !== innerFrame.contentWindow) return;
    var msg;
    try { msg = typeof event.data === 'string' ? JSON.parse(event.data) : event.data; } catch (e) { return; }
    if (!msg || msg.jsonrpc !== '2.0') return;
    var attested = !!attestNonce && !!msg.params && msg.params.nonce === attestNonce;
    if (msg.method === ATTEST_METHOD) {
      if (attested && frameUsable() && (pendingChallenge === null || msg.params.challenge === pendingChallenge)) {
        pendingChallenge = null;
        attestedAt = Date.now();
        flushPending();
      }
      return;
    }
    if (msg.method === DETACH_METHOD) {
      if (attested) { attestedAt = 0; releaseInnerFrame(); }
      return;
    }
    if (typeof msg.method === 'string' && msg.method.indexOf(SANDBOX_PREFIX) === 0) return;
    if (!frameUsable()) return;
    if (!attestFresh()) { enqueue(pendingToHost, msg); return; }
    postToHost(msg);
  });

  function buildBootstrap(nonce) {
    return (
      '<script>(function(){var N=' + JSON.stringify(nonce) +
      ';var post=function(m){parent.postMessage({jsonrpc:"2.0",method:m,params:{nonce:N}},"*")};' +
      'addEventListener("message",function(e){if(e.source===parent&&e.data&&e.data.method===' +
      JSON.stringify(CHALLENGE_METHOD) +
      '){parent.postMessage({jsonrpc:"2.0",method:' + JSON.stringify(ATTEST_METHOD) +
      ',params:{nonce:N,challenge:e.data.params.challenge}},"*")}});' +
      'post(' + JSON.stringify(ATTEST_METHOD) + ');setInterval(function(){post(' + JSON.stringify(ATTEST_METHOD) +
      ')},' + ATTEST_INTERVAL_MS + ');addEventListener("pagehide",function(){post(' + JSON.stringify(DETACH_METHOD) +
      ')},{capture:true});})();<\/script>'
    );
  }

  function buildViewInjection(nonce) {
    var meta = document.createElement('meta');
    meta.httpEquiv = 'Content-Security-Policy';
    meta.content = VIEW_CSP;
    return meta.outerHTML + buildBootstrap(nonce);
  }

  function createInnerFrame(params) {
    if (typeof VIEW_CSP !== 'string' || resourceApplied || typeof params.html !== 'string') return;
    resourceApplied = true;
    attestNonce = mintNonce();
    innerFrame = document.createElement('iframe');
    var frame = innerFrame;
    var requested = String(params.sandbox || DEFAULT_SANDBOX).split(/\s+/).filter(Boolean);
    var granted = ALLOWED_SANDBOX_TOKENS.filter(function (t) { return requested.indexOf(t) !== -1; });
    innerFrame.setAttribute('sandbox', granted.length > 0 ? granted.join(' ') : DEFAULT_SANDBOX);
    var allowParts = [];
    var permissions = params.permissions;
    if (permissions && typeof permissions === 'object') {
      if (permissions.geolocation) allowParts.push('geolocation');
      if (permissions.clipboardWrite) allowParts.push('clipboard-write');
    }
    if (allowParts.length > 0) { innerFrame.setAttribute('allow', allowParts.join('; ')); }
    innerFrame.srcdoc = injectIntoHead(params.html, buildViewInjection(attestNonce));
    innerFrame.style.cssText = 'width:100%;height:100%;border:none;display:block';
    frame.addEventListener('load', function () {
      if (innerFrame !== frame) return;
      attestedAt = 0;
      discardPending();
      pendingChallenge = mintNonce();
      frame.contentWindow.postMessage(
        { jsonrpc: '2.0', method: CHALLENGE_METHOD, params: { challenge: pendingChallenge } }, '*');
    });
    document.body.appendChild(innerFrame);
  }

  function findTagEnd(html, at) {
    var quote = '';
    for (var i = at + 1; i < html.length; i += 1) {
      var ch = html[i];
      if (quote) { if (ch === quote) { quote = ''; } continue; }
      if (ch === '"' || ch === "'") { quote = ch; continue; }
      if (ch === '>') { return i; }
    }
    return -1;
  }

  function isTagAt(lower, at, name) {
    if (lower.substr(at, name.length + 1) !== '<' + name) return false;
    var after = lower[at + name.length + 1];
    return after === undefined || after === '>' || after === '/' || /\s/.test(after);
  }

  function findDocumentInjectionPoint(html) {
    var lower = html.toLowerCase();
    var at = 0;
    var fallbackAt = 0;
    var sawDoctype = false;
    var sawHtml = false;
    if (lower.charCodeAt(0) === 0xfeff) { at = 1; fallbackAt = 1; }
    while (at < lower.length) {
      while (at < lower.length && /\s/.test(lower[at])) { at += 1; }
      fallbackAt = at;
      if (at >= lower.length) return fallbackAt;
      if (lower.substr(at, 4) === '<!--') {
        var close = lower.indexOf('-->', at + 4);
        if (close === -1) return fallbackAt;
        at = close + 3;
        fallbackAt = at;
        continue;
      }
      if (!sawDoctype && !sawHtml && isTagAt(lower, at, '!doctype')) {
        var end = findTagEnd(lower, at);
        if (end === -1) return fallbackAt;
        sawDoctype = true;
        at = end + 1;
        fallbackAt = at;
        continue;
      }
      if (!sawHtml && isTagAt(lower, at, 'html')) {
        var htmlEnd = findTagEnd(lower, at);
        if (htmlEnd === -1) return fallbackAt;
        sawHtml = true;
        at = htmlEnd + 1;
        fallbackAt = at;
        continue;
      }
      if (isTagAt(lower, at, 'head')) {
        var headEnd = findTagEnd(lower, at);
        return headEnd === -1 ? fallbackAt : headEnd + 1;
      }
      return fallbackAt;
    }
    return fallbackAt;
  }

  function injectIntoHead(html, injection) {
    var at = findDocumentInjectionPoint(html);
    return html.slice(0, at) + injection + html.slice(at);
  }

  notifyReady();
})();
""".trimStart()
}
