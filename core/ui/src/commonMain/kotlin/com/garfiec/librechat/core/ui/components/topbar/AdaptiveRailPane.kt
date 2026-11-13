package com.garfiec.librechat.core.ui.components.topbar

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.garfiec.librechat.core.ui.platform.LocalWindowFeatures
import com.garfiec.librechat.core.ui.platform.VerticalBars

/** A layout's own [width] (px, untransformed) and whether it [reaches] the bar side, as [reachesBarSide] measured it. */
internal data class BarSideReach(val width: Float, val reaches: Boolean)

/**
 * Whether a layout that measured [own] reaches the bar side. One as wide as the [pane] it is drawn in
 * takes the pane's answer: the pane is measured outside the navigation's transition layers, so a page
 * a back swipe draws scaled and offset keeps the rail it has once revealed. A narrower one (a pane
 * nested in a screen) keeps its own.
 */
internal fun reachesBarSide(own: BarSideReach, pane: BarSideReach?): Boolean =
    if (pane != null && own.width >= pane.width - EDGE_SLOP) pane.reaches else own.reaches

/** Measures [BarSideReach] on the layout the returned modifier is on, on every global move, while there are [bars]. */
@Composable
internal fun rememberBarSideReach(bars: VerticalBars?): Pair<State<BarSideReach>, Modifier> {
    // Unknown until placed; assumed, so a full-width screen doesn't open with a horizontal bar first.
    val reach = remember { mutableStateOf(BarSideReach(width = 0f, reaches = true)) }
    val currentBars by rememberUpdatedState(bars)
    val modifier = if (bars != null) {
        remember {
            Modifier.onGloballyPositioned { coordinates ->
                val side = currentBars?.side ?: return@onGloballyPositioned
                val width = coordinates.size.width.toFloat()
                val windowWidth = coordinates.findRootCoordinates().size.width.toFloat()
                reach.value = BarSideReach(width, reachesBarSide(side, coordinates.positionInRoot().x, width, windowWidth))
            }
        }
    } else {
        Modifier
    }
    return reach to modifier
}

/** The pane the app's navigation draws its screens in, for [rememberRailPlacement]; null outside one. */
internal val LocalRailPane = staticCompositionLocalOf<State<BarSideReach>?> { null }

/**
 * The pane beside the app's sidebar that the navigation draws its screens in. Place it outside the
 * navigation's transitions: a screen as wide as it then decides its rail from where the pane is laid
 * out, not from where a transition's layer draws the screen.
 */
@Composable
fun AdaptiveRailPane(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val (reach, measure) = rememberBarSideReach(LocalWindowFeatures.current.verticalBars)
    Box(modifier.then(measure), propagateMinConstraints = true) {
        CompositionLocalProvider(LocalRailPane provides reach, content = content)
    }
}
