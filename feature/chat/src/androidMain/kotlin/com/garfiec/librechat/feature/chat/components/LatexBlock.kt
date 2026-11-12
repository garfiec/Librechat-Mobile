package com.garfiec.librechat.feature.chat.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.ui.theme.chatColors
import com.garfiec.librechat.feature.chat.components.web.rememberWebAssetBaseUrl

/**
 * Escapes a LaTeX string for safe embedding inside a JavaScript string literal.
 */
private fun escapeForJs(latex: String): String {
    return latex
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("<", "\\u003c")
        .replace(">", "\\u003e")
}

/**
 * Converts an ARGB int color to a CSS rgba() string.
 */
private fun argbToCss(argb: Int): String {
    val a = ((argb shr 24) and 0xFF) / 255.0
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return "rgba($r, $g, $b, $a)"
}

/**
 * Builds the minimal HTML page that loads the bundled KaTeX and renders a LaTeX expression.
 *
 * `internal` rather than private so `VendoredAssetReferenceTest` can assert that every
 * referenced path is one the vendoring script actually ships — a wrong relative path here
 * renders a blank box with no error anywhere in Kotlin.
 *
 * @param escapedLatex The LaTeX string already escaped via [escapeForJs].
 * @param displayMode true for block/display mode (centered, large), false for inline mode.
 * @param textColorCss CSS color string for the rendered math text.
 */
internal fun buildKatexHtml(escapedLatex: String, displayMode: Boolean, textColorCss: String): String {
    return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'self' 'unsafe-inline' file:; style-src 'self' 'unsafe-inline' file:; font-src 'self' file: data:; img-src data:;">
<link rel="stylesheet" href="katex/katex.min.css">
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    background: transparent;
    color: $textColorCss;
    font-size: 18px;
    padding: 4px 0;
    ${if (displayMode) "text-align: center;" else "display: inline;"}
    overflow-x: auto;
    overflow-y: hidden;
  }
  .katex-display { margin: 0; padding: 0; }
  .katex { color: $textColorCss; }
  .katex-error { color: $textColorCss; font-family: monospace; font-size: 14px; }
</style>
</head>
<body>
<div id="math"></div>
<script src="katex/katex.min.js"></script>
<script>
try {
  katex.render("$escapedLatex", document.getElementById("math"), {
    displayMode: $displayMode,
    throwOnError: false,
    strict: false,
    trust: true
  });
} catch(e) {
  document.getElementById("math").textContent = "$escapedLatex";
}
// Report content height to Android for proper sizing
function reportHeight() {
  var h = document.body.scrollHeight;
  document.title = '' + h;
}
reportHeight();
new MutationObserver(reportHeight).observe(document.body, { childList: true, subtree: true });
</script>
</body>
</html>
    """.trimIndent()
}

// ── Plain-text renderers (lightweight, no WebView) ────────────────

@Composable
private fun NativeLatexBlock(
    latex: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = latex,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp,
        ),
        color = chatColors().messageText,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                color = chatColors().codeBody,
                shape = RoundedCornerShape(6.dp),
            )
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun NativeLatexInline(
    latex: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = latex,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
        ),
        color = chatColors().messageText,
        modifier = modifier
            .background(
                color = chatColors().codeBody,
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

// ── KaTeX (WebView) renderers ──────────────────────────────────────

/** Stores the page's content height in CSS px (0 until known). */
private fun WebView.readContentHeight(into: MutableIntState) {
    evaluateJavascript("document.body.scrollHeight") { heightStr ->
        heightStr?.toIntOrNull()?.takeIf { it > 0 }?.let { into.intValue = it }
    }
}

/**
 * Sizes the view to [height] CSS px, which are dp, converted at layout so a density change (a fold
 * between screens of different density) resizes it without recomposing, which would reload the page.
 */
private fun Modifier.cssHeight(height: MutableIntState): Modifier = layout { measurable, constraints ->
    val css = height.intValue
    val placeable = if (css > 0) {
        val px = css.dp.roundToPx().coerceIn(constraints.minHeight, constraints.maxHeight)
        measurable.measure(constraints.copy(minHeight = px, maxHeight = px))
    } else {
        measurable.measure(constraints)
    }
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun KatexLatexBlock(
    latex: String,
    modifier: Modifier = Modifier,
) {
    val textColorArgb = chatColors().messageText.toArgb()
    val textColorCss = remember(textColorArgb) { argbToCss(textColorArgb) }
    val escapedLatex = remember(latex) { escapeForJs(latex) }
    val html = remember(escapedLatex, textColorCss) {
        buildKatexHtml(escapedLatex, displayMode = true, textColorCss = textColorCss)
    }
    val assetBase = rememberWebAssetBaseUrl() ?: return
    val contentHeight = remember { mutableIntStateOf(0) }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .cssHeight(contentHeight),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.readContentHeight(contentHeight)
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?,
                    ) {
                        handler?.cancel()
                        Logger.w { "SSL error in LaTeX block WebView: ${error?.primaryError}" }
                    }
                }
                loadDataWithBaseURL(
                    assetBase,
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                assetBase,
                html,
                "text/html",
                "UTF-8",
                null,
            )
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        },
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun KatexLatexInline(
    latex: String,
    modifier: Modifier = Modifier,
) {
    val textColorArgb = chatColors().messageText.toArgb()
    val textColorCss = remember(textColorArgb) { argbToCss(textColorArgb) }
    val escapedLatex = remember(latex) { escapeForJs(latex) }
    val html = remember(escapedLatex, textColorCss) {
        buildKatexHtml(escapedLatex, displayMode = false, textColorCss = textColorCss)
    }
    val assetBase = rememberWebAssetBaseUrl() ?: return
    val contentHeight = remember { mutableIntStateOf(0) }

    AndroidView(
        modifier = modifier.cssHeight(contentHeight),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.readContentHeight(contentHeight)
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?,
                    ) {
                        handler?.cancel()
                        Logger.w { "SSL error in LaTeX inline WebView: ${error?.primaryError}" }
                    }
                }
                loadDataWithBaseURL(
                    assetBase,
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                assetBase,
                html,
                "text/html",
                "UTF-8",
                null,
            )
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        },
    )
}

// ── Public API ──────────────────────────────────────────────────────

/**
 * Renders a LaTeX math expression as a display/block element (for `$$...$$` or `\[...\]`).
 * When [useKatex] is true, renders via KaTeX WebView (full rendering).
 * When false, shows the raw LaTeX source as styled monospace text (fast, no WebView).
 */
@Composable
actual fun LatexBlock(
    latex: String,
    modifier: Modifier,
    useKatex: Boolean,
) {
    if (useKatex) {
        KatexLatexBlock(latex = latex, modifier = modifier)
    } else {
        NativeLatexBlock(latex = latex, modifier = modifier)
    }
}

/**
 * Renders a LaTeX math expression inline (for `$...$` or `\(...\)`).
 * When [useKatex] is true, renders via KaTeX WebView (full rendering).
 * When false, shows the raw LaTeX source as styled monospace text (fast, no WebView).
 */
@Composable
actual fun LatexInline(
    latex: String,
    modifier: Modifier,
    useKatex: Boolean,
) {
    if (useKatex) {
        KatexLatexInline(latex = latex, modifier = modifier)
    } else {
        NativeLatexInline(latex = latex, modifier = modifier)
    }
}
