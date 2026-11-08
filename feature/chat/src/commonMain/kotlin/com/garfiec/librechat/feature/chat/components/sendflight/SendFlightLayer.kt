package com.garfiec.librechat.feature.chat.components.sendflight

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.garfiec.librechat.core.ui.components.BubbleShape
import com.garfiec.librechat.core.ui.glass.GlassBackdrop
import com.garfiec.librechat.core.ui.glass.GlassStyle
import com.garfiec.librechat.core.ui.glass.LocalGlassBackdrop
import com.garfiec.librechat.core.ui.glass.glassSurface
import com.garfiec.librechat.core.ui.glass.rememberGlassStyle
import com.garfiec.librechat.core.ui.theme.LocalDarkTheme
import com.garfiec.librechat.feature.chat.components.ChatInputDefaults
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws the message in the air, over the thread and the composer. Fill the screen's box with it,
 * below sheets and the top bar. Draws nothing, and costs nothing, while no flight is up.
 */
@Composable
internal fun SendFlightLayer(state: SendFlightState, fontSizeMultiplier: Float, modifier: Modifier = Modifier) {
    val flight = state.flight
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val metrics = remember(density) { FlightMetrics(density) }
    val glass = rememberGlassStyle()
    val backdrop = LocalGlassBackdrop.current
    val fieldStyle = MaterialTheme.typography.bodyLarge
    val bubbleStyle = fieldStyle.copy(
        fontSize = fieldStyle.fontSize * fontSizeMultiplier,
        lineHeight = fieldStyle.lineHeight * fontSizeMultiplier,
    )
    val scheme = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val colors = FlightColors(
        box = ChatInputDefaults.containerColor,
        border = scheme.outline,
        bubble = scheme.secondaryContainer,
        fieldText = scheme.onSurface,
        bubbleText = scheme.onSecondaryContainer,
        dot = scheme.primary,
        sheen = Color.White.copy(alpha = if (dark) SHEEN_ALPHA_DARK else SHEEN_ALPHA_LIGHT),
        glassTint = glass?.tint,
    )

    if (flight != null) {
        LaunchedEffect(flight) {
            fly(state, flight, Measure(measurer, fieldStyle, bubbleStyle), metrics, haptics, glass = glass != null)
        }
    }
    Box(modifier.onPlaced { state.layer = it }) {
        if (flight != null) {
            if (glass != null) GlassDrop(state, flight, metrics, glass, colors, backdrop)
            Spacer(
                Modifier
                    .matchParentSize()
                    .drawBehind { drawFlight(state, flight, colors, metrics, glass = glass != null) },
            )
        }
    }
}

/** The drop as real glass: only a rounded rectangle can be, so the neck stays a drawn path. */
@Composable
private fun GlassDrop(
    state: SendFlightState,
    flight: Flight,
    metrics: FlightMetrics,
    glass: GlassStyle,
    colors: FlightColors,
    backdrop: GlassBackdrop?,
) {
    Box(
        Modifier
            .offset {
                val drop = state.frame(flight, metrics)?.drop ?: Rect.Zero
                IntOffset(drop.left.roundToInt(), drop.top.roundToInt())
            }
            .layout { measurable, _ ->
                val drop = state.frame(flight, metrics)?.drop
                val w = drop?.width?.roundToInt() ?: 0
                val h = drop?.height?.roundToInt() ?: 0
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(w, h) { placeable.place(0, 0) }
            }
            .graphicsLayer {
                val wobble = flight.wobble.value
                scaleX = 1f + wobble
                scaleY = 1f - wobble
                alpha = if (state.frame(flight, metrics) == null) 0f else 1f - flight.land.value
            }
            .glassSurface(glass, BubbleShape, backdrop)
            .drawBehind {
                val frame = state.frame(flight, metrics) ?: return@drawBehind
                val fill = if (frame.target.hasBubble) colors.bubble else Color.Transparent
                drawRoundRect(fill.copy(alpha = fill.alpha * frame.color), cornerRadius = CornerRadius(metrics.corner))
            },
    )
}

@Immutable
private class FlightColors(
    val box: Color,
    val border: Color,
    val bubble: Color,
    val fieldText: Color,
    val bubbleText: Color,
    val dot: Color,
    val sheen: Color,
    val glassTint: Color?,
)

private class Measure(val measurer: TextMeasurer, val field: TextStyle, val bubble: TextStyle)

/** The flight's measures in pixels. */
internal class FlightMetrics(density: Density) {
    val snap = with(density) { 14.dp.toPx() }
    val maxRecoil = with(density) { 7.dp.toPx() }
    val maxNeck = with(density) { 120.dp.toPx() }
    val corner = with(density) { 16.dp.toPx() }
    val overlap = with(density) { 2.dp.toPx() }
    val hairline = with(density) { 1.dp.toPx() }
    val fieldPadding = with(density) { 16.dp.toPx() }
    val bubblePaddingX = with(density) { 12.dp.toPx() }
    val dotInset = with(density) { 16.dp.toPx() }
    val dotRadius = with(density) { 3.dp.toPx() }
    val dotStep = with(density) { 11.dp.toPx() }
    val density = density.density
}

/** Where the copy is this frame, from the animatables and the live places of the box and the row. */
internal class FlightFrame(
    val drop: Rect,
    val shape: Float,
    val color: Float,
    val box: Rect?,
    val target: SendFlightState.Target,
    val sourceBubble: Rect,
    val sourceOrigin: Offset,
)

internal fun SendFlightState.frame(f: Flight, m: FlightMetrics): FlightFrame? {
    val start = f.start ?: return null
    val target = targetOf(f) ?: return null
    val box = rectOf(box)
    val origin = sourceOrigin(f, box, m)
    val source = sourceBubble(f, origin, target, m)
    val rise = f.rise.value
    // Aimed at the row's live place from the first frame: the drop rises as the list makes room
    // for it, and never sinks below where it started while the row is still under the composer.
    val drop = Rect(
        offset = Offset(
            lerp(source.left, target.bubble.left, f.flyX.value),
            minOf(lerp(source.top, target.bubble.top, f.flyY.value), source.top),
        ),
        size = lerp(source.size, target.bubble.size, rise),
    )
    val color = RISE_COLOR * smoothstep(RISE_COLOR_FROM, 1f, rise) +
        (1f - RISE_COLOR) * (f.flyY.value * FLY_COLOR_RATE).coerceIn(0f, 1f)
    return FlightFrame(drop, shape = rise, color = color, box = box, target = target, sourceBubble = source, sourceOrigin = origin)
        .takeIf { start.size.width > 0 }
}

/** Where the field's first line starts, scrolled up when the text is taller than the field. */
private fun sourceOrigin(f: Flight, box: Rect?, m: FlightMetrics): Offset {
    // The field rides the box: the keyboard closing on send drops the whole composer mid-flight.
    val field = if (box == null) f.fieldRect else f.fieldRect.translate(box.topLeft - f.boxRect.topLeft)
    val start = f.start ?: return field.topLeft
    val inner = field.height - 2 * m.fieldPadding
    val scrolled = max(0f, start.size.height - inner)
    return Offset(field.left + m.fieldPadding, field.top + m.fieldPadding - scrolled)
}

/** The text's own box in the field, padded the way the bubble pads it, so nothing jumps at take-off. */
private fun sourceBubble(f: Flight, origin: Offset, target: SendFlightState.Target, m: FlightMetrics): Rect {
    val start = f.start ?: return Rect(origin, f.fieldRect.size)
    var left = Float.MAX_VALUE
    var right = 0f
    for (line in 0 until start.lineCount) {
        left = minOf(left, start.getLineLeft(line))
        right = maxOf(right, start.getLineRight(line))
    }
    val padX = if (target.hasBubble) m.bubblePaddingX else 0f
    val padTop = target.text.top - target.bubble.top
    val padBottom = max(0f, target.bubble.bottom - target.text.bottom)
    return Rect(
        left = origin.x + left - padX,
        top = origin.y - padTop,
        right = origin.x + right + padX,
        bottom = origin.y + start.size.height + padBottom,
    )
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
private suspend fun fly(
    state: SendFlightState,
    f: Flight,
    measure: Measure,
    m: FlightMetrics,
    haptics: HapticFeedback,
    glass: Boolean,
) = coroutineScope {
    try {
        withTimeoutOrNull(SendFlightState.CLAIM_WINDOW) {
            snapshotFlow { f.messageId }.first { it != null }
        } ?: return@coroutineScope
        var target: SendFlightState.Target? = null
        repeat(TARGET_FRAMES) {
            if (target == null) {
                withFrameNanos {}
                target = state.targetOf(f)
            }
        }
        val landing = target ?: return@coroutineScope
        state.rectOf(state.box) ?: return@coroutineScope
        measureLayouts(f, measure, landing, m)

        // Until the neck snaps the box's edge follows the drop; at the snap it springs back.
        val tracker = launch {
            while (!f.snapped) {
                withFrameNanos {}
                val frame = state.frame(f, m) ?: continue
                val gap = (frame.box?.top ?: continue) - frame.drop.bottom
                if (!glass) f.recoilPx = recoilFor(gap, m.snap, m.maxRecoil)
                if (gap >= m.snap) {
                    f.snapped = true
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    f.recoil.snapTo(f.recoilPx)
                    launch { f.recoil.animateTo(0f, spring(dampingRatio = 0.28f, stiffness = 520f)) }
                    launch { f.wobble.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 380f), initialVelocity = 1f) }
                }
            }
        }

        val rise = launch { f.rise.animateTo(1f, tween(RISE_MS, easing = FastOutSlowInEasing)) }
        // The text swells in the field a beat before it lifts, so the neck has time to form.
        delay(LIFT_DELAY_MS)
        val x = launch { f.flyX.animateTo(1f, spring(dampingRatio = 0.88f, stiffness = 160f)) }
        // Barely overshoots: the sender line is already shown above the landing spot, and a bounce would cross it.
        val y = launch { f.flyY.animateTo(1f, spring(dampingRatio = 0.88f, stiffness = 230f)) }
        launch {
            snapshotFlow { f.flyY.value }.first { it > LANDING_KICK_AT }
            f.wobble.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 380f), initialVelocity = -0.55f)
        }
        joinAll(rise, x, y)
        tracker.cancel()
        f.wobble.snapTo(0f)
        f.replyShown = true
        launch { f.reveal.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 320f)) }
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        f.land.animateTo(1f, tween(LAND_MS, easing = LinearEasing))
        f.landed = true

        handOff(state, f, m)
    } finally {
        state.end(f)
    }
}

/** A light reads down the bubble, and its tail drops into the reply as the waiting dots. */
private suspend fun handOff(state: SendFlightState, f: Flight, m: FlightMetrics) = coroutineScope {
    val bubble = state.targetOf(f)?.bubble ?: return@coroutineScope
    val sheenMs = (SHEEN_MIN_MS + bubble.height / m.density * SHEEN_MS_PER_DP).roundToInt().coerceIn(SHEEN_MIN_MS, SHEEN_MAX_MS)
    val sheen = launch { f.sheen.animateTo(1f, tween(sheenMs, easing = FastOutSlowInEasing)) }
    snapshotFlow { f.sheen.value }.first { it >= DOT_LEAVES_AT }
    if (state.rectOf(state.waitDots) != null) {
        val now = state.targetOf(f)?.bubble ?: bubble
        f.dotStart = Offset(now.left + m.dotInset, now.bottom - m.dotInset / 2)
        joinAll(
            launch { f.dotX.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 170f)) },
            launch { f.dotY.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 260f)) },
        )
        if (state.rectOf(state.waitDots) != null) {
            f.splitting = true
            f.split.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 520f))
        }
    }
    sheen.join()
}

private fun measureLayouts(f: Flight, measure: Measure, target: SendFlightState.Target, m: FlightMetrics) {
    val fieldWidth = (f.fieldRect.width - 2 * m.fieldPadding).roundToInt().coerceAtLeast(1)
    f.start = measure.measurer.measure(f.text, measure.field, constraints = Constraints(maxWidth = fieldWidth))
    val textWidth = ceil(target.text.width).toInt().coerceAtLeast(1) + 1
    f.end = measure.measurer.measure(f.text, measure.bubble, constraints = Constraints(maxWidth = textWidth))
    f.wordLayouts = f.words.map { measure.measurer.measure(f.text.substring(it), measure.field, softWrap = false) }
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
private fun DrawScope.drawFlight(
    state: SendFlightState,
    f: Flight,
    c: FlightColors,
    m: FlightMetrics,
    glass: Boolean,
) {
    val frame = state.frame(f, m) ?: return
    val copy = 1f - f.land.value
    val start = f.start ?: return
    val end = f.end ?: return
    val bubbleFill = if (frame.target.hasBubble) c.bubble else c.bubble.copy(alpha = 0f)
    val baseColor = if (glass) c.glassTint ?: c.box else c.box
    val dropColor = lerp(baseColor, bubbleFill, frame.color)

    if (copy > 0f) {
        val box = frame.box
        if (!f.snapped && box != null) {
            budNeck(box.top - state.recoilPx(), box.left, box.right, frame.drop, m.snap, m.maxNeck, m.corner)?.let { neck ->
                drawPath(
                    neck.fill(m.overlap),
                    Brush.verticalGradient(listOf(dropColor, baseColor), startY = neck.topY, endY = neck.baseY),
                    alpha = copy,
                )
                if (!glass) drawPath(neck.sides(), c.border, alpha = copy, style = Stroke(m.hairline))
            }
        }
        val wobble = f.wobble.value
        val shown = frame.drop.scaledAroundCenter(1f + wobble, 1f - wobble)
        if (!glass) {
            drawRoundRect(dropColor, shown.topLeft, shown.size, CornerRadius(m.corner), alpha = copy)
        }
        val textColor = lerp(c.fieldText, if (frame.target.hasBubble) c.bubbleText else c.fieldText, frame.color)
        val scale = lerp(1f, end.layoutInput.style.fontSize.value / start.layoutInput.style.fontSize.value, frame.shape)
        // With no bubble the drop is the text's own box, which would shave the glyphs' edges.
        val clipRect = if (frame.target.hasBubble) shown else shown.inflate(m.bubblePaddingX)
        val clip = Path().apply { addRoundRect(RoundRect(clipRect, CornerRadius(m.corner))) }
        clipPath(clip) {
            f.words.forEachIndexed { i, range ->
                val layout = f.wordLayouts.getOrNull(i) ?: return@forEachIndexed
                val from = frame.sourceOrigin + start.getBoundingBox(range.first).topLeft - frame.sourceBubble.topLeft
                val to = frame.target.text.topLeft + end.getBoundingBox(range.first).topLeft - frame.target.bubble.topLeft
                val at = frame.drop.topLeft + lerp(from, to, frame.shape)
                withTransform({
                    translate(at.x, at.y)
                    scale(scale, scale, pivot = Offset.Zero)
                }) {
                    drawText(layout, color = textColor, alpha = copy)
                }
            }
        }
    }

    val sheen = f.sheen.value
    if (f.landed && sheen > 0f && sheen < 1f && frame.target.hasBubble) {
        val bubble = frame.target.bubble
        val band = max(bubble.height * SHEEN_BAND, 26f * m.density)
        val top = lerp(-band, bubble.height, sheen)
        val clip = Path().apply { addRoundRect(RoundRect(bubble, CornerRadius(m.corner))) }
        clipPath(clip) {
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, c.sheen, Color.Transparent),
                    startY = bubble.top + top,
                    endY = bubble.top + top + band,
                ),
                topLeft = Offset(bubble.left, bubble.top + top),
                size = Size(bubble.width, band),
            )
        }
    }

    val dots = state.rectOf(state.waitDots) ?: return
    val first = Offset(dots.left + m.dotRadius, dots.center.y)
    val dotStart = f.dotStart ?: return
    if (!f.splitting) {
        val at = Offset(lerp(dotStart.x, first.x, f.dotX.value), lerp(dotStart.y, first.y, f.dotY.value))
        drawCircle(c.dot, radius = lerp(m.dotRadius * 2f / 3f, m.dotRadius, f.dotY.value.coerceIn(0f, 1f)), center = at)
    } else {
        repeat(WAIT_DOTS) { i ->
            val to = Offset(first.x + i * m.dotStep, first.y)
            drawCircle(c.dot, radius = m.dotRadius, center = lerp(first, to, f.split.value))
        }
    }
}

private fun Rect.scaledAroundCenter(sx: Float, sy: Float): Rect {
    val w = width * sx
    val h = height * sy
    return Rect(center.x - w / 2, center.y - h / 2, center.x + w / 2, center.y + h / 2)
}

private fun smoothstep(from: Float, to: Float, v: Float): Float {
    val t = ((v - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

private const val RISE_MS = 240
private const val LAND_MS = 120
private const val TARGET_FRAMES = 4
private const val LIFT_DELAY_MS = 80L
private const val RISE_COLOR = 0.55f
private const val RISE_COLOR_FROM = 0.45f
private const val FLY_COLOR_RATE = 1.8f
private const val LANDING_KICK_AT = 0.95f
private const val SHEEN_MIN_MS = 380
private const val SHEEN_MAX_MS = 700
private const val SHEEN_MS_PER_DP = 1.2f
private const val SHEEN_BAND = 0.7f
private const val SHEEN_ALPHA_LIGHT = 0.45f
private const val SHEEN_ALPHA_DARK = 0.16f
private const val DOT_LEAVES_AT = 0.8f
private const val WAIT_DOTS = 3
