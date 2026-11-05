package com.garfiec.librechat.feature.chat.components.mcpapp

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The native end of a relay page's bridge: the page calls `__sb_post(msg)` (defined by [prelude],
 * which goes first in the page's inline script) and native answers with `__sb_fromHost(json)`
 * through [send]. Installed before the page loads; [release] when the web view goes.
 *
 * Two transports, chosen by what the WebView supports:
 * - `WebViewCompat.addWebMessageListener`, scoped to the relay's own origin and main frame. The
 *   App's opaque frame never matches the origin rule, so it never sees the object.
 * - Without `WEB_MESSAGE_LISTENER` (a WebView older than 83, which an un-updated or Play-less
 *   device can still run), `addJavascriptInterface`. Android injects that object into every frame,
 *   the App's included, and gives native no frame or origin to check. So it exposes one method
 *   that takes a string, and only a call carrying [token], a random value only the relay's own
 *   inline script holds, is passed on. The App's frame is cross-origin to the relay and can read
 *   neither its globals nor its source, so the token confines the object to the relay as the origin
 *   rule does. Nothing else of native is reachable from it.
 */
internal class RelayBridge private constructor(
    val prelude: String,
    private val sender: (String) -> Unit,
    private val teardown: () -> Unit,
) {
    @Volatile
    private var released = false

    fun send(raw: String) {
        if (!released) sender(raw)
    }

    fun release() {
        if (released) return
        released = true
        teardown()
    }

    companion object {
        /**
         * Installs the bridge on [webView] for a relay page loaded at [origin] (its base URL's
         * origin). [onMessage] gets each message the relay posted, on the main thread.
         */
        @SuppressLint("JavascriptInterface", "AddJavascriptInterface")
        fun install(
            webView: WebView,
            name: String,
            origin: String,
            onMessage: (String) -> Unit,
        ): RelayBridge {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                var reply: ((String) -> Unit)? = null
                WebViewCompat.addWebMessageListener(webView, name, setOf(origin)) { _, message, _, isMainFrame, replyProxy ->
                    if (!isMainFrame) return@addWebMessageListener
                    reply = { raw -> replyProxy.postMessage(raw) }
                    message.data?.let(onMessage)
                }
                return RelayBridge(
                    prelude = listenerPrelude(name),
                    sender = { raw -> reply?.invoke(raw) },
                    teardown = { WebViewCompat.removeWebMessageListener(webView, name) },
                )
            }
            val gate = RelayBridgeGate(newToken())
            val main = Handler(Looper.getMainLooper())
            var bridge: RelayBridge? = null
            webView.addJavascriptInterface(
                FallbackInterface(gate) { raw -> main.post { if (bridge?.released == false) onMessage(raw) } },
                name,
            )
            return RelayBridge(
                prelude = fallbackPrelude(name, gate.token),
                // Main frame only: evaluateJavascript runs in the top document, the relay's.
                sender = { raw ->
                    main.post {
                        webView.evaluateJavascript("window.__sb_fromHost(${McpAppRelayPage.inlineScriptString(raw)})", null)
                    }
                },
                teardown = { webView.removeJavascriptInterface(name) },
            ).also { bridge = it }
        }

        private fun listenerPrelude(name: String) = """
            var __sb_bridge = window.$name;
            function __sb_post(m) { __sb_bridge.postMessage(JSON.stringify(m)); }
            __sb_bridge.onmessage = function (e) { window.__sb_fromHost(e.data); };
        """.trimIndent()

        /** The token lives only in this closure: no global names it. */
        private fun fallbackPrelude(name: String, token: String) = """
            var __sb_post = (function (b, t) {
              return function (m) { b.postMessage(t, JSON.stringify(m)); };
            })(window.$name, ${McpAppRelayPage.inlineScriptString(token)});
        """.trimIndent()

        private fun newToken(): String {
            val bytes = ByteArray(TOKEN_BYTES)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        }

        private const val TOKEN_BYTES = 32
    }
}

/** Admits a fallback call only with the relay's token, compared in constant time. */
internal class RelayBridgeGate(val token: String) {
    fun admits(candidate: String?): Boolean =
        candidate != null && MessageDigest.isEqual(candidate.encodeToByteArray(), token.encodeToByteArray())
}

/** The one method the fallback exposes. Called on a WebView thread. */
private class FallbackInterface(private val gate: RelayBridgeGate, private val forward: (String) -> Unit) {
    @JavascriptInterface
    fun postMessage(token: String?, json: String?) {
        if (json == null || !gate.admits(token)) return
        forward(json)
    }
}
