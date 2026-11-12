package com.garfiec.librechat.feature.chat.components.artifact

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Inline artifact previews are capped to this fraction of the current window
 * height. The full artifact remains available via the [ArtifactViewer]
 * on tap; the inline view is a "card preview" of the top portion. The cap also
 * keeps the Android software-layer WebView's backing bitmap inside the
 * paintable range — see `InlineArtifactView.android.kt` for the rendering
 * rationale.
 */
internal const val INLINE_MAX_HEIGHT_FRACTION = 0.7f

/** The smallest a content-fitted inline preview gets, and its height until the page first reports. */
internal val INLINE_FIT_MIN_HEIGHT: Dp = 96.dp

/** The Android `addJavascriptInterface` name [CONTENT_HEIGHT_REPORTER_JS] posts to. */
internal const val CONTENT_HEIGHT_ANDROID_BRIDGE = "LcHeightBridge"

/** The iOS `WKScriptMessageHandler` name [CONTENT_HEIGHT_REPORTER_JS] posts to. */
internal const val CONTENT_HEIGHT_IOS_HANDLER = "lcHeight"

/**
 * Reports the page body's height in CSS px whenever it changes, to whichever platform bridge is
 * present. Injected by the platform rather than written into the document, so the office preview
 * HTML stays exactly what its security gate built.
 *
 * Measures the body, not the document element: the document is never shorter than the WebView, so
 * it could only ever grow. Readings are skipped while a server loading placeholder is up, because
 * it is sized in `vh` and would shrink with the view it sizes.
 */
internal const val CONTENT_HEIGHT_REPORTER_JS: String = """
(function () {
  if (window.__lcHeightReporter) return;
  window.__lcHeightReporter = true;
  function post(h) {
    if (window.$CONTENT_HEIGHT_ANDROID_BRIDGE) {
      window.$CONTENT_HEIGHT_ANDROID_BRIDGE.report(h);
    } else if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.$CONTENT_HEIGHT_IOS_HANDLER) {
      window.webkit.messageHandlers.$CONTENT_HEIGHT_IOS_HANDLER.postMessage(h);
    }
  }
  var last = -1;
  var pending = false;
  function measure() {
    pending = false;
    var b = document.body;
    if (!b || document.querySelector('.lc-docx-loading, .lc-pptx-loading')) return;
    var s = getComputedStyle(b);
    var h = Math.ceil(Math.max(b.scrollHeight, b.getBoundingClientRect().height) +
      (parseFloat(s.marginTop) || 0) + (parseFloat(s.marginBottom) || 0));
    if (h > 0 && h !== last) { last = h; post(h); }
  }
  function schedule() {
    if (pending) return;
    pending = true;
    requestAnimationFrame(measure);
  }
  measure();
  if (window.ResizeObserver && document.body) new ResizeObserver(schedule).observe(document.body);
  new MutationObserver(schedule).observe(document.documentElement, { childList: true, subtree: true });
  window.addEventListener('load', schedule);
})();
"""

/**
 * The slot height for a page that reported [contentHeightCssPx], between [INLINE_FIT_MIN_HEIGHT]
 * and [maxHeight]. One CSS px is one dp: every document routed here sets a device-width viewport.
 * Pure — unit-tested.
 */
internal fun fitInlineHeight(contentHeightCssPx: Float, maxHeight: Dp): Dp {
    val min = INLINE_FIT_MIN_HEIGHT.coerceAtMost(maxHeight)
    return contentHeightCssPx.dp.coerceIn(min, maxHeight)
}
