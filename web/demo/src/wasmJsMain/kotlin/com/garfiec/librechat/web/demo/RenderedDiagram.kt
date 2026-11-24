package com.garfiec.librechat.web.demo

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlin.io.encoding.Base64
import kotlin.js.Promise

/**
 * A ```mermaid block, drawn as an image. Mermaid runs in an off-screen frame that turns its SVG into a
 * PNG: a live frame would sit above the canvas and cover the bars and menus drawn over the messages.
 * Until the image arrives, and if Mermaid fails, the block shows its source.
 */
@Composable
internal fun RenderedDiagram(code: String, modifier: Modifier) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val key = "$isDark:$code"
    var image by remember(key) { mutableStateOf(DiagramImages[key]) }
    LaunchedEffect(key) {
        if (image != null) return@LaunchedEffect
        val rendered = runCatching { renderDiagram(code, isDark) }.getOrNull() ?: return@LaunchedEffect
        DiagramImages[key] = rendered
        image = rendered
    }
    val shown = image
    if (shown == null) {
        Text(code, modifier = modifier, style = MaterialTheme.typography.bodySmall)
        return
    }
    // Natural size, no taller than the app's inline diagrams (MERMAID_MAX_INLINE_HEIGHT). A wide
    // diagram shrinks to the message's width but no smaller than MIN_SCALE, and scrolls sideways past it.
    val aspect = shown.width.toFloat() / shown.height
    val natural = minOf(shown.width.toFloat() / RASTER_SCALE, MAX_HEIGHT_DP * aspect)
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width = minOf(natural, maxOf(maxWidth.value, natural * MIN_SCALE))
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
            Image(
                bitmap = shown,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(width.dp).aspectRatio(aspect),
            )
        }
    }
}

// Rendered diagrams by theme and source, so scrolling a chat back and forth doesn't redraw them.
private val DiagramImages = HashMap<String, ImageBitmap>()

private const val RASTER_SCALE = 2
private const val MAX_HEIGHT_DP = 300f
private const val MIN_SCALE = 0.75f

private suspend fun renderDiagram(code: String, isDark: Boolean): ImageBitmap {
    val web = window.location.href.substringBefore('#').substringBeforeLast('/') + "/web/"
    val source = Base64.encode(code.encodeToByteArray())
    val dataUrl = rasterize(diagramPage(web, source, if (isDark) "dark" else "default")).await<JsString>().toString()
    val png = Base64.decode(dataUrl.substringAfter("base64,"))
    return org.jetbrains.skia.Image.makeFromEncoded(png).toComposeImageBitmap()
}

// Runs Mermaid on the source, draws its SVG on a canvas at RASTER_SCALE and posts the PNG back.
// The SVG keeps its labels as SVG text (htmlLabels off), so drawing it never taints the canvas.
private fun diagramPage(web: String, source: String, theme: String) = """
<!DOCTYPE html>
<html><head><base href="$web"><meta charset="utf-8"></head>
<body style="margin:0">
<script src="mermaid/mermaid.min.js"></script>
<script>
(async () => {
  const done = (m) => parent.postMessage(m, "*");
  try {
    const code = new TextDecoder().decode(Uint8Array.from(atob("$source"), (c) => c.charCodeAt(0)));
    mermaid.initialize({ startOnLoad: false, theme: "$theme", securityLevel: "strict", flowchart: { htmlLabels: false } });
    const { svg } = await mermaid.render("diagram", code);
    const doc = new DOMParser().parseFromString(svg, "image/svg+xml").documentElement;
    const box = doc.viewBox.baseVal;
    doc.setAttribute("width", box.width);
    doc.setAttribute("height", box.height);
    doc.removeAttribute("style");
    const image = new Image();
    image.onload = () => {
      const canvas = document.createElement("canvas");
      canvas.width = Math.ceil(box.width * $RASTER_SCALE);
      canvas.height = Math.ceil(box.height * $RASTER_SCALE);
      canvas.getContext("2d").drawImage(image, 0, 0, canvas.width, canvas.height);
      done({ png: canvas.toDataURL("image/png") });
    };
    image.onerror = () => done({ error: "image" });
    image.src = "data:image/svg+xml;charset=utf-8," + encodeURIComponent(new XMLSerializer().serializeToString(doc));
  } catch (e) {
    done({ error: String(e) });
  }
})();
</script>
</body></html>
"""

// Loads the page in a frame that is laid out but off screen (Mermaid measures its labels), and
// resolves with the PNG it posts back; only messages from that frame count. The frame keeps the
// site's origin so Mermaid comes from the site's cache after the first diagram, which is safe only
// because every diagram here is the demo's own sample (see ArtifactFrame).
@Suppress("UnusedParameter")
private fun rasterize(page: String): Promise<JsString> = js(
    """new Promise((resolve, reject) => {
    const frame = document.createElement('iframe');
    frame.setAttribute('sandbox', 'allow-scripts allow-same-origin');
    frame.setAttribute('aria-hidden', 'true');
    frame.style.cssText = 'position:fixed;left:-10000px;top:0;width:1200px;height:800px;border:0;';
    const listener = (event) => {
        if (event.source !== frame.contentWindow) return;
        window.removeEventListener('message', listener);
        frame.remove();
        if (event.data && event.data.png) resolve(event.data.png); else reject(new Error(String(event.data && event.data.error)));
    };
    window.addEventListener('message', listener);
    frame.srcdoc = page;
    document.body.appendChild(frame);
    })""",
)
