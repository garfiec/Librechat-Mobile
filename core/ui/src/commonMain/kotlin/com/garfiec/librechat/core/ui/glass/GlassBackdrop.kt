package com.garfiec.librechat.core.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import com.garfiec.librechat.core.model.ui.GlassCapability
import com.garfiec.librechat.core.ui.theme.LocalGlassLevel
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * The content glass surfaces sample. Opaque on purpose: the backdrop library is an implementation
 * detail of this module, so no feature can bypass the Material/flat gating below by reaching for it.
 */
@Stable
class GlassBackdrop internal constructor(internal val layer: LayerBackdrop)

/** The backdrop that glass chrome inside a screen samples; null means draw without sampling. */
val LocalGlassBackdrop = compositionLocalOf<GlassBackdrop?> { null }

/**
 * Null in Material and on the flat tier: recording re-draws the content every frame, so those users
 * would pay for an effect they never see.
 */
@Composable
fun rememberGlassBackdrop(): GlassBackdrop? {
    val level = LocalGlassLevel.current
    if (level == null || level == GlassCapability.FLAT) return null
    // The recorded content is usually transparent (a list whose screen colour an ancestor paints),
    // and a blurred copy of transparent content lets the sharp original show straight through the
    // glass. Painting the screen colour first makes the sample opaque, like the real background.
    val background = MaterialTheme.colorScheme.background
    val onDraw: ContentDrawScope.() -> Unit = remember(background) {
        {
            drawRect(background)
            drawContent()
        }
    }
    val layer = rememberLayerBackdrop(onDraw = onDraw)
    return remember(layer) { GlassBackdrop(layer) }
}

/**
 * Records this node's content into [backdrop] for glass surfaces to sample. Apply it to the content
 * that scrolls *beside* the glass, never to an ancestor of the glass surface itself: a surface that
 * samples its own ancestor feeds its output back into its input.
 */
fun Modifier.glassBackdropSource(backdrop: GlassBackdrop?): Modifier =
    if (backdrop == null) this else layerBackdrop(backdrop.layer)

/**
 * Lays out [content] filling its parent and records it into [backdrop] while [recording]. Without a
 * backdrop it adds nothing — no size of its own, the parent's constraints pass straight through — so
 * a caller keeps one call site for its content, and switching style never re-creates its state.
 */
@Composable
internal fun GlassBackdropRecorder(
    backdrop: GlassBackdrop?,
    recording: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = if (backdrop == null) {
            Modifier
        } else {
            Modifier.fillMaxSize().glassBackdropSource(backdrop.takeIf { recording })
        },
        propagateMinConstraints = true,
    ) {
        content()
    }
}
