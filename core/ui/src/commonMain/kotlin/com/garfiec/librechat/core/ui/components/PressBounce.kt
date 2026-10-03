package com.garfiec.librechat.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * A button that answers the finger: it swells while touched, however long it's held, and on release
 * springs back to size. Only watches the pointer, so the button's own click still fires.
 */
@Stable
class PressBounce internal constructor(private val scope: CoroutineScope) {
    /** 1 = swollen, 0 = rest; [BounceSpec] overshoots below 0 as it settles. */
    internal val press = Animatable(0f)

    internal fun down() {
        scope.launch { press.animateTo(1f, SwellSpec) }
    }

    internal fun release() {
        scope.launch {
            // A quick tap lifts before the swell shows; finish it so the bounce reads.
            if (press.value < DIP_THRESHOLD) press.animateTo(1f, GrowSpec)
            press.animateTo(0f, BounceSpec)
        }
    }
}

@Composable
fun rememberPressBounce(): PressBounce {
    val scope = rememberCoroutineScope()
    return remember(scope) { PressBounce(scope) }
}

/** On the button's visible chip. With [enabled] false it does nothing. */
fun Modifier.pressBounce(state: PressBounce, enabled: Boolean = true): Modifier {
    if (!enabled) return this
    return pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            state.down()
            // Not waitForUpOrCancellation: it ends on any consumed or out-of-bounds move, and the
            // floating bar consumes drags its children leave. Only the finger lifting ends this.
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
            } while (event.changes.any { it.pressed })
            state.release()
        }
    }.graphicsLayer {
        // Capped in dp, so a wide button doesn't grow over its neighbours.
        val depth = min(SWELL_DEPTH, MaxSwell.toPx() / maxOf(size.width, size.height, 1f))
        val scale = 1f + depth * state.press.value
        scaleX = scale
        scaleY = scale
    }
}

private const val SWELL_DEPTH = 0.15f
private val MaxSwell = 12.dp
private const val DIP_THRESHOLD = 0.5f

private val SwellSpec = tween<Float>(durationMillis = 120, easing = LinearOutSlowInEasing)
private val GrowSpec = tween<Float>(durationMillis = 90, easing = LinearOutSlowInEasing)
private val BounceSpec = spring<Float>(dampingRatio = 0.45f, stiffness = 600f)
