package com.garfiec.librechat.feature.chat.components.veil

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.glass.GlassFlare
import com.garfiec.librechat.core.ui.platform.rememberReduceMotion
import com.garfiec.librechat.core.ui.theme.LocalDarkTheme
import com.garfiec.librechat.core.ui.theme.glassBlurSupported
import com.garfiec.librechat.core.ui.theme.isLiquidGlass
import com.garfiec.librechat.feature.chat.components.ChatInputDefaults
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * The temporary-chat transition: a violet band that sweeps out from the toggle when temporary chat
 * turns on, and back into it when it turns off. The band is what changes the screen. Every element
 * shows its temporary styling exactly when the band's peak has passed its centre, so an element
 * switches the moment the brightest part of the band reaches it, in both directions, and a second
 * tap mid-sweep simply retargets the same radius.
 *
 * Only a sweep the user started animates ([requestUserToggle]). Every other change of the flag
 * snaps: the first frame of a route-seeded temporary chat, and the landing's reset to a normal chat
 * when a new conversation navigates away from it.
 */
@Stable
class ModeVeilState internal constructor(
    initiallyOn: Boolean,
    internal val reduceMotion: Boolean,
    private val pxPerDp: Float,
) {
    /** 0 = normal everywhere, 1 = temporary everywhere; the band's radius is this × [maxRadius]. */
    internal val progress = Animatable(if (initiallyOn) 1f else 0f)

    internal var rootBounds by mutableStateOf(Rect.Zero)
    internal var origin by mutableStateOf(Offset.Unspecified)
    internal var fallbackOrigin by mutableStateOf(Offset.Unspecified)

    /** The flag value a user tap asked for; only a change to exactly this value sweeps. */
    internal var pendingUserTarget: Boolean? = null

    /** True while a sweep the user started is running; surfaces react to the band only then. */
    internal var sweeping by mutableStateOf(false)

    /** Which way the running (or last) sweep goes: true grows out of the toggle, false drains into it. */
    internal var sweepingOn by mutableStateOf(true)

    /** The style draws the band; Liquid Glass's is softer and wider. */
    internal var glass by mutableStateOf(false)

    /** Where the band starts, in root coordinates. */
    internal val resolvedOrigin: Offset
        get() = when {
            origin.isSpecified -> origin
            fallbackOrigin.isSpecified -> fallbackOrigin
            else -> rootBounds.topRight
        }

    internal val sweepDistance: Float
        get() = farthestCornerDistance(resolvedOrigin, rootBounds)

    /**
     * How far the band trails behind its leading edge. A share of the sweep rather than a fixed
     * size, so it reads the same on a phone and an unfolded Fold; never narrower than its dp floor.
     */
    internal val bandTail: Float
        get() = max(
            sweepDistance * (if (glass) GLASS_TAIL_SHARE else MATERIAL_TAIL_SHARE),
            (if (glass) GlassTailFloor else MaterialTailFloor).value * pxPerDp,
        )

    /** The band's soft edge ahead of its radius; Material's scales with the band so its front is soft too. */
    internal val bandLead: Float
        get() = if (glass) GlassLead.value * pxPerDp else bandTail * MATERIAL_LEAD_SHARE

    /** Where the band is strongest, as a fraction from its back to its front. */
    internal val bandPeakAt: Float
        get() = if (glass) GLASS_PEAK else MATERIAL_PEAK

    /** How far the band's peak sits behind its radius: what has to pass a surface to change it. */
    internal val peakLag: Float
        get() = bandPeakLag(bandTail, bandLead, bandPeakAt)

    /** Far enough that the whole band, tail included, has left the screen. */
    internal val maxRadius: Float
        get() = sweepDistance + bandTail + bandLead

    internal val radius: Float
        get() = progress.value * maxRadius

    /**
     * Marks the change of the flag to [to] as the user's, so it sweeps instead of snapping. Keyed on
     * the target so a request that never lands (two taps in one frame) can't make a later
     * programmatic change sweep.
     */
    fun requestUserToggle(to: Boolean) {
        pendingUserTarget = to
    }

    /** The toggle's centre, in root coordinates. */
    fun reportOrigin(center: Offset) {
        origin = center
    }

    /** Where the toggle probably is when it has no Compose layout (the native iOS bar). */
    fun reportFallbackOrigin(center: Offset) {
        fallbackOrigin = center
    }

    internal fun isCrossed(point: Offset): Boolean {
        // Before anything is measured there is no geometry to sweep: fall back to the flag's side.
        if (!point.isSpecified || rootBounds.isEmpty) return progress.value >= HALF
        val o = resolvedOrigin
        return veilCrossed(hypot(point.x - o.x, point.y - o.y), radius, peakLag)
    }
}

/** The screen's veil; null outside the chat screen, where everything keeps its plain state. */
val LocalModeVeil = compositionLocalOf<ModeVeilState?> { null }

/** The composer's glass edge flare while the band crosses it; read by the composer's capsule. */
internal val LocalComposerFlare = compositionLocalOf<GlassFlare?> { null }

@Composable
fun rememberModeVeilState(isTemporary: Boolean): ModeVeilState {
    val reduceMotion = rememberReduceMotion()
    val density = LocalDensity.current
    val glass = isLiquidGlass
    val state = remember {
        ModeVeilState(initiallyOn = isTemporary, reduceMotion = reduceMotion, pxPerDp = density.density)
    }
    SideEffect { state.glass = glass }
    LaunchedEffect(state, isTemporary) {
        val target = if (isTemporary) 1f else 0f
        val userToggle = state.pendingUserTarget == isTemporary
        state.pendingUserTarget = null
        if (state.progress.value == target) return@LaunchedEffect
        if (!veilShouldSweep(userToggle, state.reduceMotion)) {
            state.progress.snapTo(target)
            return@LaunchedEffect
        }
        state.sweepingOn = isTemporary
        state.sweeping = true
        try {
            state.progress.animateTo(
                targetValue = target,
                animationSpec = tween(
                    durationMillis = veilSweepMillis(state.progress.value, isTemporary),
                    easing = veilEasing(isTemporary),
                ),
            )
        } finally {
            state.sweeping = false
        }
    }
    return state
}

/** Reports the toggle's centre as the band's origin. */
fun Modifier.modeVeilOrigin(state: ModeVeilState?): Modifier =
    if (state == null) this else onGloballyPositioned { state.reportOrigin(it.boundsInRoot().center) }

/**
 * Draws the veil over (and, in Material, the retone behind) the node it is applied to: the box
 * holding the landing, the composer and the top bar.
 *
 * - **Material:** a dense band with a soft front, and behind it the background retoned
 *   violet. At rest the retone fills the box, so it lasts the whole temporary chat.
 * - **Liquid Glass:** a soft mist band, plus (where the device can blur) a ring that blurs what is
 *   under it. The ring records the box's content into a layer, but only while a sweep runs.
 */
@Composable
fun rememberModeVeilModifier(state: ModeVeilState): Modifier {
    val glass = isLiquidGlass
    val accent = ChatInputDefaults.temporaryAccent
    val dark = LocalDarkTheme.current
    val density = LocalDensity.current
    val blurRing = glass && glassBlurSupported
    val contentLayer = if (blurRing) rememberGraphicsLayer() else null
    val ringLayer = if (blurRing) rememberGraphicsLayer() else null
    val look = remember(glass, dark, accent, density) {
        with(density) {
            VeilLook(
                bandColor = accent.copy(alpha = bandAlpha(glass, dark)),
                retoneColor = if (glass) Color.Transparent else retoneTint(accent, dark),
                originFadePx = OriginFade.toPx(),
                ringBlur = RingBlur.toPx().let { BlurEffect(it, it, TileMode.Decal) },
            )
        }
    }
    return Modifier
        .onGloballyPositioned { state.rootBounds = it.boundsInRoot() }
        .drawBehind {
            if (look.retoneColor.alpha > 0f) drawRetone(state, look.retoneColor)
        }
        .drawWithContent {
            if (!state.sweeping) {
                drawContent()
                return@drawWithContent
            }
            val center = state.resolvedOrigin - state.rootBounds.topLeft
            val r = state.radius
            if (contentLayer != null && ringLayer != null) {
                drawBlurRing(contentLayer, ringLayer, center, r, state, look)
            } else {
                drawContent()
            }
            drawBand(center, r, state, look)
        }
}

/**
 * A surface the band changes. [crossed] is whether it shows its temporary styling right now;
 * [modifier] measures it (and plays its reaction); [flare] is its glass edge light, if it reacts.
 */
@Stable
class VeilTarget internal constructor(
    crossed: () -> Boolean,
    val modifier: Modifier,
    val flare: GlassFlare?,
) {
    private val crossedState = crossed
    val crossed: Boolean get() = crossedState()
}

/** How a surface answers the band passing over it. */
enum class VeilReaction {
    /** Changes state only. */
    NONE,

    /** Material: a 4dp nudge. Liquid Glass: a slight swell and an edge flare. */
    NUDGE,
}

/**
 * Tracks one surface against the band. Without a veil on screen (previews, other screens) the
 * surface simply follows [isTemporary]. [anchor] replaces the measured centre for a surface whose
 * layout this composable can't measure (the glass bar's actions capsule, which holds the toggle).
 */
@Composable
fun rememberVeilTarget(
    isTemporary: Boolean,
    reaction: VeilReaction = VeilReaction.NONE,
    swell: Float = CHIP_SWELL,
    anchor: (() -> Offset)? = null,
): VeilTarget {
    val veil = LocalModeVeil.current
    val glass = isLiquidGlass
    val density = LocalDensity.current
    val flareColor = lerp(ChatInputDefaults.temporaryAccent, Color.White, FLARE_WHITE)
    var center by remember { mutableStateOf(Offset.Unspecified) }
    val crossed = remember(veil, isTemporary, anchor) {
        if (veil == null) {
            derivedStateOf { isTemporary }
        } else {
            derivedStateOf { veil.isCrossed(anchor?.invoke() ?: center) }
        }
    }
    val bump = remember { Animatable(0f) }
    val reacts = veil != null && reaction == VeilReaction.NUDGE && !veil.reduceMotion
    if (reacts) {
        LaunchedEffect(crossed.value) {
            // Only the band passing makes a surface react, never a snap.
            if (veil?.sweeping != true) return@LaunchedEffect
            bump.snapTo(0f)
            bump.animateTo(1f, tween(if (glass) GLASS_REACT_MS else MATERIAL_REACT_MS, easing = LinearEasing))
            bump.snapTo(0f)
        }
    }
    val nudgePx = with(density) { NudgeRise.toPx() }
    val modifier = remember(veil, reacts, glass, swell, nudgePx, anchor) {
        val measure = if (veil != null && anchor == null) {
            Modifier.onGloballyPositioned { center = it.boundsInRoot().center }
        } else {
            Modifier
        }
        if (!reacts) {
            measure
        } else {
            measure.graphicsLayer {
                val t = bump.value
                if (glass) {
                    val s = 1f + swell * swellCurve(t)
                    scaleX = s
                    scaleY = s
                } else {
                    translationY = nudgeCurve(t) * nudgePx
                }
            }
        }
    }
    val flare = remember(reacts, glass, flareColor) {
        if (reacts && glass) GlassFlare(flareColor) { flareCurve(bump.value) } else null
    }
    return remember(crossed, modifier, flare) { VeilTarget({ crossed.value }, modifier, flare) }
}

private class VeilLook(
    val bandColor: Color,
    val retoneColor: Color,
    val originFadePx: Float,
    val ringBlur: BlurEffect,
)

/**
 * Material's retone: full strength behind the band, fading out across the band's back half to
 * nothing at its peak, so the tint has no edge of its own. At rest on it covers the whole box.
 */
private fun DrawScope.drawRetone(state: ModeVeilState, tint: Color) {
    val r = state.radius
    val peak = r - state.peakLag
    if (peak <= 0f) return
    val solidStop = (max(0f, r - state.bandTail) / peak).coerceIn(0f, 1f)
    drawRect(
        Brush.radialGradient(
            0f to tint,
            solidStop to tint,
            1f to Color.Transparent,
            center = state.resolvedOrigin - state.rootBounds.topLeft,
            radius = peak,
        ),
    )
}

private fun DrawScope.drawBand(center: Offset, r: Float, state: ModeVeilState, look: VeilLook) {
    val outer = r + state.bandLead
    if (outer <= 0f) return
    // Fade only where the sweep ends: at the far corner turning on, into the toggle turning off.
    val maxRadius = state.maxRadius
    val fade = if (state.sweepingOn) {
        minOf(1f, (maxRadius - r) / (EDGE_FADE * maxRadius))
    } else {
        minOf(1f, r / look.originFadePx)
    }.coerceIn(0f, 1f)
    if (fade <= 0f) return
    val inner = max(0f, r - state.bandTail)
    val innerStop = inner / outer
    val peakStop = innerStop + (1f - innerStop) * state.bandPeakAt
    val peak = look.bandColor.copy(alpha = look.bandColor.alpha * fade)
    drawRect(
        Brush.radialGradient(
            0f to Color.Transparent,
            innerStop to Color.Transparent,
            peakStop to peak,
            1f to Color.Transparent,
            center = center,
            radius = outer,
        ),
    )
}

private fun ContentDrawScope.drawBlurRing(
    contentLayer: GraphicsLayer,
    ringLayer: GraphicsLayer,
    center: Offset,
    r: Float,
    state: ModeVeilState,
    look: VeilLook,
) {
    contentLayer.record { this@drawBlurRing.drawContent() }
    drawLayer(contentLayer)
    // The same profile as the band, so the blur softens it rather than adding a ring of its own.
    val outer = r + state.bandLead
    if (outer <= 0f) return
    val innerStop = max(0f, r - state.bandTail) / outer
    val peakStop = (max(0f, r - state.peakLag) / outer).coerceAtLeast(innerStop)
    ringLayer.compositingStrategy = CompositingStrategy.Offscreen
    ringLayer.renderEffect = look.ringBlur
    ringLayer.record {
        drawLayer(contentLayer)
        drawRect(
            Brush.radialGradient(
                0f to Color.Transparent,
                innerStop to Color.Transparent,
                peakStop to Color.Black,
                1f to Color.Transparent,
                center = center,
                radius = outer,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
    drawLayer(ringLayer)
}

/**
 * [base] with Material's temporary-chat retone over it, for a scrim drawn over the retoned
 * background (the bars' fades) to keep the tint instead of covering it. [base] itself where there
 * is no retone: Liquid Glass, or off the chat screen.
 */
@Composable
fun retonedSurface(base: Color): Color {
    if (LocalModeVeil.current == null || isLiquidGlass) return base
    return retoneTint(ChatInputDefaults.temporaryAccent, LocalDarkTheme.current).compositeOver(base)
}

private fun retoneTint(accent: Color, dark: Boolean): Color = accent.copy(alpha = if (dark) 0.07f else 0.05f)

private fun bandAlpha(glass: Boolean, dark: Boolean): Float = when {
    glass && dark -> 0.28f
    glass -> 0.20f
    dark -> 0.50f
    else -> 0.40f
}

private fun swellCurve(t: Float): Float = if (t <= 0f || t >= 1f) 0f else sin(PI.toFloat() * t)

private fun flareCurve(t: Float): Float = when {
    t <= 0f || t >= 1f -> 0f
    t < FLARE_PEAK -> t / FLARE_PEAK
    else -> (1f - t) / (1f - FLARE_PEAK)
}

private const val HALF = 0.5f
private const val MATERIAL_REACT_MS = 360
private const val GLASS_REACT_MS = 480
private const val MATERIAL_PEAK = 0.65f
private const val GLASS_PEAK = 0.7f

// The band's length as a share of the sweep (72dp and 96dp over a ~576dp phone sweep), so a
// larger screen gets a proportionally wider band rather than a thinner ring.
private const val MATERIAL_TAIL_SHARE = 0.125f
private const val GLASS_TAIL_SHARE = 0.17f

// Material's front fades over a quarter of the band's length, so the band is one soft haze rather
// than a crisp ring with a tail.
private const val MATERIAL_LEAD_SHARE = 0.25f
private const val EDGE_FADE = 0.2f
private const val FLARE_PEAK = 0.35f
private const val FLARE_WHITE = 0.55f

/** A glass chip's swell; the composer, being wider, takes [COMPOSER_SWELL]. */
const val CHIP_SWELL = 0.03f
const val COMPOSER_SWELL = 0.015f

private val MaterialTailFloor: Dp = 72.dp
private val GlassTailFloor: Dp = 96.dp
private val GlassLead: Dp = 8.dp
private val OriginFade: Dp = 40.dp
private val NudgeRise: Dp = 4.dp
private val RingBlur: Dp = 4.dp
