package com.garfiec.librechat.feature.chat.components.mcpapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import com.garfiec.librechat.feature.chat.viewmodel.McpAppSession

/**
 * The web views of the open Apps, owned above the message list (`rememberMcpApps`) so that an App
 * outlives the composable showing it: a card scrolling away only detaches its view, and Expand moves
 * the same live view into the panel. A view is built the first time its session is attached and
 * destroyed only by [retain] (its session closed) or when the pool leaves composition (leaving the
 * chat, or Android recreating its Activity, after which each App reloads in a new view).
 */
@Stable
internal interface McpAppViewPool {
    /**
     * Destroys every view whose session is not in [sessionIds], keeping a picture only of those
     * [evicted] names (occurrence keys).
     */
    fun retain(sessionIds: Set<Long>, evicted: Set<String>)

    /** The last picture of the inline App at [occurrenceKey], kept after it was evicted. */
    fun snapshot(occurrenceKey: String): ImageBitmap?
}

/**
 * The pool, with the platform's two reasons to give Apps up: [onMemoryPressure] (Android's critical
 * trim) and [onRendererExit] (a session's renderer died).
 */
@Composable
internal expect fun rememberMcpAppViewPool(onMemoryPressure: () -> Unit, onRendererExit: (sessionId: Long) -> Unit): McpAppViewPool

/** Android's low-RAM flag (`ActivityManager.isLowRamDevice`); false on iOS. */
@Composable
internal expect fun rememberIsLowRamDevice(): Boolean

/**
 * Attaches [session]'s web view from [pool], built on first use: it loads [page] at
 * [McpAppPage.BASE_URL], installs a bridge reachable only from that page's own main frame, and wires
 * it to the host both ways (`host.send` out, `host.onRelayMessage` in, `host.pageAttached` once per
 * new view, never on a reattach). Leaving composition only detaches it. [inline] keeps a picture of
 * it as it leaves, for the card to show if it is evicted.
 *
 * - Android (`RelayBridge`): `WebViewCompat.addWebMessageListener` scoped to [McpAppPage.ORIGIN],
 *   main frame only. On a WebView without `WEB_MESSAGE_LISTENER`, a single-method
 *   `addJavascriptInterface` object that passes on only calls carrying a per-page random token
 *   that only the relay's own script holds, since that object reaches every frame. Software layer
 *   in the thread and the panel alike: detaching on scroll is the recycle that crashes the GL path.
 * - iOS: the relay and its `WKScriptMessageHandler` live in a dedicated `WKContentWorld`, main frame
 *   only, and the handler also drops anything not from the main frame.
 *
 * Both refuse main-frame navigation off the page, new windows, and TLS errors.
 */
@Composable
internal expect fun McpAppWebView(
    session: McpAppSession,
    page: McpAppPage,
    pool: McpAppViewPool,
    inline: Boolean,
    modifier: Modifier = Modifier,
)

/**
 * Whether the composition is going away only because the platform is recreating its UI (Android's
 * Activity on rotation, fold or a theme change), so state held outside it should survive. Asked at
 * dispose time. iOS never recreates its UI for these, so it is always false there.
 */
@Composable
internal expect fun rememberIsRecreatingUi(): () -> Boolean

/**
 * Whether an App's frame can be given the device location here. Android's WebView takes a
 * delegated `allow="geolocation"` in the opaque App frame to its permission prompt (verified on
 * WebView 145). WebKit refuses geolocation to any opaque origin outright, so iOS never offers it.
 */
internal expect val mcpAppLocationSupported: Boolean

/**
 * Asks the OS for approximate location when it isn't granted yet, then reports whether it is.
 * Returns the trigger. Reports false at once where [mcpAppLocationSupported] is false.
 */
@Composable
internal expect fun rememberCoarseLocationRequester(onResult: (granted: Boolean) -> Unit): () -> Unit
