package com.garfiec.librechat.core.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Swallows taps and drags that land in a floating bar's empty space, so they don't fall through to
 * the content scrolling behind it. Children are dispatched first, so the bar's own controls keep
 * their events; only what they leave unconsumed is swallowed here.
 */
fun Modifier.consumeUnhandledTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent().changes.forEach { it.consume() }
        }
    }
}
