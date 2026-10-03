package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Swallows taps and drags that land in a floating bar's empty space, so they don't fall through to
 * the content scrolling behind it. Children are dispatched first, so the bar's own controls keep
 * their events; only what they leave unconsumed is swallowed here.
 *
 * Movement within touch slop is left alone: a clickable reads a consumed move as an ancestor taking
 * the gesture and cancels, so a held press on a bar button would never click. Nothing behind the bar
 * acts on movement that small.
 */
fun Modifier.consumeUnhandledTouches(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        var dragging = false
        do {
            val event = awaitPointerEvent()
            if (!dragging) {
                dragging = event.changes.any { (it.position - down.position).getDistance() > viewConfiguration.touchSlop }
            }
            event.changes.forEach { if (dragging || !it.pressed) it.consume() }
        } while (event.changes.any { it.pressed })
    }
}
