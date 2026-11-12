package com.garfiec.librechat.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** A route whose screen needs the window's whole width: the tablet sidebar steps aside while it is on top. */
interface FullWidthRoute

/** Screens that need the window's whole width for a while, as a [FullWidthRoute] does for its stay. */
@Stable
class SidebarAsideRequests {
    private var count by mutableIntStateOf(0)

    /** Whether any screen asks for the sidebar to step aside now. */
    val requested: Boolean get() = count > 0

    internal fun add() {
        count++
    }

    internal fun remove() {
        count--
    }
}

/** Provided by the tablet layout; null where there is no sidebar to move. */
val LocalSidebarAsideRequests = staticCompositionLocalOf<SidebarAsideRequests?> { null }

/** Asks the tablet sidebar to step aside while [active] and this call is composed. */
@Composable
fun RequestSidebarAside(active: Boolean) {
    val requests = LocalSidebarAsideRequests.current ?: return
    DisposableEffect(requests, active) {
        if (active) requests.add()
        onDispose { if (active) requests.remove() }
    }
}
