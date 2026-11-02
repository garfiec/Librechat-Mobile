package com.garfiec.librechat.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Lets a screen inside the navigation drawer's content turn off the drawer's swipe while it is
 * shown. Consuming the drag doesn't keep the drawer out: Material's drawer starts on the event that
 * crosses its touch slop without checking whether a child consumed it, so a fast swipe pulls the
 * drawer in for a frame before it cancels.
 */
@Stable
class DrawerGestureLock {
    private var holders by mutableIntStateOf(0)

    /** Whether any composed [BlockDrawerGestures] is asking for the drawer's swipe to be off. */
    val isLocked: Boolean get() = holders > 0

    internal fun acquire() {
        holders++
    }

    internal fun release() {
        holders--
    }
}

/** Provided by the layout that owns a modal drawer. Null where there is none (sidebar layouts). */
val LocalDrawerGestureLock = staticCompositionLocalOf<DrawerGestureLock?> { null }

/** Turns off the drawer's swipe for as long as this is composed. */
@Composable
fun BlockDrawerGestures() {
    val lock = LocalDrawerGestureLock.current ?: return
    DisposableEffect(lock) {
        lock.acquire()
        onDispose { lock.release() }
    }
}
