package com.garfiec.librechat.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val SidebarWidth = 320.dp

private val MinContentWidth = 360.dp

// The compact-height window size class's upper bound.
private val MinSidebarWindowHeight = 480.dp

/**
 * Whether a window this size gets the sidebar layout, whose sidebar pushes the content aside, rather
 * than the phone layout's drawer over it. Both platform hosts decide with this, so they can't diverge.
 *
 * The sidebar has to leave the content a phone's width, or every screen beside it lays out narrower
 * than the smallest phone. A short window (a phone on its side) keeps the drawer: the sidebar's
 * header and footer would leave its list no room.
 */
fun usesSidebarLayout(windowWidth: Dp, windowHeight: Dp): Boolean =
    windowWidth - SidebarWidth >= MinContentWidth && windowHeight >= MinSidebarWindowHeight

/**
 * [usesSidebarLayout] for the current window. Read from the window rather than a size class so iPad
 * Split View / Slide Over, which resize the window without a rotation, flip the layout too.
 */
@Composable
fun windowUsesSidebarLayout(): Boolean {
    val window = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { usesSidebarLayout(window.width.toDp(), window.height.toDp()) }
}

/** How an open artifact is shown: a sheet over the chat, a pane beside it, or the whole window. */
enum class ArtifactPresentation { SHEET, SPLIT, FULL_SCREEN }

/** The narrowest either side of a split may be: the chat and the artifact each keep a small phone's width. */
val MinSplitPaneWidth = 320.dp

/** The space between the chat and the artifact in a split, which holds the divider's handle. */
val SplitGutterWidth = 24.dp

val MinSplitWindowWidth = MinSplitPaneWidth * 2 + SplitGutterWidth

/**
 * What the Automatic artifact style shows in a window this size, read from the window as
 * [usesSidebarLayout] is, never from the device type. A short window (a phone on its side) gets the
 * whole of it; one with room for the chat and the artifact side by side splits; any other gets the
 * sheet.
 */
fun automaticArtifactPresentation(windowWidth: Dp, windowHeight: Dp): ArtifactPresentation = when {
    windowHeight < MinSidebarWindowHeight -> ArtifactPresentation.FULL_SCREEN
    windowWidth >= MinSplitWindowWidth -> ArtifactPresentation.SPLIT
    else -> ArtifactPresentation.SHEET
}

/** [automaticArtifactPresentation] for the current window. */
@Composable
fun windowAutomaticArtifactPresentation(): ArtifactPresentation {
    val window = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { automaticArtifactPresentation(window.width.toDp(), window.height.toDp()) }
}

/**
 * Whether a split in a window this wide leaves room for a sidebar [sidebarWidth] wide too. If not,
 * the sidebar steps aside while the split is up.
 */
fun splitKeepsSidebar(windowWidth: Dp, sidebarWidth: Dp = SidebarWidth): Boolean =
    windowWidth - sidebarWidth >= MinSplitWindowWidth
