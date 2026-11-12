package com.garfiec.librechat.feature.chat.components.artifact

import kotlin.math.abs

/** The artifact's shares of the width the divider settles on: a third, a half, two thirds. */
internal val SplitDetents = listOf(1f / 3f, 1f / 2f, 2f / 3f)

private const val STEP_EPSILON = 0.001f

/**
 * The artifact pane's width at [fraction] of [available] (the width less the gutter), keeping the
 * chat and the artifact each at least [minPane] wide.
 */
internal fun splitPaneWidth(available: Float, fraction: Float, minPane: Float): Float =
    (available * fraction).coerceIn(minPane, (available - minPane).coerceAtLeast(minPane))

/** What letting go of the divider does. */
internal sealed interface SplitRelease {
    /** The divider stays, with the artifact at [fraction] of the width. */
    data class Settle(val fraction: Float) : SplitRelease

    /** The artifact closes. */
    data object Close : SplitRelease

    /** The artifact takes the whole window. */
    data object FullScreen : SplitRelease
}

/**
 * Where the divider goes when let go with the artifact [paneWidth] wide, as dragged: past a pane's
 * minimum the drag keeps counting, so it can be narrower than [minPane] or leave the chat narrower.
 * [overdrag] past the artifact's minimum closes it, past the chat's fills the window with it; within
 * [snap] of a detent it settles on the detent.
 */
internal fun splitRelease(
    paneWidth: Float,
    available: Float,
    minPane: Float,
    snap: Float,
    overdrag: Float,
): SplitRelease {
    if (paneWidth <= minPane - overdrag) return SplitRelease.Close
    if (available - paneWidth <= minPane - overdrag) return SplitRelease.FullScreen
    val max = (available - minPane).coerceAtLeast(minPane)
    val width = paneWidth.coerceIn(minPane, max)
    val detent = SplitDetents.map { it * available }
        .filter { it in minPane..max }
        .minByOrNull { abs(it - width) }
    val settled = if (detent != null && abs(detent - width) <= snap) detent else width
    return SplitRelease.Settle(settled / available)
}

/** The detent after [fraction] toward a wider ([wider]) or narrower artifact; [fraction] at the end. */
internal fun stepSplitDetent(fraction: Float, wider: Boolean): Float = if (wider) {
    SplitDetents.firstOrNull { it > fraction + STEP_EPSILON } ?: fraction
} else {
    SplitDetents.lastOrNull { it < fraction - STEP_EPSILON } ?: fraction
}
