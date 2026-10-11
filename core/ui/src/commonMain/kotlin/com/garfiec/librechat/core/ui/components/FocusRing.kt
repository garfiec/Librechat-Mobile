package com.garfiec.librechat.core.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp

/**
 * TCHAT: the design's focus ring: a solid 2dp ring in `focus-ring`, 2dp outside the control and
 * following its [shape]. Shown for keyboard, switch-access and D-pad focus, never for touch. Put it
 * on the control's outer modifier; it watches focus anywhere inside (`hasFocus`).
 */
fun Modifier.focusRing(shape: Shape, color: Color): Modifier = this then FocusRingElement(shape, color)

private data class FocusRingElement(val shape: Shape, val color: Color) : ModifierNodeElement<FocusRingNode>() {
    override fun create() = FocusRingNode(shape, color)
    override fun update(node: FocusRingNode) {
        node.shape = shape
        node.color = color
        node.invalidateDraw()
    }
}

private class FocusRingNode(var shape: Shape, var color: Color) :
    Modifier.Node(), DrawModifierNode, FocusEventModifierNode, CompositionLocalConsumerModifierNode {

    private var focused = false

    override fun onFocusEvent(focusState: FocusState) {
        val now = focusState.hasFocus && currentValueOf(LocalInputModeManager).inputMode == InputMode.Keyboard
        if (now != focused) {
            focused = now
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!focused) return
        val gap = 2.dp.toPx()
        val stroke = 2.dp.toPx()
        val inset = gap + stroke / 2
        val ringSize = Size(size.width + inset * 2, size.height + inset * 2)
        val outline = shape.createOutline(ringSize, layoutDirection, this)
        translate(left = -inset, top = -inset) {
            drawOutline(outline, color = color, style = Stroke(width = stroke))
        }
    }
}
