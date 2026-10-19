package com.garfiec.librechat.core.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.garfiec.librechat.core.ui.components.PlatformBackHandler
import com.garfiec.librechat.core.ui.theme.LocalDarkTheme

/**
 * A Liquid Glass alert dialog with the M3 layout, opened by `AdaptiveAlertDialog`. The buttons stay
 * plain text, as on an iOS alert: glass on glass would double the effect.
 */
// [modifier] is the caller's, for the panel (as on an M3 dialog); the root is the full-screen scrim.
@Suppress("ModifierNotUsedAtRoot")
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GlassFloatingDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    scrollableText: Boolean = true,
) {
    val style = rememberGlassStyle() ?: return
    val backdrop = LocalGlassBackdrop.current
    val dismiss by rememberUpdatedState(onDismissRequest)
    PlatformBackHandler(enabled = properties.dismissOnBackPress) { dismiss() }

    val shown = remember { Animatable(0f) }
    LaunchedEffect(shown) { shown.animateTo(1f, ShowSpec) }
    val colors = MaterialTheme.colorScheme

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = shown.value }
                .background(ScrimColor)
                // Takes every touch, as a dialog window does.
                .pointerInput(properties.dismissOnClickOutside) {
                    awaitEachGesture {
                        awaitFirstDown()
                        if (waitForUpOrCancellation() != null && properties.dismissOnClickOutside) dismiss()
                    }
                },
        )
        Box(
            // safeDrawing includes the keyboard, so a dialog with a field rises above it.
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(DialogMargin),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier
                    .widthIn(min = MinWidth, max = MaxWidth)
                    .graphicsLayer {
                        alpha = shown.value
                        val scale = START_SCALE + (1f - START_SCALE) * shown.value
                        scaleX = scale
                        scaleY = scale
                    }
                    .glassSurface(style, DialogShape, backdrop)
                    .clip(DialogShape)
                    .background(GlassDefaults.panelThickening(colors.surface, LocalDarkTheme.current))
                    // The content behind is still in the tree, unlike behind a dialog window.
                    .semantics { isTraversalGroup = true },
                propagateMinConstraints = true,
            ) {
                Column(Modifier.padding(DialogPadding)) {
                    icon?.let { slot ->
                        CompositionLocalProvider(LocalContentColor provides colors.secondary) {
                            Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp)) { slot() }
                        }
                    }
                    val titleAlignment = if (icon == null) Alignment.Start else Alignment.CenterHorizontally
                    if (scrollableText) {
                        // Centred under an icon, the title stays out of the scroll, which is only as
                        // wide as its content.
                        if (icon != null) title?.let { DialogTitle(it, titleAlignment) }
                        // Title and text scroll as one, in what's left once the buttons have their
                        // row, so a tall title or a long message never pushes them off the panel.
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                            if (icon == null) title?.let { DialogTitle(it, titleAlignment) }
                            text?.let { DialogText(Modifier.align(Alignment.Start), it) }
                        }
                    } else {
                        title?.let { DialogTitle(it, titleAlignment) }
                        // The slot scrolls itself (its own list) in what's left.
                        text?.let { DialogText(Modifier.weight(1f, fill = false).align(Alignment.Start), it) }
                    }
                    Spacer(Modifier.height(24.dp))
                    // Wraps rather than squeezing a label to a word a line. As on M3, confirm comes
                    // first in the flipped direction: side by side it sits at the end, stacked on top.
                    val direction = LocalLayoutDirection.current
                    CompositionLocalProvider(LocalLayoutDirection provides direction.flipped()) {
                        FlowRow(
                            modifier = Modifier.align(Alignment.End),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            CompositionLocalProvider(
                                LocalLayoutDirection provides direction,
                                LocalContentColor provides colors.primary,
                            ) {
                                ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                                    confirmButton()
                                    dismissButton?.invoke()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.DialogTitle(slot: @Composable () -> Unit, alignment: Alignment.Horizontal) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
            Box(Modifier.align(alignment).padding(bottom = 16.dp)) { slot() }
        }
    }
}

@Composable
private fun DialogText(modifier: Modifier = Modifier, slot: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
            Box(modifier) { slot() }
        }
    }
}

private fun LayoutDirection.flipped(): LayoutDirection =
    if (this == LayoutDirection.Ltr) LayoutDirection.Rtl else LayoutDirection.Ltr

private const val START_SCALE = 0.9f
private val DialogShape = RoundedCornerShape(28.dp)
private val DialogMargin = 24.dp
private val DialogPadding = 24.dp
private val MinWidth = 280.dp
private val MaxWidth = 560.dp
private val ScrimColor = Color.Black.copy(alpha = 0.32f)
private val ShowSpec = spring<Float>(dampingRatio = 0.8f, stiffness = 600f)
