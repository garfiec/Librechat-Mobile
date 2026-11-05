package com.garfiec.librechat.feature.chat.components.mcpapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import co.touchlab.kermit.Logger
import com.garfiec.librechat.feature.chat.components.web.configureLazyListWebView
import com.garfiec.librechat.feature.chat.components.web.safelyDestroyWebView
import com.garfiec.librechat.feature.chat.viewmodel.McpAppSession

/** A picture of an inline App is kept at this fraction of its size: it is only shown dimmed. */
private const val SNAPSHOT_SCALE = 0.5f

/**
 * Pooled App web views. Every view is software-rendered ([configureLazyListWebView]): a view that
 * detaches when its card scrolls away is exactly the recycle that crashes the hardware (GL functor)
 * path, and the same view moves into the panel on Expand, so the panel is software too.
 */
private class AndroidMcpAppViewPool(
    /** The Activity's, never a dialog's: a view outlives the panel's window. */
    private val context: Context,
    private val onViewLost: (Long) -> Unit,
) : McpAppViewPool {
    private val views = HashMap<Long, PooledView>()
    private val snapshots = HashMap<String, ImageBitmap>()

    private class PooledView(val webView: WebView, val bridge: RelayBridge, val session: McpAppSession) {
        var lastPicture: ImageBitmap? = null
    }

    fun attach(container: FrameLayout, session: McpAppSession, page: McpAppPage) {
        val pooled = views[session.id] ?: create(session, page).also { views[session.id] = it }
        val view = pooled.webView
        if (view.parent === container) return
        (view.parent as? ViewGroup)?.removeView(view)
        container.removeAllViews()
        container.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        snapshots.remove(session.occurrence.key)
    }

    /** The container is going: keep a picture (inline only) and let the view go, still running. */
    fun detach(container: FrameLayout, keepPicture: Boolean) {
        val view = container.getChildAt(0) as? WebView
        if (view != null && keepPicture) {
            views.values.firstOrNull { it.webView === view }?.let { it.lastPicture = picture(view) ?: it.lastPicture }
        }
        container.removeAllViews()
    }

    private fun picture(view: WebView): ImageBitmap? = runCatching {
        if (view.width <= 0 || view.height <= 0) return null
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(full))
        val scaled = full.scale(
            (view.width * SNAPSHOT_SCALE).toInt().coerceAtLeast(1),
            (view.height * SNAPSHOT_SCALE).toInt().coerceAtLeast(1),
        )
        if (scaled !== full) full.recycle()
        scaled.asImageBitmap()
    }.getOrNull()

    override fun retain(sessionIds: Set<Long>, evicted: Set<String>) {
        views.keys.filter { it !in sessionIds }.forEach { id ->
            views.remove(id)?.let { pooled ->
                val key = pooled.session.occurrence.key
                pooled.lastPicture?.takeIf { key in evicted }?.let { snapshots[key] = it }
                destroy(pooled)
            }
        }
        snapshots.keys.retainAll(evicted)
    }

    override fun snapshot(occurrenceKey: String): ImageBitmap? = snapshots[occurrenceKey]

    fun destroyAll() {
        views.values.forEach(::destroy)
        views.clear()
        snapshots.clear()
    }

    private fun destroy(pooled: PooledView) {
        pooled.session.host.send = null
        (pooled.webView.parent as? ViewGroup)?.removeView(pooled.webView)
        pooled.bridge.release()
        safelyDestroyWebView(pooled.webView)
    }

    private fun lost(view: WebView) {
        val entry = views.entries.firstOrNull { it.value.webView === view } ?: return
        views.remove(entry.key)
        destroy(entry.value)
        onViewLost(entry.key)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(session: McpAppSession, page: McpAppPage): PooledView {
        val host = session.host
        val webView = WebView(context).apply {
            configureLazyListWebView(this)
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportMultipleWindows(false)
            settings.javaScriptCanOpenWindowsAutomatically = false
            // Default onCreateWindow refuses every new window. A location request reaches here
            // only from a frame whose `allow` names geolocation, which the host grants per server.
            webChromeClient = object : WebChromeClient() {
                private var pendingLocation: LocationPromptWithdrawal? = null

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?,
                ) {
                    // The opaque App frame asks as origin "". Never retained: the next page asks again.
                    pendingLocation = host.onLocationPrompt { allowed ->
                        pendingLocation = null
                        callback?.invoke(origin, allowed, false)
                    }
                }

                // The page went away or cancelled: its consent dialog must not stay up to be answered.
                override fun onGeolocationPermissionsHidePrompt() {
                    pendingLocation?.withdraw()
                    pendingLocation = null
                }
            }
            webViewClient = object : WebViewClient() {
                // The page is loaded once with loadDataWithBaseURL; nothing may replace it.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.isForMainFrame

                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    handler?.cancel()
                    Logger.w { "SSL error in MCP app WebView: ${error?.primaryError}" }
                }

                // Unhandled, a dead renderer takes the whole app down with it.
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail?): Boolean {
                    Logger.w { "MCP app renderer gone (crashed=${detail?.didCrash()})" }
                    lost(view)
                    return true
                }
            }
        }
        // Before the load, so the bridge exists when the relay's first line runs.
        val bridge = RelayBridge.install(webView, McpAppPage.BRIDGE_NAME, McpAppPage.ORIGIN, host::onRelayMessage)
        host.send = bridge::send
        host.pageAttached()
        webView.loadDataWithBaseURL(
            McpAppPage.BASE_URL,
            McpAppRelayPage.inlinePage(page, bridge.prelude),
            "text/html",
            "UTF-8",
            null,
        )
        return PooledView(webView, bridge, session)
    }
}

@Composable
internal actual fun rememberMcpAppViewPool(onMemoryPressure: () -> Unit, onRendererExit: (sessionId: Long) -> Unit): McpAppViewPool {
    val context = LocalContext.current
    val lostCallback = rememberUpdatedState(onRendererExit)
    val pressureCallback = rememberUpdatedState(onMemoryPressure)
    val pool = remember(context) { AndroidMcpAppViewPool(context, onViewLost = { lostCallback.value(it) }) }
    DisposableEffect(pool) {
        val callbacks = object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                // Only delivered on API < 34; onLowMemory covers newer releases.
                @Suppress("DEPRECATION")
                if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) pressureCallback.value()
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() {
                pressureCallback.value()
            }
        }
        context.applicationContext.registerComponentCallbacks(callbacks)
        onDispose {
            context.applicationContext.unregisterComponentCallbacks(callbacks)
            pool.destroyAll()
        }
    }
    return pool
}

@Composable
internal actual fun rememberIsLowRamDevice(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
    }
}

@Composable
internal actual fun McpAppWebView(
    session: McpAppSession,
    page: McpAppPage,
    pool: McpAppViewPool,
    inline: Boolean,
    modifier: Modifier,
) {
    val androidPool = pool as AndroidMcpAppViewPool
    val keepPicture = rememberUpdatedState(inline)
    // A container, not the pooled view itself: a lazy list reuses the AndroidView node, and the
    // pooled view moves between containers (thread and panel) with removeView/addView.
    AndroidView(
        modifier = modifier,
        factory = { context -> FrameLayout(context) },
        update = { container -> androidPool.attach(container, session, page) },
        onReset = { container -> androidPool.detach(container, keepPicture.value) },
        onRelease = { container -> androidPool.detach(container, keepPicture.value) },
    )
}

@Composable
internal actual fun rememberIsRecreatingUi(): () -> Boolean {
    val activity = LocalActivity.current
    return remember(activity) { { activity?.isChangingConfigurations == true } }
}

internal actual val mcpAppLocationSupported: Boolean = true

@Composable
internal actual fun rememberCoarseLocationRequester(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val report = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        report.value(granted)
    }
    return remember(context, launcher) {
        {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) report.value(true) else launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
}
