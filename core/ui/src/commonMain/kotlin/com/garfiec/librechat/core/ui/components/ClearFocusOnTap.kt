package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Clears focus — dismissing the keyboard — on a tap that no child handled. Buttons, the text field
 * itself, and scroll drags consume their pointer events, so only taps on otherwise inert space fire.
 *
 * Apply at every composition root that can host a text field: the app root, and the root of each
 * Dialog/ModalBottomSheet containing one. Those render in their own window (Android) or platform
 * layer (iOS), which the app root's pointer input never sees. Gesture-by-gesture behaviour is
 * tabulated in core/ui/CLAUDE.md.
 */
fun Modifier.clearFocusOnTap(): Modifier = this then ClearFocusOnTapElement

private data object ClearFocusOnTapElement : ModifierNodeElement<ClearFocusOnTapNode>() {
    override fun create() = ClearFocusOnTapNode()
    override fun update(node: ClearFocusOnTapNode) = Unit
}

private class ClearFocusOnTapNode : DelegatingNode(), CompositionLocalConsumerModifierNode {
    init {
        delegate(
            SuspendingPointerInputModifierNode {
                // Not detectTapGestures: it only cancels when a child consumes the movement, so a
                // drag across inert space would still count as a tap.
                awaitEachGesture {
                    if (awaitUnhandledTap()) currentValueOf(LocalFocusManager).clearFocus()
                }
            },
        )
    }
}

/** True when one pointer goes down and up within touch slop with nothing consuming it. */
private suspend fun AwaitPointerEventScope.awaitUnhandledTap(): Boolean {
    val down = awaitFirstDown()
    while (true) {
        val change = awaitPointerEvent().changes.singleOrNull()
        val abandoned = change == null ||
            change.id != down.id ||
            change.isConsumed ||
            (change.position - down.position).getDistance() > viewConfiguration.touchSlop
        if (abandoned || change.changedToUp()) return !abandoned
    }
}
