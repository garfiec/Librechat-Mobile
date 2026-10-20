package com.garfiec.librechat.feature.chat.components.artifact

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import com.garfiec.librechat.core.ui.theme.isSurfaceDark
import com.garfiec.librechat.feature.chat.components.web.loadVendoredHtml
import com.garfiec.librechat.feature.chat.components.web.rememberWebAssetBaseUrl
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import kotlinx.cinterop.useContents
import platform.Foundation.NSNumber
import platform.UIKit.UIScreen
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

/**
 * iOS inline artifact preview. The slot is the inline cap for every type
 * routed here (HTML, React, uncached Mermaid) unless `fitContent` sizes it to
 * the page — matching Android.
 * SVG and cached Mermaid take the synchronous-aspect-ratio path via
 * `InlineSvgSurface` upstream; Markdown renders natively. iOS does not
 * participate in the mermaid SVG cache (Coil 3 on Apple uses Skia SVGDOM, not
 * AndroidSVG — fidelity is unverified). Full content lives in the fullscreen
 * [ArtifactPanel] on tap.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun InlineArtifactView(
    artifact: Artifact,
    onTap: () -> Unit,
    modifier: Modifier,
    fitContent: Boolean,
    header: (@Composable () -> Unit)?,
) {
    val isDarkTheme = isSurfaceDark()
    val html = remember(artifact, isDarkTheme) {
        ArtifactWebContent.buildHtml(artifact.content, artifact.type, isDarkTheme, inline = true)
    }

    // Match the Android cap (see InlineArtifactSizing.kt). On iOS, points map
    // 1:1 to Compose Dp, so UIScreen.mainScreen.bounds.size.height is already
    // in the unit we want.
    val maxInlineHeightDp = remember {
        UIScreen.mainScreen.bounds.useContents { size.height * INLINE_MAX_HEIGHT_FRACTION }.dp
    }
    var contentHeight by remember { mutableStateOf<Float?>(null) }
    val slotHeight: Dp = if (fitContent) {
        fitInlineHeight(contentHeight ?: 0f, maxInlineHeightDp)
    } else {
        maxInlineHeightDp
    }

    val assetBase = rememberWebAssetBaseUrl() ?: return
    val navigationPolicy = remember { LocalOnlyNavigationPolicy() }

    val uiKitViewBlock: @Composable () -> Unit = {
        var loadedHtml by remember { mutableStateOf("") }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(2.dp),
        ) {
            UIKitView(
                modifier = Modifier.fillMaxWidth().height(slotHeight),
                factory = {
                    val config = WKWebViewConfiguration().apply {
                        defaultWebpagePreferences.allowsContentJavaScript = true
                        if (fitContent) installContentHeightReporter(userContentController) { contentHeight = it }
                    }
                    val webView = WKWebView(frame = cValue { }, configuration = config)
                    webView.setOpaque(false)
                    webView.scrollView.setScrollEnabled(false)
                    webView.navigationDelegate = navigationPolicy
                    webView.loadVendoredHtml(html, assetBase)
                    loadedHtml = html
                    webView
                },
                update = { webView ->
                    if (html != loadedHtml) {
                        webView.loadVendoredHtml(html, assetBase)
                        loadedHtml = html
                    }
                },
                onRelease = { webView ->
                    webView.stopLoading()
                    if (fitContent) {
                        // The content controller retains its handler, which retains the state it writes.
                        webView.configuration.userContentController
                            .removeScriptMessageHandlerForName(CONTENT_HEIGHT_IOS_HANDLER)
                    }
                },
            )
        }
    }

    ArtifactCardSurface(onTap = onTap, modifier = modifier) {
        Column {
            header?.invoke()
            uiKitViewBlock()
        }
    }
}

/**
 * Runs [CONTENT_HEIGHT_REPORTER_JS] in every page [controller] loads, once the document is parsed,
 * and hands its readings to [onHeight]. WebKit delivers script messages on the main thread.
 */
private fun installContentHeightReporter(controller: WKUserContentController, onHeight: (cssPx: Float) -> Unit) {
    controller.addScriptMessageHandler(ContentHeightMessageHandler(onHeight), CONTENT_HEIGHT_IOS_HANDLER)
    controller.addUserScript(
        WKUserScript(
            source = CONTENT_HEIGHT_REPORTER_JS,
            injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentEnd,
            forMainFrameOnly = true,
        ),
    )
}

private class ContentHeightMessageHandler(
    private val onHeight: (cssPx: Float) -> Unit,
) : NSObject(), WKScriptMessageHandlerProtocol {
    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage,
    ) {
        val height = (didReceiveScriptMessage.body as? NSNumber)?.doubleValue ?: return
        if (height > 0) onHeight(height.toFloat())
    }
}
