package com.garfiec.librechat.feature.chat.components.veil

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.Easing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.hypot
import kotlin.math.roundToInt

// The temporary-chat veil's geometry and timing (see ModeVeilState), kept free of Compose state so
// it can be unit-tested.

/** How far behind its radius a band [tail] long, with [lead] ahead of it, peaks at [peakAt]. */
internal fun bandPeakLag(tail: Float, lead: Float, peakAt: Float): Float = tail - peakAt * (tail + lead)

/** Whether an element at [distance] from the origin is behind the band's peak at [radius]. */
internal fun veilCrossed(distance: Float, radius: Float, peakLag: Float): Boolean = distance < radius - peakLag

internal fun veilShouldSweep(userToggle: Boolean, reduceMotion: Boolean): Boolean = userToggle && !reduceMotion

/** A full sweep's length, shortened to what is left when a tap reverses one mid-way. */
internal fun veilSweepMillis(from: Float, on: Boolean): Int {
    val remaining = if (on) 1f - from else from
    val full = if (on) SWEEP_ON_MS else SWEEP_OFF_MS
    return (full * remaining).roundToInt().coerceAtLeast(MIN_SWEEP_MS)
}

internal fun veilEasing(on: Boolean): Easing = if (on) EaseOutCubic else EaseInOutCubic

/**
 * When, in a full sweep, the band's peak crosses a point [distance] from the origin; null when it
 * never does. For tests and timing checks: the screen itself never schedules by time.
 */
internal fun veilArrivalMillis(distance: Float, maxRadius: Float, peakLag: Float, on: Boolean): Int? {
    val duration = if (on) SWEEP_ON_MS else SWEEP_OFF_MS
    val easing = veilEasing(on)
    val crossedAtEnd = veilCrossed(distance, maxRadius * (if (on) 1f else 0f), peakLag)
    if (!crossedAtEnd && on) return null
    for (step in 0..ARRIVAL_STEPS) {
        val t = step / ARRIVAL_STEPS.toFloat()
        val p = if (on) easing.transform(t) else 1f - easing.transform(t)
        if (veilCrossed(distance, p * maxRadius, peakLag) == on) return (t * duration).roundToInt()
    }
    return null
}

internal fun farthestCornerDistance(origin: Offset, bounds: Rect): Float = maxOf(
    hypot(origin.x - bounds.left, origin.y - bounds.top),
    hypot(origin.x - bounds.right, origin.y - bounds.top),
    hypot(origin.x - bounds.left, origin.y - bounds.bottom),
    hypot(origin.x - bounds.right, origin.y - bounds.bottom),
)

/** Material nudge: up 4dp, a 1dp overshoot past rest, settle. In units of [NudgeRise]. */
internal fun nudgeCurve(t: Float): Float = when {
    t <= 0f || t >= 1f -> 0f
    t < NUDGE_UP -> -EaseOutCubic.transform(t / NUDGE_UP)
    t < NUDGE_OVER -> -1f + (1f + OVERSHOOT) * EaseInOutCubic.transform((t - NUDGE_UP) / (NUDGE_OVER - NUDGE_UP))
    else -> OVERSHOOT * (1f - EaseOutCubic.transform((t - NUDGE_OVER) / (1f - NUDGE_OVER)))
}

private const val SWEEP_ON_MS = 560
private const val SWEEP_OFF_MS = 420
private const val MIN_SWEEP_MS = 160
private const val ARRIVAL_STEPS = 400
private const val NUDGE_UP = 0.3f
private const val NUDGE_OVER = 0.7f
private const val OVERSHOOT = 0.25f
