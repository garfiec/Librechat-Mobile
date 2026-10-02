package com.garfiec.librechat.core.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Liquid Glass segmented control, modelled on the iOS 26 tab bar: the thumb swells into a lens
 * while touched, and a drag carries it under the finger.
 *
 * Pass a [backdrop] only from a sibling of the recorded content (a bar slot), never from inside it:
 * a surface sampling its own ancestor feeds back into itself.
 *
 * The lens, highlight and inner shadow need the refraction shader, so the blur-only and flat tiers
 * get the motion without them. Motion and effect values follow the LiquidBottomTabs sample in
 * Kyant's backdrop catalog (github.com/Kyant0/AndroidLiquidGlass, Apache-2.0).
 */
@Composable
internal fun LiquidSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backdrop: GlassBackdrop? = null,
) {
    val style = rememberGlassStyle()
    val refract = style?.refracts == true
    val trackColor = GlassControlColors.fill
    val thumbColor = GlassControlColors.segmentThumb
    val accent = style?.accent ?: MaterialTheme.colorScheme.primary
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val motion = remember(scope) { LiquidThumbMotion(scope, selectedIndex.coerceAtLeast(0).toFloat()) }
    val trackSurface = if (style != null && backdrop != null) {
        Modifier.glassSurface(style, CircleShape, backdrop) {
            val tilt = motion.leanTilt
            if (rtl) -tilt else tilt
        }
    } else {
        Modifier.background(trackColor, CircleShape)
    }

    // The thumb samples the page (where it overhangs the track) under an invisible copy of the track
    // whose labels are in the accent colour on the thumb's fill. At rest that copy reads as the iOS
    // selected segment; touched, the fill fades out and the lens shows the accent labels refracting.
    val accentLayer = rememberLayerBackdrop()
    val thumbBackdrop: Backdrop = if (backdrop != null) {
        rememberCombinedBackdrop(backdrop.layer, accentLayer)
    } else {
        accentLayer
    }

    val count = options.size.coerceAtLeast(1)
    LaunchedEffect(motion, selectedIndex, count) {
        if (selectedIndex in options.indices && motion.target != selectedIndex.toFloat()) {
            motion.moveTo(selectedIndex.toFloat(), count)
        }
    }
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnSelect by rememberUpdatedState(onSelect)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(SegmentHeight)
            .alpha(if (enabled) 1f else GlassDefaults.DISABLED_ALPHA),
    ) {
        val segmentWidth = (maxWidth - TrackPadding * 2) / count
        val segmentPx = with(density) { segmentWidth.toPx() }
        val padPx = with(density) { TrackPadding.toPx() }
        val thumbHeight = SegmentHeight - TrackPadding * 2
        motion.pressedScaleX = (segmentWidth + ThumbOverhangX * 2) / segmentWidth
        motion.pressedScaleY = (thumbHeight + ThumbOverhangY * 2) / thumbHeight
        motion.maxPullPx = with(density) { MaxPull.toPx() }
        motion.maxShiftPx = with(density) { MaxShift.toPx() }
        motion.maxGrowPx = with(density) { MaxGrow.toPx() }
        motion.leanSpanPx = segmentPx
        val lean = Modifier.graphicsLayer { translationX = motion.leanX }
        // The glass grows with a drag and keepLabels undoes it for the labels. Read in layout, so a
        // drag doesn't recompose.
        val growTrack = Modifier.layout { measurable, constraints ->
            val grow = motion.growY.roundToInt()
            val placeable = measurable.measure(
                constraints.copy(minHeight = constraints.minHeight + 2 * grow, maxHeight = constraints.maxHeight + 2 * grow),
            )
            layout(placeable.width, placeable.height - 2 * grow) { placeable.place(0, -grow) }
        }
        val keepLabels = Modifier.layout { measurable, constraints ->
            val grow = motion.growY.roundToInt()
            val placeable = measurable.measure(
                constraints.copy(
                    minHeight = (constraints.minHeight - 2 * grow).coerceAtLeast(0),
                    maxHeight = (constraints.maxHeight - 2 * grow).coerceAtLeast(0),
                ),
            )
            layout(placeable.width, placeable.height + 2 * grow) { placeable.place(0, grow) }
        }

        // Segment position (0 = first segment's start, fractional between) to x and back, mirrored in RTL.
        fun xAt(position: Float, width: Float): Float {
            val fromStart = padPx + position * segmentPx
            return if (rtl) width - fromStart else fromStart
        }
        fun positionAt(x: Float, width: Float): Float = ((if (rtl) width - x else x) - padPx) / segmentPx

        // Sized in segments so it fills a tablet strip too; built at the origin and moved to the thumb.
        val glowBrush = remember(segmentPx) {
            Brush.radialGradient(
                0f to Color.White.copy(alpha = GLOW_PEAK),
                GLOW_SHOULDER to Color.White.copy(alpha = GLOW_PEAK * GLOW_SHOULDER_LEVEL),
                1f to Color.Transparent,
                center = Offset.Zero,
                radius = segmentPx * GLOW_RADIUS_SEGMENTS,
            )
        }
        val touchGlow = Modifier.drawWithContent {
            val progress = motion.glowProgress
            if (progress > 0f) {
                drawRect(Color.White, alpha = GLOW_WASH * progress, blendMode = BlendMode.Plus)
                val center = Offset(xAt(motion.value + 0.5f, size.width), size.height / 2f)
                translate(center.x, center.y) {
                    drawRect(glowBrush, topLeft = -center, size = size, alpha = progress, blendMode = BlendMode.Plus)
                }
            }
            drawContent()
        }

        // Read latest rather than keyed: a re-layout mid-press (a sidebar dragged beside the strip)
        // would otherwise restart the gesture and cancel it before the thumb is released.
        val latestCount by rememberUpdatedState(count)
        val latestPositionAt by rememberUpdatedState(::positionAt)
        val gestures = if (enabled) {
            Modifier.pointerInput(motion) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    motion.touchDown()
                    var finished = false
                    try {
                        var dragging = false
                        var released = false
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                released = true
                                break
                            }
                            if (!dragging && abs(change.position.x - down.position.x) > viewConfiguration.touchSlop) {
                                dragging = true
                            }
                            if (dragging) {
                                change.consume()
                                val at = latestPositionAt(change.position.x, size.width.toFloat())
                                motion.follow(
                                    to = rubberBand(at - 0.5f, latestCount - 1f),
                                    pullY = change.position.y - down.position.y,
                                )
                                motion.lean(change.position.x - down.position.x)
                            } else {
                                // An ancestor (a scrolling list, a sheet's drag) took the gesture: give it up,
                                // as a clickable does, rather than selecting under the finger on release.
                                val final = awaitPointerEvent(PointerEventPass.Final)
                                if (final.changes.any { it.id == down.id && it.isConsumed }) break
                            }
                        }
                        val target = when {
                            !released -> currentSelected
                            dragging -> motion.target.roundToInt()
                            else -> (latestPositionAt(down.position.x, size.width.toFloat()) - 0.5f).roundToInt()
                        }.coerceIn(0, latestCount - 1)
                        motion.touchUp(target.toFloat(), latestCount)
                        finished = true
                        if (target != currentSelected) currentOnSelect(target)
                    } finally {
                        // Cancelled mid-press: never leave the thumb swollen.
                        if (!finished) motion.touchUp(currentSelected.coerceIn(0, latestCount - 1).toFloat(), latestCount)
                    }
                }
            }
        } else {
            Modifier
        }

        SegmentLabels(
            options = options,
            selectedIndex = selectedIndex,
            segmentWidth = segmentWidth,
            accent = null,
            onSelect = onSelect.takeIf { enabled },
            modifier = lean
                .then(growTrack)
                .then(trackSurface)
                // The glow is a full-bounds rect; unclipped it lights the corners past the round ends.
                .clip(CircleShape)
                .then(touchGlow)
                .then(keepLabels)
                .selectableGroup(),
        )
        SegmentLabels(
            options = options,
            selectedIndex = selectedIndex,
            segmentWidth = segmentWidth,
            accent = accent,
            onSelect = null,
            modifier = Modifier
                .clearAndSetSemantics {}
                .then(lean)
                .then(growTrack)
                .alpha(0f)
                .layerBackdrop(accentLayer)
                .then(trackSurface)
                .clip(CircleShape)
                .drawBehind { drawRect(thumbColor, alpha = 1f - motion.pressProgress) }
                .then(touchGlow)
                .then(keepLabels),
        )

        // No thumb when nothing is selected (a stored value outside the options).
        if (selectedIndex in options.indices) {
            Box(
                modifier = Modifier
                    .padding(TrackPadding)
                    .graphicsLayer {
                        // The thumb is laid out on the first segment, so translate from there.
                        translationX = xAt(motion.value, 0f) - xAt(0f, 0f) + motion.leanX
                        translationY = motion.pullY
                    }
                    .width(segmentWidth)
                    .fillMaxHeight()
                    // Swell and stretch resize the thumb rather than scaling its layer: a scaled
                    // capsule's round ends turn into flattened ovals, a resized one stays a capsule.
                    .layout { measurable, constraints ->
                        val width = constraints.maxWidth
                        val height = constraints.maxHeight
                        val swollen = measurable.measure(
                            Constraints.fixed(
                                (width * motion.scaleX).roundToInt(),
                                (height * motion.scaleY).roundToInt(),
                            ),
                        )
                        layout(width, height) {
                            swollen.place((width - swollen.width) / 2, (height - swollen.height) / 2)
                        }
                    }
                    .drawBackdrop(
                        backdrop = thumbBackdrop,
                        shape = { CircleShape },
                        effects = {
                            val progress = motion.pressProgress
                            if (refract && progress > 0f) {
                                lens(
                                    LensHeight.toPx() * progress,
                                    LensAmount.toPx() * progress,
                                    chromaticAberration = true,
                                )
                            }
                        },
                        highlight = {
                            val progress = motion.pressProgress
                            if (refract && progress > 0f) {
                                tiltedHighlight(if (rtl) -motion.sheenTilt else motion.sheenTilt, progress)
                            } else {
                                null
                            }
                        },
                        shadow = {
                            Shadow.Default.copy(radius = lerp(RestShadow, Shadow.Default.radius, motion.pressProgress))
                        },
                        innerShadow = {
                            val progress = motion.pressProgress
                            if (refract && progress > 0f) {
                                InnerShadow(radius = InnerShadowRadius * progress, alpha = progress)
                            } else {
                                null
                            }
                        },
                    ),
            )
        }
        // Over everything, so a drag that starts on the thumb is still the control's.
        Box(Modifier.fillMaxSize().then(gestures))
    }
}

/**
 * One row of segment labels. The visible row ([accent] null) carries the tab semantics; touch goes
 * through the control's own gesture, so a tab is only clickable here for accessibility services.
 * The accent row is the hidden copy the thumb shows, so all of its labels take the selected weight.
 */
@Composable
private fun SegmentLabels(
    options: List<String>,
    selectedIndex: Int,
    segmentWidth: Dp,
    accent: Color?,
    onSelect: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier)
            .padding(TrackPadding),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .semantics {
                        role = Role.Tab
                        this.selected = selected
                        if (onSelect != null) {
                            onClick {
                                onSelect(index)
                                true
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = accent ?: Color.Unspecified,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected || accent != null) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The thumb's springs. [moveTo] releases the swell just before arriving, so its wobble overlaps the slide's end. */
@Stable
private class LiquidThumbMotion(private val scope: CoroutineScope, initial: Float) {
    private val position = Animatable(initial)
    private val press = Animatable(0f)
    private val swellX = Animatable(1f)
    private val swellY = Animatable(1f)
    private val stretch = Animatable(0f)
    private val pull = Animatable(0f)
    private val sheen = Animatable(0f)
    private val glowAnim = Animatable(0f)
    private val dragDx = Animatable(0f)
    private var job: Job? = null

    // Set from layout.
    var pressedScaleX = 1f
    var pressedScaleY = 1f
    var maxPullPx = 0f
    var maxShiftPx = 0f

    /** The drag that reaches full lean: one segment, not the strip, so a wide tablet strip leans as readily. */
    var leanSpanPx = 1f
    var maxGrowPx = 0f

    val value: Float get() = position.value
    val target: Float get() = position.targetValue
    val pressProgress: Float get() = press.value
    val pullY: Float get() = pull.value

    val leanTilt: Float get() = if (maxShiftPx > 0f) leanX / maxShiftPx * MAX_SHEEN_TILT else 0f

    val leanX: Float
        get() = maxShiftPx * leanAmount * if (dragDx.value < 0f) -1f else 1f

    val growY: Float get() = maxGrowPx * leanAmount

    // Eased out, so it gives quickly then stiffens.
    private val leanAmount: Float
        get() = EaseOut.transform((abs(dragDx.value) / leanSpanPx.coerceAtLeast(1f)).coerceAtMost(1f))

    fun lean(dx: Float) {
        scope.launch { dragDx.snapTo(dx) }
    }

    val scaleX: Float get() = swellX.value / (1f - stretchAmount * STRETCH_X)
    val scaleY: Float get() = swellY.value * (1f - stretchAmount * STRETCH_Y)

    // Deliberately underdamped: overshooting past zero ripples the thumb wide, tall, wide.
    private val stretchAmount: Float get() = stretch.value.coerceIn(-MAX_STRETCH, MAX_STRETCH)

    val glowProgress: Float get() = glowAnim.value.coerceAtLeast(0f)

    val sheenTilt: Float get() = sheen.value * MAX_SHEEN_TILT

    fun touchDown() {
        job?.cancel()
        job = scope.launch { animatePress(1f, pressedScaleX, pressedScaleY) }
        scope.launch { glowAnim.animateTo(1f, GlowSpec) }
    }

    fun touchUp(target: Float, count: Int) {
        scope.launch { dragDx.animateTo(0f, LeanSpec) }
        scope.launch { glowAnim.animateTo(0f, GlowSpec) }
        moveTo(target, count)
    }

    /** [pullY] is the finger's vertical travel; the thumb gives to it, rubber-banded. */
    fun follow(to: Float, pullY: Float) {
        scope.launch { slide(to, FollowSpec) }
        val give = maxPullPx * (1f - 1f / (1f + abs(pullY) * PULL_GIVE / maxPullPx.coerceAtLeast(1f)))
        scope.launch { pull.animateTo(if (pullY < 0) -give else give, FollowSpec) }
    }

    fun moveTo(to: Float, count: Int) {
        val range = (count - 1).coerceAtLeast(1)
        job?.cancel()
        job = scope.launch {
            animatePress(1f, pressedScaleX, pressedScaleY)
            launch { pull.animateTo(0f, SwellYSpec) }
            launch {
                slide(to, PositionSpec)
                launch { sheen.animateTo(0f, StretchSpec) }
                stretch.animateTo(0f, StretchSpec)
            }
            snapshotFlow { abs(position.value - to) < range * RELEASE_FRACTION }.first { it }
            animatePress(0f, 1f, 1f)
        }
    }

    private suspend fun slide(to: Float, spec: SpringSpec<Float>) {
        position.animateTo(to, spec) {
            val speed = abs(velocity) / VELOCITY_SCALE
            scope.launch { stretch.animateTo(speed.coerceAtMost(MAX_STRETCH), StretchSpec) }
            scope.launch { sheen.animateTo((velocity / VELOCITY_SCALE).coerceIn(-1f, 1f), StretchSpec) }
        }
    }

    /**
     * Starts (does not await) the press animations. Undispatched, so each animateTo takes its
     * Animatable over at the call: a later call (the release) always wins over an earlier one (the
     * swell), even when the release comes straight after it, as on a tap of the selected segment.
     */
    private fun CoroutineScope.animatePress(progress: Float, scaleX: Float, scaleY: Float) {
        launch(start = CoroutineStart.UNDISPATCHED) { press.animateTo(progress, PressSpec) }
        launch(start = CoroutineStart.UNDISPATCHED) { swellX.animateTo(scaleX, SwellXSpec) }
        launch(start = CoroutineStart.UNDISPATCHED) { swellY.animateTo(scaleY, SwellYSpec) }
    }

    private companion object {
        const val VISIBILITY = 0.001f
        const val STRETCH_X = 0.75f
        const val STRETCH_Y = 0.6f
        const val MAX_STRETCH = 0.28f

        /** Segments per second that give a full stretch's worth of stretch target, before the clamp. */
        const val VELOCITY_SCALE = 14f
        const val PULL_GIVE = 0.5f
        const val MAX_SHEEN_TILT = 60f
        const val RELEASE_FRACTION = 0.025f
        val PositionSpec = spring(dampingRatio = 0.8f, stiffness = 500f, visibilityThreshold = VISIBILITY)
        val FollowSpec = spring(dampingRatio = 0.55f, stiffness = 380f, visibilityThreshold = VISIBILITY)
        val PressSpec = spring(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = VISIBILITY)
        val SwellXSpec = spring(dampingRatio = 0.6f, stiffness = 250f, visibilityThreshold = VISIBILITY)
        val SwellYSpec = spring(dampingRatio = 0.6f, stiffness = 250f, visibilityThreshold = VISIBILITY)
        val LeanSpec = spring(dampingRatio = 0.6f, stiffness = 300f, visibilityThreshold = VISIBILITY)
        val GlowSpec = spring(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = VISIBILITY)
        val StretchSpec = spring(dampingRatio = 0.3f, stiffness = 170f, visibilityThreshold = VISIBILITY)
    }
}

private val SegmentHeight = 36.dp
private val TrackPadding = 3.dp

private val ThumbOverhangX = 12.dp
private val ThumbOverhangY = 12.dp
private val MaxPull = 4.dp
private val MaxShift = 8.dp
private val MaxGrow = 4.dp

/** Past either end the thumb follows a quarter as far, up to a third of a segment. */
private fun rubberBand(position: Float, last: Float): Float = when {
    position < 0f -> (position * OVERDRAG).coerceAtLeast(-MAX_OVERDRAG)
    position > last -> last + ((position - last) * OVERDRAG).coerceAtMost(MAX_OVERDRAG)
    else -> position
}

private const val OVERDRAG = 0.25f
private const val MAX_OVERDRAG = 0.33f
private val LensHeight = 16.dp
private val LensAmount = 20.dp
private val InnerShadowRadius = 8.dp
private val RestShadow = 2.dp

private const val GLOW_WASH = 0.04f
private const val GLOW_PEAK = 0.08f
private const val GLOW_SHOULDER = 0.4f
private const val GLOW_SHOULDER_LEVEL = 0.6f
private const val GLOW_RADIUS_SEGMENTS = 1f
