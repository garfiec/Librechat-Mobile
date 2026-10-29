package com.garfiec.librechat.core.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.GlassCapability
import com.garfiec.librechat.core.ui.theme.LocalDarkTheme
import com.garfiec.librechat.core.ui.theme.LocalGlassLevel
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow

private val BlurRadius = 12.dp
private val RefractionHeight = 12.dp
private val RefractionAmount = 24.dp

private const val HIGHLIGHT_ANGLE = 45f
private const val LIGHT_TINT_ALPHA = 0.40f
private const val DARK_TINT_ALPHA = 0.40f
private const val DARK_TINT_LIFT = 0.2f

internal val GlassStyle.refracts: Boolean
    get() = level != GlassCapability.FLAT && level != GlassCapability.BLUR_ONLY

internal fun tiltedHighlight(tilt: Float, alpha: Float = 1f): Highlight =
    Highlight.Default.copy(alpha = alpha, style = HighlightStyle.Default(angle = HIGHLIGHT_ANGLE + tilt))

/**
 * The theme-resolved values a glass surface draws with. Resolved once per composition by
 * [rememberGlassStyle] so [glassSurface] can stay an ordinary (non-composable) modifier.
 */
@Immutable
class GlassStyle internal constructor(
    internal val level: GlassCapability,
    /** The legibility floor over the sampled content, not a colour. */
    val tint: Color,
    internal val flatColor: Color,
    internal val hairline: Color,
    val accent: Color,
    internal val blurPx: Float,
    internal val refractionHeightPx: Float,
    internal val refractionAmountPx: Float,
)

/** Null in Material mode, so callers keep their Material branch exactly as it was. */
@Composable
fun rememberGlassStyle(): GlassStyle? {
    val level = LocalGlassLevel.current ?: return null
    val scheme = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val density = LocalDensity.current
    return remember(level, scheme, dark, density) {
        with(density) {
            GlassStyle(
                level = level,
                // Dark glass is lifted a little toward the text colour: a wash of the near-black dark
                // surface reads as flat black, where iOS dark glass is a faint grey.
                tint = if (dark) {
                    lerp(scheme.surface, scheme.onSurface, DARK_TINT_LIFT).copy(alpha = DARK_TINT_ALPHA)
                } else {
                    scheme.surface.copy(alpha = LIGHT_TINT_ALPHA)
                },
                flatColor = scheme.surfaceContainerHigh.copy(alpha = 0.94f),
                hairline = scheme.outlineVariant.copy(alpha = if (dark) 0.35f else 0.55f),
                accent = scheme.primary,
                blurPx = BlurRadius.toPx(),
                refractionHeightPx = RefractionHeight.toPx(),
                refractionAmountPx = RefractionAmount.toPx(),
            )
        }
    }
}

/**
 * A brief light along a glass surface's edge, as if something bright passed over it. [amount] is
 * read at draw time (0 draws nothing, 1 is full strength), so animating it only redraws.
 */
@Stable
class GlassFlare(val color: Color, val amount: () -> Float)

/**
 * Glass behind this node's content, degrading by tier; with no [backdrop], a translucent surface.
 * [highlightTilt] (degrees) is read at draw time, for a sheen that moves with a gesture. [flare]
 * lights the edge on every tier, the flat one included.
 *
 * [shape] is a [CornerBasedShape] because the refraction shader throws on any other shape.
 */
fun Modifier.glassSurface(
    style: GlassStyle,
    shape: CornerBasedShape,
    backdrop: GlassBackdrop?,
    flare: GlassFlare? = null,
    highlightTilt: (() -> Float)? = null,
): Modifier = glassSurfaceBase(style, shape, backdrop, highlightTilt).then(
    if (flare != null) Modifier.glassFlare(flare, shape) else Modifier,
)

private fun Modifier.glassFlare(flare: GlassFlare, shape: CornerBasedShape): Modifier = drawWithContent {
    val amount = flare.amount().coerceIn(0f, 1f)
    if (amount <= 0f) {
        drawContent()
        return@drawWithContent
    }
    // The wash goes under the content so it never tints the icons or text; the edge goes over it.
    drawOutline(
        shape.createOutline(size, layoutDirection, this),
        flare.color.copy(alpha = flare.color.alpha * amount * FLARE_FILL_ALPHA),
    )
    drawContent()
    drawInsetOutlineStroke(shape, flare.color.copy(alpha = flare.color.alpha * amount), FlareStroke.toPx())
}

/**
 * Strokes [shape]'s outline inset by half the stroke, as [border] does, so the whole line sits
 * inside the bounds instead of straddling (and, under a clip, losing half of) the edge.
 */
fun DrawScope.drawInsetOutlineStroke(shape: Shape, color: Color, strokePx: Float) {
    val half = strokePx / 2f
    inset(half) {
        drawOutline(shape.createOutline(size, layoutDirection, this), color, style = Stroke(width = strokePx))
    }
}

private val FlareStroke = 1.5.dp
private const val FLARE_FILL_ALPHA = 0.12f

private fun Modifier.glassSurfaceBase(
    style: GlassStyle,
    shape: CornerBasedShape,
    backdrop: GlassBackdrop?,
    highlightTilt: (() -> Float)?,
): Modifier {
    if (style.level == GlassCapability.FLAT || backdrop == null) {
        return background(style.flatColor, shape).border(GlassDefaults.Hairline, style.hairline, shape)
    }
    return drawBackdrop(
        backdrop = backdrop.layer,
        shape = { shape },
        // No vibrancy(): it alone made glass janky while streaming (see core/ui/CLAUDE.md). As the
        // first effect it also pads the blur layer by the blur radius.
        effects = {
            blur(style.blurPx)
            if (style.refracts) lens(style.refractionHeightPx, style.refractionAmountPx)
        },
        highlight = if (style.refracts) {
            { tiltedHighlight(highlightTilt?.invoke() ?: 0f) }
        } else {
            { Highlight.Plain }
        },
        // The default soft shadow is what separates a clear capsule from a plain background.
        shadow = { Shadow.Default },
        onDrawSurface = { drawRect(style.tint) },
    )
}
