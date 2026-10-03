package com.garfiec.librechat.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.modifier.ModifierLocalModifierNode
import androidx.compose.ui.modifier.modifierLocalMapOf
import androidx.compose.ui.modifier.modifierLocalOf
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Press-drag-release on a menu's opener, iOS style. A tap opens the menu on release. Holding presses
 * the opener in until the long-press timeout, then pops the menu open; dragging opens it at once.
 * With the menu open, a highlight springs between rows under the finger, the hovered row lifts, and
 * the panel leans toward the finger and stretches past its far edge. Lifting over a row selects it
 * (the menu folds into that row); lifting well away from the menu cancels it.
 *
 * The opener applies [menuDragAnchor] (and [menuDragPressEffect] on what should press in), the
 * menu takes the state through `AdaptiveDropdownMenu(dragSelection = …)`, and each selectable row
 * applies [menuDragTarget]. They meet in screen coordinates: the whole gesture stays with the opener
 * that took the down (and the popup fallback is a separate window), so rows are hit-tested here.
 */
@Stable
class MenuDragSelection internal constructor(
    private val scope: CoroutineScope,
    private val haptics: HapticFeedback,
) {
    internal var anchor: LayoutCoordinates? = null

    /** The menu panel's untransformed frame; set by the menu while it is open. */
    internal var panel: LayoutCoordinates? = null
    private val targets = mutableListOf<MenuDragTargetNode>()
    private var hovered: MenuDragTargetNode? = null

    /** The finger in screen coordinates while dragging, else null. */
    internal var finger by mutableStateOf<Offset?>(null)
        private set

    /** The finger is far enough from the menu that lifting it cancels. */
    internal var cancelArmed by mutableStateOf(false)
        private set

    /** The row a drag selected, in screen coordinates; the closing menu folds into it. */
    internal var selected by mutableStateOf<Rect?>(null)
        private set

    // The highlight, in screen coordinates. Its top and bottom ride separate springs so it stretches
    // as it travels: the leading edge is stiff, the trailing one lags.
    internal val highlightTop = Animatable(0f)
    internal val highlightBottom = Animatable(0f)
    internal var highlightLeft by mutableFloatStateOf(0f)
        private set
    internal var highlightRight by mutableFloatStateOf(0f)
        private set
    internal val highlightAlpha = Animatable(0f)

    /** The opener's press-in: 1 fully in, 0 at rest, below 0 swelling (a tap's open). */
    internal val press = Animatable(0f)

    /**
     * The menu is open — however it was opened. The opener is never pressed in or leaning while it
     * is: a hold or drag dips it and bounces it back up as the menu opens ([opened]); a plain tap
     * swells it and lets it settle back as the menu opens.
     */
    internal var menuOpen by mutableStateOf(false)
        private set

    /** This open came from a hold or drag ([opened]) rather than the opener's own tap. */
    private var openedByGesture = false

    internal fun menuOpenChanged(open: Boolean) {
        if (open == menuOpen) return
        menuOpen = open
        if (!open) {
            openedByGesture = false
            return
        }
        scope.launch { pull.animateTo(Offset.Zero, SettleSpec) }
        if (!openedByGesture) {
            scope.launch {
                press.animateTo(-1f, TapSwellSpec)
                press.animateTo(0f, PopSpec)
            }
        }
    }

    /** The opener's lean toward the finger, in px. */
    internal val pull = Animatable(Offset.Zero, Offset.VectorConverter)

    internal fun register(target: MenuDragTargetNode) {
        targets += target
    }

    internal fun unregister(target: MenuDragTargetNode) {
        targets -= target
        if (hovered === target) hover(null)
    }

    /** Clears the previous gesture's leftovers; the menu calls this as it opens. */
    internal fun reset() {
        hovered = null
        selected = null
        scope.launch { highlightAlpha.snapTo(0f) }
    }

    internal fun pressStart(holdMillis: Long) {
        if (menuOpen) return
        scope.launch { press.animateTo(1f, tween(holdMillis.toInt(), easing = LinearOutSlowInEasing)) }
    }

    internal fun opened(byHold: Boolean) {
        openedByGesture = true
        if (byHold) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch { pull.animateTo(Offset.Zero, SettleSpec) }
        scope.launch {
            // A drag can open the menu before the press has visibly gone in; dip it first so the
            // bounce back up always reads.
            if (press.value < DIP_THRESHOLD) press.animateTo(1f, DipSpec)
            press.animateTo(0f, PopSpec)
        }
    }

    internal fun lean(target: Offset) {
        if (openedByGesture || menuOpen) return
        scope.launch { pull.animateTo(target, TrackSpec) }
    }

    internal fun track(localToAnchor: Offset, cancelDistance: Float) {
        val onScreen = anchor?.takeIf { it.isAttached }?.localToScreen(localToAnchor) ?: return
        if (!onScreen.isSpecified) return
        finger = onScreen
        val frame = panel?.screenBounds()
        val armed = frame != null &&
            frame.distanceTo(onScreen) > cancelDistance &&
            anchor?.screenBounds()?.contains(onScreen) != true
        if (armed != cancelArmed) {
            cancelArmed = armed
            if (armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
        hover(if (armed) null else targets.firstOrNull { it.screenBounds()?.contains(onScreen) == true })
    }

    /** Ends a drag over a row: selects it and returns true. */
    internal fun release(): Boolean {
        val target = hovered ?: return false
        val bounds = target.screenBounds() ?: return false
        hovered = null
        selected = bounds
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        target.onSelect()
        return true
    }

    internal fun finish() {
        finger = null
        cancelArmed = false
        if (selected == null) hover(null)
        // A hold or drag already started the opener's bounce; letting go mustn't restart it.
        if (openedByGesture) return
        scope.launch { press.animateTo(0f, PopSpec) }
        scope.launch { pull.animateTo(Offset.Zero, SettleSpec) }
    }

    private fun hover(target: MenuDragTargetNode?) {
        if (target === hovered) return
        hovered = target
        val bounds = target?.screenBounds()
        if (bounds == null) {
            scope.launch { highlightAlpha.animateTo(0f, tween(HIGHLIGHT_FADE_MILLIS)) }
            return
        }
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        highlightLeft = bounds.left
        highlightRight = bounds.right
        if (highlightAlpha.targetValue == 0f) {
            scope.launch {
                highlightTop.snapTo(bounds.top)
                highlightBottom.snapTo(bounds.bottom)
            }
        } else {
            val down = bounds.top > highlightTop.targetValue
            scope.launch { highlightTop.animateTo(bounds.top, if (down) TrailSpec else LeadSpec) }
            scope.launch { highlightBottom.animateTo(bounds.bottom, if (down) LeadSpec else TrailSpec) }
        }
        scope.launch { highlightAlpha.animateTo(1f, tween(HIGHLIGHT_FADE_MILLIS)) }
    }
}

@Composable
fun rememberMenuDragSelection(): MenuDragSelection {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    return remember(scope, haptics) { MenuDragSelection(scope, haptics) }
}

private enum class HoldOutcome { Tap, Hold, Drag, Cancel }

/**
 * On the opener (the layout the menu anchors to). [onOpen] opens the menu on a hold or a drag; a tap
 * is left to the opener's own click. Watches in the initial pass without consuming the down; once
 * the finger drags past touch slop the moves are consumed, which cancels that click. [onCancel]
 * closes the menu when a drag is released far from it.
 */
fun Modifier.menuDragAnchor(
    state: MenuDragSelection,
    enabled: Boolean = true,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
): Modifier {
    if (!enabled) return this
    return onPlaced { state.anchor = it }.pointerInput(state) {
        val slop = viewConfiguration.touchSlop
        val holdMillis = viewConfiguration.longPressTimeoutMillis
        val leanCap = LeanCap.toPx()
        val cancelDistance = CancelDistance.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val center = Offset(size.width / 2f, size.height / 2f)
            fun PointerInputChange.leanToward() = state.lean(rubberVector(position - center, leanCap, ANCHOR_LEAN_SOFTNESS))

            state.pressStart(holdMillis)
            val outcome = withTimeoutOrNull(holdMillis) {
                var result: HoldOutcome? = null
                while (result == null) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    result = when {
                        change == null -> HoldOutcome.Cancel
                        !change.pressed -> HoldOutcome.Tap
                        (change.position - down.position).getDistance() > slop -> {
                            change.consume()
                            HoldOutcome.Drag
                        }
                        else -> {
                            change.leanToward()
                            null
                        }
                    }
                }
                result
            } ?: HoldOutcome.Hold

            if (outcome == HoldOutcome.Hold || outcome == HoldOutcome.Drag) {
                onOpen()
                state.opened(byHold = outcome == HoldOutcome.Hold)
                var dragging = outcome == HoldOutcome.Drag
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (dragging) {
                            val armed = state.cancelArmed
                            if (state.release()) {
                                change.consume()
                            } else if (armed) {
                                change.consume()
                                onCancel()
                            }
                        }
                        break
                    }
                    change.leanToward()
                    if (!dragging && (change.position - down.position).getDistance() > slop) dragging = true
                    if (dragging) {
                        state.track(change.position, cancelDistance)
                        change.consume()
                    }
                }
            }
            state.finish()
        }
    }
}

/** On the opener's visible chip: presses in while held and leans toward the finger. */
fun Modifier.menuDragPressEffect(state: MenuDragSelection): Modifier = graphicsLayer {
    val scale = 1f - PRESS_DEPTH * state.press.value
    scaleX = scale
    scaleY = scale
    val pull = state.pull.value
    translationX = pull.x
    translationY = pull.y
}

/** On a selectable menu row: a drag that ends over it calls [onSelect]; it lifts while highlighted. */
fun Modifier.menuDragTarget(state: MenuDragSelection?, onSelect: () -> Unit): Modifier =
    if (state == null) this else this then MenuDragTargetElement(state, onSelect)

/** On a row's leading icon, inside a [menuDragTarget] row: grows while the row is highlighted. */
fun Modifier.menuDragRowIcon(): Modifier = this then MenuDragRowIconElement

private val ModifierLocalRowLift = modifierLocalOf<() -> Float> { { 0f } }

private data class MenuDragTargetElement(
    val state: MenuDragSelection,
    val onSelect: () -> Unit,
) : ModifierNodeElement<MenuDragTargetNode>() {
    override fun create() = MenuDragTargetNode(state, onSelect)

    override fun update(node: MenuDragTargetNode) {
        if (node.state !== state) {
            node.state.unregister(node)
            node.state = state
            if (node.isAttached) state.register(node)
        }
        node.onSelect = onSelect
    }
}

/**
 * A row's lift is how much of the highlight covers it, times the highlight's opacity — so it rides
 * the highlight's springs rather than animating on its own.
 */
internal class MenuDragTargetNode(
    var state: MenuDragSelection,
    var onSelect: () -> Unit,
) : Modifier.Node(), LayoutModifierNode, GlobalPositionAwareModifierNode, ModifierLocalModifierNode {
    private var coordinates: LayoutCoordinates? = null

    override val providedValues = modifierLocalMapOf(ModifierLocalRowLift to { lift() })

    override fun onAttach() = state.register(this)

    override fun onDetach() {
        state.unregister(this)
        coordinates = null
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
    }

    fun screenBounds(): Rect? = coordinates?.screenBounds()

    fun lift(): Float {
        val alpha = state.highlightAlpha.value
        if (alpha == 0f) return 0f
        val bounds = screenBounds() ?: return 0f
        if (bounds.height <= 0f) return 0f
        val overlap = min(state.highlightBottom.value, bounds.bottom) - max(state.highlightTop.value, bounds.top)
        return alpha * (overlap / bounds.height).coerceIn(0f, 1f)
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        val direction = if (layoutDirection == LayoutDirection.Ltr) 1f else -1f
        val nudge = RowNudge.toPx() * direction
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                val lift = lift()
                translationX = lift * nudge
                alpha = 1f - ROW_DIM * state.highlightAlpha.value * (1f - lift)
            }
        }
    }
}

private object MenuDragRowIconElement : ModifierNodeElement<MenuDragRowIconNode>() {
    override fun create() = MenuDragRowIconNode()
    override fun update(node: MenuDragRowIconNode) = Unit
    override fun hashCode() = 0
    override fun equals(other: Any?) = other === this
}

private class MenuDragRowIconNode : Modifier.Node(), LayoutModifierNode, ModifierLocalModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                val scale = 1f + ICON_GROWTH * ModifierLocalRowLift.current()
                scaleX = scale
                scaleY = scale
            }
        }
    }
}

internal fun LayoutCoordinates.screenBounds(): Rect? {
    if (!isAttached) return null
    val topLeft = localToScreen(Offset.Zero)
    if (!topLeft.isSpecified) return null
    return Rect(topLeft, localToScreen(Offset(size.width.toFloat(), size.height.toFloat())))
}

private fun Rect.distanceTo(point: Offset): Float {
    val dx = max(max(left - point.x, 0f), point.x - right)
    val dy = max(max(top - point.y, 0f), point.y - bottom)
    return sqrt(dx * dx + dy * dy)
}

/**
 * iOS-style rubber band: grows ~linearly for small [distance], approaches [cap] for large ones.
 * Lower [softness] needs more travel to reach the cap.
 */
internal fun rubberBand(distance: Float, cap: Float, softness: Float): Float =
    if (distance <= 0f) 0f else cap * (1f - 1f / (distance * softness / cap + 1f))

internal fun rubberVector(vector: Offset, cap: Float, softness: Float): Offset {
    val length = vector.getDistance()
    if (length == 0f) return Offset.Zero
    return vector * (rubberBand(length, cap, softness) / length)
}

private val LeanCap = 4.dp
private val CancelDistance = 48.dp
private val RowNudge = 4.dp
private const val ANCHOR_LEAN_SOFTNESS = 0.1f
private const val PRESS_DEPTH = 0.08f
private const val ROW_DIM = 0.15f
private const val ICON_GROWTH = 0.15f
private const val HIGHLIGHT_FADE_MILLIS = 100

private val LeadSpec = spring<Float>(dampingRatio = 0.85f, stiffness = 1400f)
private val TrailSpec = spring<Float>(dampingRatio = 0.85f, stiffness = 500f)
private val TrackSpec = spring<Offset>(dampingRatio = 0.9f, stiffness = 1500f)
private val SettleSpec = spring<Offset>(dampingRatio = 0.6f, stiffness = 500f)

/** Underdamped, so the released opener pops back past rest before settling. */
private val PopSpec = spring<Float>(dampingRatio = 0.45f, stiffness = 600f)

private val DipSpec = tween<Float>(durationMillis = 90, easing = LinearOutSlowInEasing)
private const val DIP_THRESHOLD = 0.5f

/** A tap's swell (press -1 = [PRESS_DEPTH] larger) before [PopSpec] settles it. */
private val TapSwellSpec = tween<Float>(durationMillis = 110, easing = LinearOutSlowInEasing)
