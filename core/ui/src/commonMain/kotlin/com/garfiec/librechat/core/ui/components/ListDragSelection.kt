package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * Press-drag-release across a list's rows, in place: press a row and drag up or down, and the
 * highlight springs from row to row under the finger; lifting over a row selects it, lifting off
 * every row does nothing. Taps are left to the rows' own clicks. The highlight is also the rows'
 * pressed state: it shows on the touched row at once and fades if the press ends as a tap or a
 * swipe, so a row should keep its click's press ripple off; [dragSelectRow] does that.
 *
 * Only a vertical drag is taken, so it can sit inside a horizontal swipe (a drawer): whichever axis
 * passes touch slop first wins. Holding past the long-press timeout takes the gesture whatever the
 * direction. Inside a vertical scroll or sheet, `holdOnly` leaves every drag that starts before the
 * hold to them, so only a hold starts the drag-select. Gestures a child consumes first (its own
 * drag) are left to it.
 *
 * The container applies [listDragSelection]; each selectable row applies [dragSelectRow] (or
 * [listDragTarget] beside its own click), and its leading icon [dragSelectRowIcon].
 */
@Stable
class ListDragSelection internal constructor(
    private val haptics: HapticFeedback,
    internal val highlight: DragHighlight,
) {
    internal var container: LayoutCoordinates? = null
    internal var color by mutableStateOf(Color.Unspecified)

    internal fun held() = haptics.performHapticFeedback(HapticFeedbackType.LongPress)
}

@Composable
fun rememberListDragSelection(): ListDragSelection {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = DRAG_HIGHLIGHT_ALPHA)
    return remember(scope, haptics) { ListDragSelection(haptics, DragHighlight(scope, haptics)) }
        .also { it.color = color }
}

private enum class Claim { Tap, Hold, Drag, Yield }

/**
 * On the layout holding the rows; draws the highlight behind them. With [state] null it does nothing.
 * [holdOnly] is for rows that scroll or sit in a sheet: a vertical drag before the hold is theirs.
 */
fun Modifier.listDragSelection(state: ListDragSelection?, holdOnly: Boolean = false): Modifier =
    if (state == null) this else dragSelectGesture(state, holdOnly)

private fun Modifier.dragSelectGesture(state: ListDragSelection, holdOnly: Boolean): Modifier =
    onPlaced { state.container = it }
        .drawBehind { drawHighlight(state.highlight, state.container, state.color) }
        .pointerInput(state, holdOnly) {
            val slop = viewConfiguration.touchSlop
            val holdMillis = viewConfiguration.longPressTimeoutMillis
            val highlight = state.highlight
            fun toScreen(local: Offset): Offset? =
                state.container?.takeIf { it.isAttached }?.localToScreen(local)?.takeIf { it.isSpecified }

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val onScreen = toScreen(down.position) ?: return@awaitEachGesture
                if (highlight.isExcluded(onScreen)) return@awaitEachGesture
                val start = highlight.targetAt(onScreen) ?: return@awaitEachGesture
                highlight.reset()
                // The pressed state, before the gesture is known to be a drag-select.
                highlight.hover(start, tick = false)

                // Main pass, so a child's own drag (the account avatar's swipe) consumes first and
                // wins, and this runs before the drawer's horizontal drag above it.
                val claim = withTimeoutOrNull(holdMillis) {
                    var result: Claim? = null
                    while (result == null) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                        val delta = change?.let { it.position - down.position } ?: Offset.Zero
                        result = when {
                            change == null || change.isConsumed -> Claim.Yield
                            !change.pressed -> Claim.Tap
                            !holdOnly && abs(delta.y) > slop && abs(delta.y) > abs(delta.x) -> {
                                change.consume()
                                Claim.Drag
                            }
                            abs(delta.x) > slop || (holdOnly && abs(delta.y) > slop) -> Claim.Yield
                            else -> null
                        }
                    }
                    result
                } ?: Claim.Hold

                if (claim == Claim.Hold || claim == Claim.Drag) {
                    if (claim == Claim.Hold) state.held()
                    // A hold released without moving is the row's own click, already on its way.
                    var dragging = claim == Claim.Drag
                    while (true) {
                        // Initial pass once claimed: a list's own scroll sits inside this modifier (a
                        // LazyColumn's), so in the Main pass it would see the drag first and scroll, or
                        // hand it to the sheet. Consumed here, neither starts.
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes
                            .firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (dragging && highlight.select() != null) change.consume()
                            break
                        }
                        if (!dragging && (change.position - down.position).getDistance() > slop) dragging = true
                        if (dragging) {
                            highlight.hover(toScreen(change.position)?.let(highlight::targetAt))
                            change.consume()
                        }
                    }
                }
                // A scroll that took the press fades it at once; anything else is a release.
                if (claim == Claim.Yield) highlight.hover(null) else highlight.release()
            }
        }

/**
 * On a control inside a drag-select row that acts on its own (an edit or delete button, a star): a
 * press that starts on it is the control's alone, with no row highlight and no drag-select.
 */
fun Modifier.dragSelectExclude(state: ListDragSelection?): Modifier = dragSelectExclude(state?.highlight)

/** On a selectable row inside [listDragSelection]: a drag that ends over it calls [onSelect]. */
fun Modifier.listDragTarget(state: ListDragSelection?, onSelect: () -> Unit): Modifier =
    dragHighlightTarget(state?.highlight, onSelect)

/**
 * A clickable row inside [listDragSelection]: its drag target, clipped to the highlight's shape
 * ([DragSelectRowShape]), and its click. The highlight is the row's pressed state, so the row's
 * [LocalIndication] shows focus and hover but no press ripple under it. With [state] null it is a
 * plain clickable row with the full indication. Pad the row's content inside this.
 */
fun Modifier.dragSelectRow(state: ListDragSelection?, role: Role? = null, onClick: () -> Unit): Modifier =
    listDragTarget(state, onClick)
        .clip(DragSelectRowShape)
        .clickable(
            interactionSource = null,
            indication = if (state == null) RowIndication.Full else RowIndication.PressFree,
            role = role,
            onClick = onClick,
        )

/** [LocalIndication], read by the node itself so [dragSelectRow] needn't be composable. */
private enum class RowIndication : IndicationNodeFactory {
    Full,
    PressFree,
    ;

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        LocalIndicationNode(if (this == PressFree) PressFreeSource(interactionSource) else interactionSource)
}

private class LocalIndicationNode(
    private val interactionSource: InteractionSource,
) : DelegatingNode(), CompositionLocalConsumerModifierNode, ObserverModifierNode {
    private var base: Indication? = null
    private var delegated: DelegatableNode? = null

    override fun onAttach() = update()

    override fun onObservedReadsChanged() = update()

    private fun update() {
        var current: Indication? = null
        observeReads { current = currentValueOf(LocalIndication) }
        if (current === base) return
        base = current
        delegated?.let { undelegate(it) }
        delegated = (current as? IndicationNodeFactory)?.create(interactionSource)?.also { delegate(it) }
    }
}

private class PressFreeSource(source: InteractionSource) : InteractionSource {
    override val interactions: Flow<Interaction> = source.interactions.filter { it !is PressInteraction }
}
