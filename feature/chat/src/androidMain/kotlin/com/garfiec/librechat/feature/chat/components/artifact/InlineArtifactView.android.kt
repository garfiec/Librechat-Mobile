package com.garfiec.librechat.feature.chat.components.artifact

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.ui.theme.isSurfaceDark
import com.garfiec.librechat.feature.chat.components.web.configureLazyListWebView
import com.garfiec.librechat.feature.chat.components.web.rememberWebAssetBaseUrl
import com.garfiec.librechat.feature.chat.components.web.safelyDestroyWebView

/**
 * Android inline artifact preview. The slot is hardcoded to the inline cap for
 * every type routed here (HTML, React, uncached Mermaid). SVG and cached
 * Mermaid take the synchronous-aspect-ratio path via `InlineSvgSurface`
 * upstream; Markdown renders natively. Full content lives in the fullscreen
 * [ArtifactPanel] on tap. A fixed slot is the only configuration we've
 * measured that fully eliminates LazyColumn scroll-jump for WebView-hosted
 * previews; `fitContent` (office previews) trades one resize per card for not
 * leaving most of a short document's card empty.
 */
@Composable
actual fun InlineArtifactView(
    artifact: Artifact,
    onTap: () -> Unit,
    modifier: Modifier,
    fitContent: Boolean,
    header: (@Composable () -> Unit)?,
) {
    val isDarkTheme = isSurfaceDark()
    val bgArgb = MaterialTheme.colorScheme.surface.toArgb()
    val html = remember(artifact, isDarkTheme) {
        ArtifactWebContent.buildHtml(artifact.content, artifact.type, isDarkTheme, inline = true)
    }
    var loadedHtml by remember { mutableStateOf("") }
    var contentHeight by remember { mutableStateOf<Float?>(null) }

    val assetBase = rememberWebAssetBaseUrl() ?: return

    val cache = LocalMermaidRenderCache.current

    val mermaidKey: String? = remember(artifact.content, artifact.type, isDarkTheme) {
        if (ArtifactType.from(artifact.type) == ArtifactType.MERMAID &&
            isCacheableMermaid(artifact.content)
        ) {
            mermaidCacheKey(artifact.content, isDarkTheme)
        } else {
            null
        }
    }

    // Inline previews are capped to ~70% of the current window so a long HTML /
    // React doc can never claim multiple screens of LazyColumn space (the full
    // content lives in the fullscreen ArtifactPanel on tap). A second reason
    // the cap matters on Android: `LAYER_TYPE_SOFTWARE` on the inline WebView
    // (see `WebViewSafeDestroy.kt`) allocates a backing bitmap sized to the
    // WebView's full layout height — an unbounded 380×4500 dp layout produces
    // either a paint-skipping software-bitmap-too-large failure or an OOM that
    // shows up as a blank card. Capping the layout keeps the bitmap small.
    val maxInlineHeightDp = (LocalConfiguration.current.screenHeightDp * INLINE_MAX_HEIGHT_FRACTION).dp
    val slotHeight: Dp = if (fitContent) {
        fitInlineHeight(contentHeight ?: 0f, maxInlineHeightDp)
    } else {
        maxInlineHeightDp
    }

    val androidViewBlock: @Composable () -> Unit = {
        AndroidView(
            modifier = Modifier
                .padding(2.dp)
                .fillMaxWidth()
                .height(slotHeight),
            factory = { context ->
                createInlineWebView(
                    context = context,
                    bgArgb = bgArgb,
                    // Must be bound before loadDataWithBaseURL so MermaidBridge is
                    // bound when mermaid's IIFE executes.
                    mermaidReceiver = mermaidKey?.let { MermaidBridgeReceiver(cache, it) },
                    onContentHeight = if (fitContent) ({ contentHeight = it }) else null,
                ).apply {
                    loadDataWithBaseURL(assetBase, html, "text/html", "UTF-8", null)
                    loadedHtml = html
                }
            },
            update = { webView ->
                webView.setBackgroundColor(bgArgb)
                if (html != loadedHtml) {
                    webView.loadDataWithBaseURL(assetBase, html, "text/html", "UTF-8", null)
                    loadedHtml = html
                }
            },
            onRelease = ::safelyDestroyWebView,
        )
    }

    ArtifactCardSurface(onTap = onTap, modifier = modifier) {
        Column {
            header?.invoke()
            if (mermaidKey != null) key(mermaidKey) { androidViewBlock() } else androidViewBlock()
        }
    }
}

private val LOCAL_NAVIGATION_SCHEMES = setOf("about", "data", "blob", "file")

/**
 * SECURITY: whether to refuse [request], a navigation started from a preview. A link in one (a docx
 * hyperlink, an HTML artifact's <a>) would otherwise replace the page with a remote one, running its
 * script with any bridges bound. A form POST never reaches `shouldOverrideUrlLoading`; the
 * artifact CSPs' `form-action 'none'` covers it.
 */
internal fun isRemoteMainFrameNavigation(request: WebResourceRequest): Boolean =
    request.isForMainFrame && request.url.scheme?.lowercase() !in LOCAL_NAVIGATION_SCHEMES

/**
 * A WebView configured for an inline preview. With [onContentHeight], the page reports its body
 * height through [ContentHeightJsBridge] once it has loaded and whenever it changes after.
 */
@SuppressLint("SetJavaScriptEnabled")
private fun createInlineWebView(
    context: Context,
    bgArgb: Int,
    mermaidReceiver: MermaidBridgeReceiver?,
    onContentHeight: ((cssPx: Float) -> Unit)?,
): WebView = WebView(context).apply {
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
    configureLazyListWebView(this)
    setBackgroundColor(bgArgb)
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.loadWithOverviewMode = true
    settings.useWideViewPort = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    isVerticalScrollBarEnabled = false
    isHorizontalScrollBarEnabled = false
    webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            if (onContentHeight != null) view.evaluateJavascript(CONTENT_HEIGHT_REPORTER_JS, null)
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            isRemoteMainFrameNavigation(request)

        override fun onReceivedSslError(
            view: WebView?,
            handler: SslErrorHandler?,
            error: SslError?,
        ) {
            handler?.cancel()
            Logger.w { "SSL error in inline artifact WebView: ${error?.primaryError}" }
        }
    }
    if (mermaidReceiver != null) {
        addJavascriptInterface(MermaidJsBridge(mermaidReceiver), "MermaidBridge")
    }
    if (onContentHeight != null) {
        addJavascriptInterface(ContentHeightJsBridge(onContentHeight), CONTENT_HEIGHT_ANDROID_BRIDGE)
    }
}
