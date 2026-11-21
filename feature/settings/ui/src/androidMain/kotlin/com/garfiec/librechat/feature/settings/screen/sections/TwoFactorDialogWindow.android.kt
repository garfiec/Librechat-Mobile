package com.garfiec.librechat.feature.settings.screen.sections

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.DialogProperties

internal actual fun twoFactorDialogProperties(): DialogProperties = DialogProperties(decorFitsSystemWindows = false)

internal actual fun Modifier.twoFactorDialogInsets(): Modifier = then(RelayoutOnResizeElement).safeDrawingPadding()

/**
 * The wrap-height window is first measured with the system-bar insets of a full-screen frame. Once
 * it is placed they drop to 0 and the card shrinks, but the window keeps its first height, with the
 * card drawn off-centre, until a view requests a layout. This node requests one when the card resizes.
 */
private data object RelayoutOnResizeElement : ModifierNodeElement<RelayoutOnResizeNode>() {
    override fun create() = RelayoutOnResizeNode()

    override fun update(node: RelayoutOnResizeNode) = Unit
}

private class RelayoutOnResizeNode : Modifier.Node(), LayoutAwareModifierNode {
    private var lastSize: IntSize? = null

    override fun onRemeasured(size: IntSize) {
        if (lastSize != null && lastSize != size) {
            val view = requireView()
            view.post { view.requestLayout() }
        }
        lastSize = size
    }
}
