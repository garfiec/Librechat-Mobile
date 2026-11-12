package com.garfiec.librechat.feature.chat.components.artifact

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.garfiec.librechat.core.ui.components.AdaptivePanelSurface
import com.garfiec.librechat.core.ui.components.ArtifactPresentation
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.core.ui.components.MinSplitPaneWidth
import com.garfiec.librechat.core.ui.components.PanelPlacement
import com.garfiec.librechat.core.ui.components.SidebarWidth
import com.garfiec.librechat.core.ui.components.SplitGutterWidth
import com.garfiec.librechat.core.ui.components.predictiveBackPane
import com.garfiec.librechat.core.ui.components.predictiveBackSheet
import com.garfiec.librechat.core.ui.components.predictiveBackSheetContent
import com.garfiec.librechat.core.ui.components.rememberSheetPredictiveBack
import com.garfiec.librechat.core.ui.components.splitKeepsSidebar
import com.garfiec.librechat.core.ui.components.topbar.CoversNativeBars
import com.garfiec.librechat.core.ui.components.windowAutomaticArtifactPresentation
import com.garfiec.librechat.core.ui.glass.glassBackdropSource
import com.garfiec.librechat.core.ui.glass.rememberGlassBackdrop
import com.garfiec.librechat.core.ui.glass.rememberGlassStyle
import com.garfiec.librechat.core.ui.navigation.RequestSidebarAside
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.artifact_narrower
import com.garfiec.librechat.feature.chat.resources.artifact_wider
import com.garfiec.librechat.feature.chat.resources.cd_resize_artifact
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What the viewer in an [ArtifactPresentationHost] offers in its current shape. */
internal data class ArtifactViewerControls(
    val inSheet: Boolean,
    /** Null where it already fills the window. */
    val onExpand: (() -> Unit)?,
    /** Non-null only when expanded from another shape. */
    val onCollapse: (() -> Unit)?,
)

/** What an [ArtifactPresentationHost] has open, and how it stands. */
@Stable
internal class ArtifactPresentationState {
    var entry by mutableStateOf<ArtifactViewerHandoff.Entry?>(null)
        private set

    /** The user asked for the whole window from the header. */
    var expanded by mutableStateOf(false)

    var sheetFull by mutableStateOf(false)

    /** Opens [entry], or swaps it in for the open one, which keeps its shape. */
    fun show(entry: ArtifactViewerHandoff.Entry, opensFull: Boolean) {
        if (this.entry == null) {
            expanded = false
            sheetFull = opensFull
        }
        this.entry = entry
    }

    fun close() {
        entry = null
    }
}

/**
 * Lays [content] (the chat) out with the open artifact as a sheet over it, a pane beside it or the
 * whole window: [automatic] picks by the window ([windowAutomaticArtifactPresentation]), otherwise
 * always the sheet; the header's expand takes the whole window in any of them.
 *
 * The viewer is composed once, in this window, whatever its shape: a rotation, fold, resize or expand
 * only moves it, so its web page never reloads. Never present it from another window or route.
 * A change of shape never makes it smaller than the user had it: a sheet after a wider shape stands
 * at full height.
 *
 * [splitFraction] is the artifact's share of the width beside the chat; a drag of the divider
 * reports where it settled to [onSplitFractionChange]. [origin] is where the artifact was tapped, so
 * the pane grows out of the card and shrinks back into it. [panelScope] wraps the panel and lives as
 * long as this host, for what must outlast one opening (a download that lands after it closed).
 */
@Composable
internal fun ArtifactPresentationHost(
    state: ArtifactPresentationState,
    automatic: Boolean,
    splitFraction: Float,
    onSplitFractionChange: (Float) -> Unit,
    origin: ArtifactOrigin,
    panelScope: @Composable (panel: @Composable () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    viewer: @Composable (
        shown: ArtifactViewerHandoff.Entry,
        controls: ArtifactViewerControls,
        modifier: Modifier,
    ) -> Unit = { shown, controls, viewerModifier ->
        ArtifactViewer(
            artifact = shown.artifact,
            versions = shown.versions,
            inSheet = controls.inSheet,
            onClose = state::close,
            onExpand = controls.onExpand,
            onCollapse = controls.onCollapse,
            modifier = viewerModifier,
        )
    },
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val windowWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val windowShape = if (automatic) windowAutomaticArtifactPresentation() else ArtifactPresentation.SHEET
    val presentation = if (state.expanded) ArtifactPresentation.FULL_SCREEN else windowShape
    val entry = state.entry
    // Kept through the closing motion, after the entry itself has gone.
    var lingering by remember { mutableStateOf<ArtifactViewerHandoff.Entry?>(null) }
    val displayed = entry ?: lingering
    val motion = remember { HostMotion() }
    val scope = rememberCoroutineScope()

    SideEffect {
        if (entry != null) lingering = entry
        val previous = motion.lastShape
        if (displayed == null) {
            motion.lastShape = null
        } else {
            if (presentation == ArtifactPresentation.SHEET && previous != null && previous != ArtifactPresentation.SHEET) {
                state.sheetFull = true
            }
            motion.lastShape = presentation
        }
    }
    val imeUp = WindowInsets.ime.getBottom(density) > 0
    if (imeUp && entry != null && presentation == ArtifactPresentation.SHEET && !state.sheetFull) {
        SideEffect { state.sheetFull = true }
    }

    // The glass sidebar is wider by a start cutout, as the tablet layout draws it.
    val sidebarWidth = if (rememberGlassStyle() != null) {
        SidebarWidth + WindowInsets.safeDrawing.asPaddingValues().calculateStartPadding(LocalLayoutDirection.current)
    } else {
        SidebarWidth
    }
    RequestSidebarAside(
        active = entry != null && (
            presentation == ArtifactPresentation.FULL_SCREEN ||
                presentation == ArtifactPresentation.SPLIT && !splitKeepsSidebar(windowWidth, sidebarWidth)
            ),
    )
    // A sheet or the whole window covers the screen's own bars; a pane beside the chat leaves them.
    CoversNativeBars(active = displayed != null && presentation != ArtifactPresentation.SPLIT)

    val back = rememberSheetPredictiveBack(enabled = entry != null) {
        if (state.expanded && windowShape != ArtifactPresentation.FULL_SCREEN) {
            state.expanded = false
            reset()
        } else {
            state.close()
        }
    }

    // Opening grows the pane out of the card (a sheet rises instead); closing shrinks it back into
    // the card once the chat has widened, or slides it out when the card has left the screen.
    val isOpen = entry != null
    val currentPresentation by rememberUpdatedState(presentation)
    // Taken once per artifact shown: one opened from an inline preview sets no card, and must not
    // grow out of the last card tapped.
    LaunchedEffect(entry) {
        if (entry != null) {
            motion.card = origin.bounds
            origin.bounds = null
        }
    }
    LaunchedEffect(isOpen) {
        if (isOpen) {
            back.reset()
            // A close cut short by this opening leaves its motion behind.
            motion.anchor = null
            motion.fadeContent = false
            motion.dragged = 0f
            val card = motion.card?.invoke()?.let(motion::toHost)
            if (card != null && currentPresentation != ArtifactPresentation.SHEET) {
                motion.anchor = card
                motion.fadeContent = true
                motion.shown.snapTo(1f)
                motion.morph.snapTo(0f)
                motion.morph.animateTo(1f, MorphOpenSpec)
                motion.anchor = null
            } else {
                motion.morph.snapTo(1f)
                motion.shown.animateTo(1f, ShowSpec)
            }
        } else if (lingering != null) {
            // A frame for the chat to take its full width, so the card is where it will stay.
            withFrameNanos {}
            val card = motion.card?.invoke()?.let(motion::toHost)?.takeIf { motion.isOnScreen(it) }
            if (card != null && motion.lastPlacedShape != ArtifactPresentation.SHEET) {
                motion.anchor = card
                motion.fadeContent = true
                motion.morph.animateTo(0f, MorphCloseSpec)
            } else {
                motion.shown.animateTo(0f, HideSpec)
            }
            lingering = null
            motion.anchor = null
            motion.fadeContent = false
            motion.morph.snapTo(1f)
            motion.shown.snapTo(0f)
            motion.dragged = 0f
            motion.card = null
        }
    }
    // Expanding grows the pane from where it stood. Other changes of shape (a rotation, a fold, a
    // collapse) just land in the new one; the system already animates a rotation.
    LaunchedEffect(state.expanded) {
        val from = motion.lastPlacedRect
        if (state.expanded && entry != null && from != null) {
            motion.anchor = from
            motion.fadeContent = false
            motion.morph.snapTo(0f)
            motion.morph.animateTo(1f, MorphReshapeSpec)
            motion.anchor = null
        }
    }

    val backdrop = rememberGlassBackdrop()
    val haptics = LocalHapticFeedback.current
    var committedFraction by remember { mutableFloatStateOf(splitFraction) }
    LaunchedEffect(splitFraction) { committedFraction = splitFraction }
    val settleFraction: (Float) -> Unit = { fraction ->
        committedFraction = fraction
        onSplitFractionChange(fraction)
    }
    val expand = remember(state) { { state.expanded = true } }
    val collapse = remember(state) { { state.expanded = false } }
    val splitOpen = displayed != null && presentation == ArtifactPresentation.SPLIT
    // Only a sheet lies over the chat; beside it or over everything there is nothing of it to sample.
    val sampledBackdrop = backdrop.takeIf { displayed != null && presentation == ArtifactPresentation.SHEET }
    val chatHidden = entry != null && presentation != ArtifactPresentation.SPLIT
    val safeDrawing = WindowInsets.safeDrawing

    Layout(
        modifier = modifier
            .fillMaxSize()
            .onPlaced { motion.host = it }
            .then(if (splitOpen) Modifier.background(MaterialTheme.colorScheme.background) else Modifier),
        content = {
            Box(
                Modifier
                    .layoutId(Slot.Chat)
                    .glassBackdropSource(sampledBackdrop)
                    // A sheet or the whole window is modal: what's under it is out of reach.
                    .then(if (chatHidden) Modifier.clearAndSetSemantics {} else Modifier),
            ) { content() }
            if (displayed != null && presentation == ArtifactPresentation.SHEET) {
                Box(
                    Modifier
                        .layoutId(Slot.Scrim)
                        .graphicsLayer { alpha = motion.shown.value * motion.morph.value }
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = ScrimAlpha))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown()
                                if (waitForUpOrCancellation() != null) state.close()
                            }
                        },
                )
            }
            if (splitOpen) {
                SplitDivider(
                    dragging = motion.dragPaneWidth != null,
                    onDrag = { delta ->
                        val start = motion.dragPaneWidth ?: motion.paneWidth
                        motion.dragPaneWidth = start - delta * (if (rtl) -1f else 1f)
                    },
                    onDragStop = {
                        val raw = motion.dragPaneWidth
                        motion.dragPaneWidth = null
                        if (raw != null) {
                            when (val release = with(density) { motion.release(raw, this) }) {
                                is SplitRelease.Settle -> {
                                    val snapped = release.fraction * motion.available
                                    if (abs(snapped - raw) > 0.5f) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    settleFraction(release.fraction)
                                }
                                SplitRelease.Close -> state.close()
                                SplitRelease.FullScreen -> state.expanded = true
                            }
                        }
                    },
                    onDoubleTap = { settleFraction(HALF) },
                    onStep = { wider -> settleFraction(stepSplitDetent(motion.fraction(committedFraction), wider)) },
                    modifier = Modifier.layoutId(Slot.Divider),
                )
            }
            panelScope {
                if (displayed != null) {
                    val placement = when (presentation) {
                        ArtifactPresentation.SHEET -> PanelPlacement.BOTTOM_SHEET
                        ArtifactPresentation.SPLIT -> PanelPlacement.SIDE
                        ArtifactPresentation.FULL_SCREEN -> PanelPlacement.WHOLE
                    }
                    val backTransform = when (presentation) {
                        ArtifactPresentation.SHEET -> Modifier.predictiveBackSheet(back)
                        ArtifactPresentation.SPLIT -> Modifier.predictiveBackPane(back, towardEnd = true, rtl = rtl)
                        ArtifactPresentation.FULL_SCREEN -> Modifier.predictiveBackPane(back, towardEnd = false, rtl = rtl)
                    }
                    AdaptivePanelSurface(
                        placement = placement,
                        backdrop = sampledBackdrop,
                        modifier = Modifier
                            .layoutId(Slot.Panel)
                            .then(backTransform)
                            .draggable(
                                state = rememberDraggableState { delta ->
                                    motion.dragged = max(motion.dragged + delta, motion.minDrag)
                                },
                                orientation = Orientation.Vertical,
                                enabled = presentation == ArtifactPresentation.SHEET && entry != null,
                                onDragStopped = { velocity ->
                                    val dismiss = motion.dragged > motion.sheetVisibleHeight * DISMISS_FRACTION ||
                                        velocity > with(density) { DismissVelocity.toPx() }
                                    when {
                                        dismiss -> state.close()
                                        !state.sheetFull && motion.dragged < -motion.halfTravel * RAISE_FRACTION -> {
                                            // Stands at full height now: carry the drag over so it doesn't jump.
                                            motion.dragged += motion.halfTravel
                                            state.sheetFull = true
                                            scope.launch { motion.settleDrag() }
                                        }
                                        else -> scope.launch { motion.settleDrag() }
                                    }
                                },
                            ),
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .then(
                                    if (presentation == ArtifactPresentation.SHEET) Modifier.predictiveBackSheetContent(back) else Modifier,
                                )
                                .graphicsLayer { alpha = motion.contentAlpha() },
                        ) {
                            if (presentation == ArtifactPresentation.SHEET) {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) { LowProfileDragHandle() }
                            }
                            // Another artifact tapped while one is open crosses over to it.
                            Crossfade(targetState = displayed, modifier = Modifier.weight(1f), label = "artifact") { shown ->
                                viewer(
                                    shown,
                                    ArtifactViewerControls(
                                        inSheet = presentation == ArtifactPresentation.SHEET,
                                        onExpand = expand.takeIf { presentation != ArtifactPresentation.FULL_SCREEN },
                                        onCollapse = collapse.takeIf {
                                            state.expanded && windowShape != ArtifactPresentation.FULL_SCREEN
                                        },
                                    ),
                                    Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val gutter = SplitGutterWidth.roundToPx()
        val minPane = MinSplitPaneWidth.toPx()
        motion.available = (width - gutter).toFloat()
        val livePane = motion.dragPaneWidth
        motion.paneWidth = splitPaneWidth(motion.available, committedFraction, minPane)
        val paneWidth = (livePane?.coerceIn(minPane, max(minPane, motion.available - minPane)) ?: motion.paneWidth)
            .roundToInt()
        // The chat narrows once, as the pane opens, and widens first as it closes.
        val chatWidth = if (entry != null && presentation == ArtifactPresentation.SPLIT) {
            // Under the split's minimum only while the sidebar is still stepping aside.
            (width - gutter - paneWidth).coerceAtLeast(0)
        } else {
            width
        }
        val chat = measurables.first { it.layoutId == Slot.Chat }.measure(Constraints.fixed(chatWidth, height))
        val full = Constraints.fixed(width, height)
        val scrim = measurables.firstOrNull { it.layoutId == Slot.Scrim }?.measure(full)
        val divider = measurables.firstOrNull { it.layoutId == Slot.Divider }?.measure(Constraints.fixed(gutter, height))

        val sheetTop = safeDrawing.getTop(this)
        val sheetWidth = min(width, ArtifactSheetMaxWidth.roundToPx())
        val target = when (presentation) {
            ArtifactPresentation.SPLIT -> Rect(Offset((width - paneWidth).toFloat(), 0f), Size(paneWidth.toFloat(), height.toFloat()))
            ArtifactPresentation.SHEET -> Rect(
                Offset(((width - sheetWidth) / 2).toFloat(), sheetTop.toFloat()),
                Size(sheetWidth.toFloat(), (height - sheetTop).toFloat()),
            )
            ArtifactPresentation.FULL_SCREEN -> Rect(Offset.Zero, Size(width.toFloat(), height.toFloat()))
        }
        val panel = measurables.firstOrNull { it.layoutId == Slot.Panel }?.measure(
            Constraints.fixed(target.width.roundToInt(), target.height.roundToInt()),
        )
        val halfTop = max(sheetTop, height / 2)
        motion.halfTravel = (halfTop - sheetTop).toFloat()
        val restTop = if (state.sheetFull) sheetTop else halfTop
        motion.sheetVisibleHeight = (height - restTop).toFloat()
        // Up to full height from half; not past the top once there.
        motion.minDrag = (sheetTop - restTop).toFloat()

        layout(width, height) {
            chat.placeRelative(0, 0)
            scrim?.placeRelative(0, 0)
            divider?.placeRelative(chatWidth, 0)
            if (panel != null) {
                val shown = motion.shown.value
                var x = target.left
                var y = target.top
                when (presentation) {
                    ArtifactPresentation.SHEET -> {
                        y = lerp(height.toFloat(), restTop + motion.dragged, shown)
                    }
                    ArtifactPresentation.SPLIT -> x += (1f - shown) * (paneWidth + gutter)
                    ArtifactPresentation.FULL_SCREEN -> Unit
                }
                val physicalLeft = if (rtl) width - x - target.width else x
                val placed = Rect(Offset(physicalLeft, y), target.size)
                motion.lastPlacedRect = placed
                motion.lastPlacedShape = presentation
                val anchor = motion.anchor
                panel.placeRelativeWithLayer(x.roundToInt(), y.roundToInt()) {
                    if (presentation == ArtifactPresentation.FULL_SCREEN && anchor == null) {
                        alpha = shown
                        val scale = WholeEnterScale + (1f - WholeEnterScale) * shown
                        scaleX = scale
                        scaleY = scale
                    }
                    if (anchor != null) {
                        // Laid out at its final size; only the window onto it grows (or shrinks).
                        val visible = lerp(anchor, placed, motion.morph.value).translate(-placed.topLeft)
                        shape = RectShape(visible)
                        clip = true
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitDivider(
    dragging: Boolean,
    onDrag: (delta: Float) -> Unit,
    onDragStop: () -> Unit,
    onDoubleTap: () -> Unit,
    onStep: (wider: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(Res.string.cd_resize_artifact)
    val wider = stringResource(Res.string.artifact_wider)
    val narrower = stringResource(Res.string.artifact_narrower)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragStop by rememberUpdatedState(onDragStop)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    // The divider moves under the finger, so its own coordinates shift as it is dragged: deltas are
    // taken in the window's instead, or it would trail the finger.
    val placed = remember { DividerPlacement() }
    Box(
        modifier
            .fillMaxSize()
            .onPlaced { placed.left = it.positionInWindow().x }
            .pointerInput(Unit) {
                var last = 0f
                detectHorizontalDragGestures(
                    onDragStart = { start -> last = start.x + placed.left },
                    onDragEnd = { currentOnDragStop() },
                    onDragCancel = { currentOnDragStop() },
                ) { change, _ ->
                    change.consume()
                    val now = change.position.x + placed.left
                    currentOnDrag(now - last)
                    last = now
                }
            }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { currentOnDoubleTap() }) }
            .semantics {
                contentDescription = description
                customActions = listOf(
                    CustomAccessibilityAction(wider) { onStep(true); true },
                    CustomAccessibilityAction(narrower) { onStep(false); true },
                )
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                // The artifact is on the end side: an arrow toward the start widens it.
                val towardStart = if (rtl) Key.DirectionRight else Key.DirectionLeft
                val towardEnd = if (rtl) Key.DirectionLeft else Key.DirectionRight
                when (event.key) {
                    towardStart -> onStep(true).let { true }
                    towardEnd -> onStep(false).let { true }
                    else -> false
                }
            }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = if (dragging) 6.dp else 4.dp, height = 48.dp)
                .background(
                    if (dragging) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant,
                    CircleShape,
                ),
        )
    }
}

private enum class Slot { Chat, Scrim, Divider, Panel }

private class DividerPlacement {
    var left = 0f
}

/** The artifact sheet's widest: wider than a menu sheet, since it shows a page. */
internal val ArtifactSheetMaxWidth = 840.dp

/** Windows narrower than this (a phone held upright) open the artifact sheet half way, others at full height. */
internal val ArtifactSheetHalfOpenBelowWidth = 600.dp

/** Clips to [rect], in the clipped node's own coordinates. */
private class RectShape(private val rect: Rect) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rectangle(rect)
}

/** Motion and drag state the layout reads every frame, kept out of composition. */
@Stable
private class HostMotion {
    /** 0 = off screen, 1 = in place: a sheet rises and a pane slides in with it. */
    val shown = Animatable(0f)

    /** 0 = clipped to [anchor], 1 = whole. */
    val morph = Animatable(1f)

    /** The bounds in the window of the card the open artifact was tapped on, while it is on screen. */
    var card: (() -> Rect?)? = null

    /** What the pane grows from or shrinks into, in this host's coordinates; null when neither. */
    var anchor by mutableStateOf<Rect?>(null)

    /** The content fades with the morph (out of or into the card), not when reshaping. */
    var fadeContent = false

    /** How far the sheet is pulled down (negative: up), px. */
    var dragged by mutableFloatStateOf(0f)
    var minDrag = 0f
    var halfTravel = 0f
    var sheetVisibleHeight = 0f

    /** The artifact's width as the divider is dragged, px, past the minimums; null when not dragging. */
    var dragPaneWidth by mutableStateOf<Float?>(null)
    var paneWidth = 0f
    var available = 0f

    var host: LayoutCoordinates? = null
    var lastShape: ArtifactPresentation? = null
    var lastPlacedShape: ArtifactPresentation? = null
    var lastPlacedRect: Rect? = null

    fun contentAlpha(): Float = if (fadeContent) ((morph.value - HALF) / HALF).coerceIn(0f, 1f) else 1f

    fun fraction(fallback: Float): Float = if (available > 0f) paneWidth / available else fallback

    fun release(rawPaneWidth: Float, density: Density): SplitRelease = with(density) {
        splitRelease(
            paneWidth = rawPaneWidth,
            available = available,
            minPane = MinSplitPaneWidth.toPx(),
            snap = DetentSnap.toPx(),
            overdrag = DividerOverdrag.toPx(),
        )
    }

    suspend fun settleDrag() {
        animate(dragged, 0f, animationSpec = SettleSpec) { value, _ -> dragged = value }
    }

    /** [window] (a rect in the window) in this host's coordinates. */
    fun toHost(window: Rect): Rect? {
        val coordinates = host?.takeIf { it.isAttached } ?: return null
        return Rect(coordinates.windowToLocal(window.topLeft), window.size)
    }

    fun isOnScreen(rect: Rect): Boolean {
        val size = host?.size ?: return false
        return rect.bottom > 0f && rect.top < size.height && rect.right > 0f && rect.left < size.width
    }
}

private const val HALF = 0.5f
private const val ScrimAlpha = 0.32f
private const val DISMISS_FRACTION = 0.33f
private const val RAISE_FRACTION = 0.3f
private const val WholeEnterScale = 0.94f
private val DismissVelocity = 1200.dp
private val DetentSnap = 16.dp
private val DividerOverdrag = 96.dp

// Material's emphasized curves: decelerate into place, accelerate away.
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private val MorphOpenSpec = tween<Float>(durationMillis = 500, easing = EmphasizedDecelerate)
private val MorphCloseSpec = tween<Float>(durationMillis = 250, easing = EmphasizedAccelerate)
private val MorphReshapeSpec = tween<Float>(durationMillis = 400, easing = EmphasizedDecelerate)
private val ShowSpec = spring<Float>(dampingRatio = 0.85f, stiffness = 380f)
private val HideSpec = tween<Float>(durationMillis = 220, easing = EmphasizedAccelerate)
private val SettleSpec = spring<Float>(dampingRatio = 0.8f, stiffness = 500f)
