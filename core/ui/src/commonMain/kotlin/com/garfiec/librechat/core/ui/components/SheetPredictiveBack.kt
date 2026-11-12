package com.garfiec.librechat.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.PredictiveBackHandler
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

/** How far a sheet drawn in the app's own window has shrunk under a back gesture, 0 to 1. */
@Stable
class SheetPredictiveBack internal constructor() {
    internal val progress = Animatable(0f)

    /** Grows the sheet back; call when it stays up after a committed gesture or shows again. */
    suspend fun reset() {
        if (progress.value != 0f) progress.animateTo(0f, BackCancelSpec)
    }
}

/**
 * Back for a sheet drawn in the app's own window, with M3 `ModalBottomSheet`'s predictive-back
 * shrink: apply [predictiveBackSheet] to the surface and [predictiveBackSheetContent] to what it
 * holds. A cancelled gesture grows it back; a committed one calls [onBack] and leaves it shrunk
 * (the sheet slides away from there), so the caller [resets][SheetPredictiveBack.reset] it if the
 * sheet stays. Claims [LocalBackClaims] while [enabled], as [PlatformBackHandler] does.
 */
// PredictiveBackHandler is deprecated in favour of NavigationEventHandler, which only lands in a
// later Compose; MaterialMenuOverlay stays on it for the same reason.
@Suppress("DEPRECATION")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun rememberSheetPredictiveBack(
    enabled: Boolean,
    onBack: suspend SheetPredictiveBack.() -> Unit,
): SheetPredictiveBack {
    val back = remember { SheetPredictiveBack() }
    val currentOnBack by rememberUpdatedState(onBack)
    BackClaim(enabled)
    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { event -> back.progress.snapTo(PredictiveBackEasing.transform(event.progress)) }
            back.currentOnBack()
        } catch (cancellation: CancellationException) {
            back.progress.animateTo(0f, BackCancelSpec)
            throw cancellation
        }
    }
    return back
}

/** Shrinks the sheet toward its bottom edge. */
fun Modifier.predictiveBackSheet(back: SheetPredictiveBack): Modifier = graphicsLayer {
    val p = back.progress.value
    scaleX = backScaleX(p)
    scaleY = backScaleY(p)
    transformOrigin = TransformOrigin(0.5f, 1f)
}

/** Undoes the sheet's vertical squash on its content, so the content scales without distorting. */
fun Modifier.predictiveBackSheetContent(back: SheetPredictiveBack): Modifier = graphicsLayer {
    val p = back.progress.value
    val y = backScaleY(p)
    scaleY = if (y != 0f) backScaleX(p) / y else 1f
    transformOrigin = TransformOrigin(0.5f, 0f)
}

/**
 * Shrinks a panel standing beside the screen's content toward its end edge ([towardEnd]), or one
 * covering the whole screen toward its centre, rounding its corners as it goes. [rtl] puts the end
 * edge on the left.
 */
fun Modifier.predictiveBackPane(back: SheetPredictiveBack, towardEnd: Boolean, rtl: Boolean): Modifier = graphicsLayer {
    val p = back.progress.value
    val scale = 1f - (1f - MinPaneBackScale) * p
    scaleX = scale
    scaleY = scale
    transformOrigin = when {
        !towardEnd -> TransformOrigin.Center
        rtl -> TransformOrigin(0f, 0.5f)
        else -> TransformOrigin(1f, 0.5f)
    }
    if (p > 0f) {
        shape = RoundedCornerShape(PaneBackCorner * p)
        clip = true
    }
}

private fun GraphicsLayerScope.backScaleX(progress: Float): Float {
    val width = size.width
    if (width.isNaN() || width == 0f) return 1f
    return 1f - lerp(0f, min(MaxScaleXDistance.toPx(), width), progress) / width
}

private fun GraphicsLayerScope.backScaleY(progress: Float): Float {
    val height = size.height
    if (height.isNaN() || height == 0f) return 1f
    return 1f - lerp(0f, min(MaxScaleYDistance.toPx(), height), progress) / height
}

// M3's PredictiveBack easing and BottomSheet distances.
private val PredictiveBackEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)
private val MaxScaleXDistance = 48.dp
private val MaxScaleYDistance = 24.dp

// M3's full-screen predictive back: down to 90%, with the device's corner radius.
private const val MinPaneBackScale = 0.9f
private val PaneBackCorner = 28.dp
