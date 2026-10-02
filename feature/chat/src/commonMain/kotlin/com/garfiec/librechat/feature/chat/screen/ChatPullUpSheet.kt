package com.garfiec.librechat.feature.chat.screen

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.animateToWithDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveSheetSurface
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.core.ui.components.PlatformBackHandler
import com.garfiec.librechat.core.ui.glass.GlassBackdrop
import com.garfiec.librechat.core.ui.glass.rememberGlassBackdrop
import com.garfiec.librechat.feature.chat.components.ChatOptionsPage
import com.garfiec.librechat.feature.chat.components.ChatOptionsSheetController
import com.garfiec.librechat.feature.chat.components.ChatToolsSheetContent
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ChatViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Anchor states for the finger-following pull-up tools sheet. */
internal enum class PullUpAnchor { Hidden, Revealed }

/**
 * Per-gesture bookkeeping for the pull-up reveal, held outside the [NestedScrollConnection] so it
 * survives the connection being recreated and can be reset from a pointer-down handler. [listScrolled]
 * is true once the thread list has consumed scroll within the current gesture — while set, the reveal
 * is suppressed so a single drag from the top can't overshoot the bottom into opening the sheet.
 */
internal class PullUpGesture {
    var listScrolled = false
}

/**
 * The pull-up tools sheet: an upward pull on the thread surface (once it's scrolled to the bottom)
 * progressively reveals the same "+" tools/attachment menu, following the finger via an anchored-drag
 * surface. Material 3's ModalBottomSheet can't be driven by external over-scroll, so this is a custom
 * surface rather than the ModalBottomSheet the composer's "+" still opens.
 *
 * Apply [listModifier] to the active message list and [landingModifier] to the landing surface, record
 * the screen into [backdrop] while [visible], and draw [ChatPullUpSheetOverlay] last.
 */
@Stable
internal class ChatPullUpSheetState(
    val anchored: AnchoredDraggableState<PullUpAnchor>,
    val flingBehavior: TargetedFlingBehavior,
    /** Fling velocity above which a flick (rather than drag position) decides open/closed. */
    private val minFlingVelocityPx: Float,
    /** The whole screen, recorded for the sheet only while it shows. */
    val backdrop: GlassBackdrop?,
    private val height: MutableIntState,
    private val gesture: PullUpGesture,
) {
    var heightPx: Int
        get() = height.intValue
        set(value) {
            height.intValue = value
        }

    private val isOpen: Boolean
        get() {
            val o = anchored.offset
            return !o.isNaN() && o < heightPx
        }

    /** Derived so readers recompose only on the open<->closed transition, not per drag frame. */
    val visible: Boolean by derivedStateOf { heightPx > 0 && isOpen }

    suspend fun hide() {
        anchored.animateTo(PullUpAnchor.Hidden)
    }

    // Velocity-aware settle, only once the sheet is already partly open (so a fling that merely
    // reaches the list bottom can't fling it open). Velocity wins, else position at 40%.
    // animateToWithDecay, NOT settle(velocity) — the latter throws for this threshold-less state.
    private suspend fun settle(velocity: Float) {
        val h = heightPx.toFloat()
        val o = anchored.offset
        if (h > 0f && !o.isNaN() && o < h) {
            val target = when {
                velocity <= -minFlingVelocityPx -> PullUpAnchor.Revealed
                velocity >= minFlingVelocityPx -> PullUpAnchor.Hidden
                o < h * 0.6f -> PullUpAnchor.Revealed
                else -> PullUpAnchor.Hidden
            }
            anchored.animateToWithDecay(target, velocity)
        }
    }

    // Bridges the message-list over-scroll into the sheet: reveal on leftover upward drag once the
    // list is at its true bottom (onPostScroll), retract on downward drag while the sheet is open
    // (onPreScroll).
    private val listConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (available.y > 0f && isOpen) {
                Offset(0f, anchored.dispatchRawDelta(available.y))
            } else {
                Offset.Zero
            }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (consumed.y != 0f) {
                gesture.listScrolled = true
            }
            // Only reveal from a gesture that began at the bottom (list never scrolled), or keep
            // responding once the sheet is already opening. The flag is reset on each pointer-down
            // (see listModifier), so a cancelled gesture can't leave the reveal permanently suppressed.
            val allowReveal = !gesture.listScrolled || isOpen
            return if (available.y < 0f && allowReveal) {
                Offset(0f, anchored.dispatchRawDelta(available.y))
            } else {
                Offset.Zero
            }
        }

        override suspend fun onPreFling(available: Velocity): Velocity =
            if (available.y > 0f && isOpen) {
                settle(available.y)
                available
            } else {
                Velocity.Zero
            }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settle(available.y)
            return available
        }
    }

    // Bridges the sheet's own content scroll into the surface: anchoredDraggable is pointer-input only
    // and, unlike ModalBottomSheet, has no nested-scroll bridge, so ChatToolsSheetContent's
    // verticalScroll would otherwise swallow every drag. onPostScroll for downward (content scrolls
    // first, leftover retracts), onPreScroll for upward.
    val sheetConnection = object : NestedScrollConnection {
        // Upward before the content, so a half-retracted sheet pulls back up mid-drag.
        // Unconditional is safe: at Revealed, dispatchRawDelta consumes 0.
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // NaN guard: before onSizeChanged sets anchors, offset is NaN and dispatchRawDelta
            // would poison the child scrollable's delta.
            val o = anchored.offset
            return if (available.y < 0f && !o.isNaN()) {
                Offset(0f, anchored.dispatchRawDelta(available.y))
            } else {
                Offset.Zero
            }
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (available.y > 0f && isOpen) {
                Offset(0f, anchored.dispatchRawDelta(available.y))
            } else {
                Offset.Zero
            }

        // Mirror of onPreScroll: an upward flick on a half-retracted sheet settles it rather than
        // handing velocity to the content.
        override suspend fun onPreFling(available: Velocity): Velocity {
            val o = anchored.offset
            val partlyRetracted = !o.isNaN() && o > 0f
            return if (available.y < 0f && partlyRetracted) {
                settle(available.y)
                available
            } else {
                Velocity.Zero
            }
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settle(available.y)
            return available
        }
    }

    /**
     * For the active message list: the nested-scroll bridge plus a pointer-down reset of the
     * per-gesture "list scrolled" flag, so every fresh touch re-arms the reveal even if a prior drag
     * ended without a fling callback.
     */
    val listModifier: Modifier = Modifier
        .nestedScroll(listConnection)
        .pointerInput(gesture) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                gesture.listScrolled = false
            }
        }

    /**
     * For the landing surface, which isn't scrollable, so nested-scroll never fires there — drives the
     * same state with a direct drag modifier instead.
     */
    val landingModifier: Modifier = Modifier.anchoredDraggable(
        state = anchored,
        orientation = Orientation.Vertical,
        flingBehavior = flingBehavior,
    )
}

@Composable
internal fun rememberChatPullUpSheetState(): ChatPullUpSheetState {
    val anchored = remember { AnchoredDraggableState(PullUpAnchor.Hidden) }
    val height = remember { mutableIntStateOf(0) }
    val gesture = remember { PullUpGesture() }
    val minFlingVelocityPx = with(LocalDensity.current) { 125.dp.toPx() }
    val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
        state = anchored,
        positionalThreshold = { distance -> distance * 0.4f },
        animationSpec = tween(),
    )
    val backdrop = rememberGlassBackdrop()
    val state = remember(anchored, flingBehavior, minFlingVelocityPx, backdrop) {
        ChatPullUpSheetState(anchored, flingBehavior, minFlingVelocityPx, backdrop, height, gesture)
    }
    // Dismiss the IME the moment the sheet starts to reveal (the screens' Scaffolds use imePadding()).
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(anchored) {
        snapshotFlow {
            val o = anchored.offset
            !o.isNaN() && height.intValue > 0 && o < height.intValue
        }.distinctUntilChanged().collect { revealing ->
            if (revealing) keyboardController?.hide()
        }
    }
    return state
}

/**
 * Scrim, back handling and the sheet surface for [state]. Drawn last in the screen's root box so
 * it sits above the composer and the top bar. The scrim's dim level is drawn in the draw phase
 * (drawBehind) and the sheet offset is read in the layout phase (offset {}), so a drag doesn't
 * recompose the screen.
 *
 * The model selector and parameters pages are handed off to [optionsController] rather than swapped
 * into this surface (whose anchored drag the selector's search/IME/list would fight), retracting it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoxScope.ChatPullUpSheetOverlay(
    state: ChatPullUpSheetState,
    topContentPadding: Dp,
    uiState: ChatUiState,
    viewModel: ChatViewModel,
    optionsController: ChatOptionsSheetController,
    selectedModelDisplay: String?,
    onAttachFiles: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhotos: () -> Unit,
    onAttachFromServer: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val hide: () -> Unit = { coroutineScope.launch { state.hide() } }
    // ChatToolsSheetContent hoists the MCP sub-list's expansion (the paged options sheet needs it to
    // survive a page swap), so this surface owns its own copy.
    var mcpExpanded by remember { mutableStateOf(false) }
    // Cap the sheet's height to the space below the top bar so tall content (e.g. many MCP servers)
    // can't push the bottom-anchored surface up past the screen top and clip the attachment cards
    // off-screen. Excess content scrolls inside the sheet instead. No minimum floor: in a very short
    // window (split-screen/freeform) a floor could itself exceed the window and reintroduce the
    // top-clip. coerceAtLeast(0) only guards heightIn against a negative on a pathologically small
    // window.
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val maxSheetHeight = (windowHeight - topContentPadding - 8.dp).coerceAtLeast(0.dp)

    if (state.visible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Drag the dimmed backdrop to push the sheet back down, or tap to dismiss.
                .anchoredDraggable(
                    state = state.anchored,
                    orientation = Orientation.Vertical,
                    flingBehavior = state.flingBehavior,
                )
                .pointerInput(Unit) {
                    detectTapGestures { hide() }
                }
                .drawBehind {
                    val o = state.anchored.offset
                    val h = state.heightPx
                    val p = if (h > 0 && !o.isNaN()) (1f - o / h).coerceIn(0f, 1f) else 0f
                    drawRect(color = Color.Black, alpha = 0.5f * p)
                },
        )
    }
    PlatformBackHandler(enabled = state.visible, onBack = hide)
    AdaptiveSheetSurface(
        backdrop = state.backdrop,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            // Match ModalBottomSheet: full-bleed on phones, capped + centered on wide
            // (fold/tablet) displays instead of edge-to-edge.
            .fillMaxWidth()
            .widthIn(max = BottomSheetDefaults.SheetMaxWidth)
            .heightIn(max = maxSheetHeight)
            // Stay invisible until measured: on the very first frame the offset is NaN and the
            // measured height is still 0, so without this the sheet would place at offset 0
            // (fully revealed) and flash the whole menu open on every chat entry.
            .graphicsLayer { alpha = if (state.heightPx > 0) 1f else 0f }
            .onSizeChanged { size ->
                if (size.height != state.heightPx) {
                    state.heightPx = size.height
                    if (size.height > 0) {
                        // newTarget is load-bearing: the default is the anchor closest to the
                        // CURRENT offset, which is the old height. A sheet that more than doubles
                        // while hidden (its content loading after the first measure) is then nearer
                        // Revealed (0) than the new Hidden, and snaps open over the composer on its
                        // own. Keep its state.
                        state.anchored.updateAnchors(
                            DraggableAnchors {
                                PullUpAnchor.Hidden at size.height.toFloat()
                                PullUpAnchor.Revealed at 0f
                            },
                            newTarget = state.anchored.targetValue,
                        )
                    }
                }
            }
            .offset {
                val o = state.anchored.offset
                IntOffset(0, if (o.isNaN()) state.heightPx else o.roundToInt())
            }
            .anchoredDraggable(
                state = state.anchored,
                orientation = Orientation.Vertical,
                flingBehavior = state.flingBehavior,
            )
            // Lets a drag over the scrolling content move the surface. See sheetConnection.
            .nestedScroll(state.sheetConnection),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LowProfileDragHandle()
            ChatToolsSheetContent(
                enabledTools = uiState.effectiveEnabledTools,
                onToggleTool = viewModel::toggleTool,
                mcpServers = uiState.mcpServers,
                selectedMcpServerNames = uiState.selectedMcpServerNames,
                onToggleMcpServer = viewModel::toggleMcpServer,
                onAttachFiles = onAttachFiles,
                onTakePhoto = onTakePhoto,
                onPickPhotos = onPickPhotos,
                onAttachFromServer = onAttachFromServer,
                onOpenModelParameters = {
                    optionsController.open(ChatOptionsPage.ModelParameters)
                    hide()
                },
                onOpenModelSelector = {
                    optionsController.open(ChatOptionsPage.ModelSelector)
                    hide()
                },
                selectedModelDisplay = selectedModelDisplay,
                onDismiss = hide,
                isCodeInterpreterAvailable = uiState.isCodeInterpreterAvailable,
                webSearchEnabled = uiState.webSearchEnabled,
                urlContextEnabled = uiState.urlContextProviderGate,
                runCodeEnabled = uiState.runCodeEnabled,
                fileSearchEnabled = uiState.fileSearchEnabled,
                memoryEnabled = uiState.isMemoryToolAvailable,
                mcpServersEnabled = uiState.mcpServersEnabled,
                gates = uiState.chatInputGates,
                contextGauge = uiState.contextGaugeDetails,
                contextUsageEnabled = uiState.contextUsageEnabled,
                onCompact = viewModel::compactConversation.takeIf { uiState.canCompactNow },
                onSnoozeCompact = viewModel::snoozeCompactNudge,
                contextBarPlacement = uiState.contextBarPlacement,
                contextGaugeExpanded = uiState.contextGaugeExpanded,
                onContextGaugeExpandedChange = viewModel::setContextGaugeExpanded,
                mcpExpanded = mcpExpanded,
                onMcpExpandedChange = { mcpExpanded = it },
            )
        }
    }

    // Manual attachment routing. The pick can come from this surface, which stays revealed and
    // drag-responsive underneath — retract it before the routing sheet shows, or the two surfaces
    // fight for the same gestures.
    val routingPending = uiState.composer.pendingUploadRouting != null
    LaunchedEffect(routingPending) {
        if (routingPending) state.hide()
    }
}
