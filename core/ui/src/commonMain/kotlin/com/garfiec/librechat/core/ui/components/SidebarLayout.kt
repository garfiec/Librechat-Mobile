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
