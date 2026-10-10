package com.garfiec.librechat.core.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.PlatformBackHandler
import com.garfiec.librechat.core.ui.theme.LocalDarkTheme
import kotlinx.coroutines.launch

/**
 * A floating Liquid Glass sheet, composed over the app by [GlassSheetHost]; don't call it directly.
 * Re-implements the M3 sheet's slide, scrim, back, drag/fling-to-dismiss and nested-scroll pull.
 */
@Composable
internal fun GlassFloatingSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val style = rememberGlassStyle() ?: return
    val backdrop = LocalGlassBackdrop.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dismissRequest by rememberUpdatedState(onDismissRequest)

    // 0 = off screen below, 1 = resting. Drives the slide and the scrim together.
    val shown = remember { Animatable(0f) }
    LaunchedEffect(shown) { shown.animateTo(1f, ShowSpec) }
    var panelHeight by remember { mutableIntStateOf(0) }
    var dragged by remember { mutableFloatStateOf(0f) }
    var dismissing by remember { mutableStateOf(false) }

    val dismiss: () -> Unit = {
        if (!dismissing) {
            dismissing = true
            scope.launch {
                shown.animateTo(0f, HideSpec)
                dismissRequest()
            }
        }
    }
    val settle: (velocity: Float) -> Unit = { velocity ->
        if (dragged > panelHeight * DISMISS_FRACTION || velocity > with(density) { DismissVelocity.toPx() }) {
            dismiss()
        } else {
            scope.launch { animate(dragged, 0f, animationSpec = SettleSpec) { value, _ -> dragged = value } }
        }
    }
    PlatformBackHandler(enabled = true, onBack = dismiss)

    // Scrollable content first takes back any pull it gave the sheet, and hands its unused downward
    // scroll at the top (a pull past the start of the list) to the sheet.
    val nestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f || dragged <= 0f) return Offset.Zero
                val taken = maxOf(available.y, -dragged)
                dragged += taken
                return Offset(0f, taken)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                dragged += available.y
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragged <= 0f) return Velocity.Zero
                settle(available.y)
                return available
            }
        }
    }

    val bottomInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
    // Thicker than a control's glass, so rows stay legible over a busy chat.
    val thickening = GlassDefaults.panelThickening(MaterialTheme.colorScheme.surface, LocalDarkTheme.current)
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = shown.value }
                .background(ScrimColor)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        if (waitForUpOrCancellation() != null) dismiss()
                    }
                },
        )
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(SheetMargin),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = MaxSheetWidth)
                    .fillMaxWidth()
                    .onSizeChanged { panelHeight = it.height }
                    .graphicsLayer {
                        val hiddenOffset = panelHeight + SheetMargin.toPx() + bottomInset.getBottom(this)
                        translationY = (1f - shown.value) * hiddenOffset + dragged.coerceAtLeast(0f)
                    }
                    .glassSheetPanel(style, backdrop, thickening)
                    .draggable(
                        state = rememberDraggableState { delta -> dragged = (dragged + delta).coerceAtLeast(0f) },
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity -> settle(velocity) },
                    )
                    .nestedScroll(nestedScroll)
                    // The content behind is still in the tree (a window would have hidden it), so at
                    // least keep the sheet's own contents together for screen readers.
                    .semantics { isTraversalGroup = true },
            ) {
                // What an M3 sheet provides from its container; without it, text that relies on the
                // ambient content colour renders in the default black, unreadable on dark glass.
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    dragHandle?.invoke()
                    content()
                }
            }
        }
    }
}

/** Shared with `AdaptiveSheetSurface`, so a sheet a screen drags itself matches this one. */
internal fun Modifier.glassSheetPanel(style: GlassStyle, backdrop: GlassBackdrop?, thickening: Color): Modifier =
    glassSurface(style, SheetShape, backdrop).clip(SheetShape).background(thickening)

private val SheetShape = RoundedCornerShape(32.dp)

/** How far a floating sheet sits in from the screen's edges (inside the safe area). */
internal val SheetMargin = 12.dp
private val MaxSheetWidth = 640.dp
private val DismissVelocity = 1200.dp
private const val DISMISS_FRACTION = 0.33f
private val ScrimColor = Color.Black.copy(alpha = 0.2f)
private val ShowSpec = spring<Float>(dampingRatio = 0.85f, stiffness = 380f)
private val HideSpec = tween<Float>(durationMillis = 220)
private val SettleSpec = spring<Float>(dampingRatio = 0.8f, stiffness = 500f)
