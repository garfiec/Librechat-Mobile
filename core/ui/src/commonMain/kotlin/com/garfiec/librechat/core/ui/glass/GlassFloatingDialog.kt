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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
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
                    title?.let { slot ->
                        CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
                            ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                                Box(
                                    Modifier
                                        .align(if (icon == null) Alignment.Start else Alignment.CenterHorizontally)
                                        .padding(bottom = 16.dp),
                                ) { slot() }
                            }
                        }
                    }
                    text?.let { slot ->
                        CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant) {
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                                // Takes what's left once the buttons have their row, so long content
                                // scrolls (its own list) instead of pushing them off the panel.
                                Box(Modifier.weight(1f, fill = false).align(Alignment.Start)) { slot() }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CompositionLocalProvider(LocalContentColor provides colors.primary) {
                            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                                dismissButton?.invoke()
                                confirmButton()
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val START_SCALE = 0.9f
private val DialogShape = RoundedCornerShape(28.dp)
private val DialogMargin = 24.dp
private val DialogPadding = 24.dp
private val MinWidth = 280.dp
private val MaxWidth = 560.dp
private val ScrimColor = Color.Black.copy(alpha = 0.32f)
private val ShowSpec = spring<Float>(dampingRatio = 0.8f, stiffness = 600f)
