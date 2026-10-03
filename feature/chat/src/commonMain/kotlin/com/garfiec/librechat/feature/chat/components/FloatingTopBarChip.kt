package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.pressBounce
import com.garfiec.librechat.core.ui.components.rememberPressBounce
import com.garfiec.librechat.core.ui.glass.LocalGlassBackdrop
import com.garfiec.librechat.core.ui.glass.glassSurface
import com.garfiec.librechat.core.ui.glass.rememberGlassStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The diameter of a circular floating top-bar control chip. Shared so the bar's height
 * measurement and the chips themselves stay in sync.
 */
internal val FloatingBarChipSize = 44.dp

/**
 * A solid, outlined chip that backs each floating chat top-bar control. Uses the same fill and
 * border as the bottom composer's input box ([ChatInputDefaults]) so the floating header controls
 * read as the same family of surfaces — legible over arbitrary chat content scrolling behind them.
 */
@Composable
internal fun FloatingBarChip(
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = CircleShape,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val glass = rememberGlassStyle()
    val color = if (glass != null) Color.Transparent else ChatInputDefaults.containerColor
    val border = if (glass != null) null else BorderStroke(1.dp, ChatInputDefaults.borderColor)
    val contentColor = MaterialTheme.colorScheme.onSurface
    val modifier = if (glass != null) modifier.glassSurface(glass, shape, LocalGlassBackdrop.current) else modifier
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = color,
            contentColor = contentColor,
            border = border,
            modifier = modifier,
            content = content,
        )
    } else {
        Surface(shape = shape, color = color, contentColor = contentColor, border = border, modifier = modifier, content = content)
    }
}

/**
 * The shared frame for a floating-bar content bubble: a [FloatingBarChip] sized to the bar's chip
 * height with its [content] vertically centered. [fillWidth] expands the chip across the available
 * region (the FILL alignment); otherwise it hugs its content. [contentModifier] is applied to the
 * centered interior so callers can attach gestures spanning the whole bubble.
 */
@Composable
internal fun FloatingBarContentChip(
    fillWidth: Boolean,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    FloatingBarChip(
        modifier = modifier.height(FloatingBarChipSize)
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier),
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier.fillMaxHeight().then(contentModifier),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

/**
 * A [FloatingBarContentChip] wrapping a single centered, ellipsized label — the bar's title/model
 * content bubble. Shared so the two content modes render identically. A tap is wired through
 * [onClick] as a proper button (model selector) that bounces on press, outside Liquid Glass like its
 * neighbours. [onLongClick] is a hold plus a long-click semantics action (the in-place title edit),
 * with no click role or ripple, since a tap does nothing: the hold charges [holdCharge] instead. The
 * caller applies [holdChargeScale], so the pop can carry over to whatever replaces this chip. The gesture reads the latest callback via
 * [rememberUpdatedState], so an unstable caller lambda doesn't restart it on every recomposition.
 */
@Composable
internal fun FloatingBarLabelChip(
    text: String,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    holdCharge: HoldCharge? = null,
) {
    val latestLongClick by rememberUpdatedState(onLongClick)
    val haptics = LocalHapticFeedback.current
    val contentModifier = if (onLongClick != null) {
        Modifier
            .pointerInput(holdCharge) {
                awaitEachGesture {
                    awaitFirstDown()
                    val timeout = viewConfiguration.longPressTimeoutMillis
                    holdCharge?.charge(timeout)
                    var held = true
                    withTimeoutOrNull(timeout) {
                        waitForUpOrCancellation()
                        held = false
                    }
                    if (held) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        holdCharge?.pop()
                        latestLongClick?.invoke()
                    } else {
                        holdCharge?.cancel()
                    }
                }
            }
            .semantics { onLongClick(label = onLongClickLabel) { latestLongClick?.invoke(); true } }
    } else {
        Modifier
    }
    val bounce = rememberPressBounce()
    FloatingBarContentChip(
        fillWidth = fillWidth,
        modifier = modifier.pressBounce(bounce, enabled = onClick != null && rememberGlassStyle() == null),
        onClick = onClick,
        contentModifier = contentModifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/**
 * A press that builds over the long-press timeout and pops when the hold lands, so a long-press-only
 * control shows the finger it's getting somewhere. Lifting early settles back without the pop.
 */
@Stable
internal class HoldCharge(private val scope: CoroutineScope) {
    /** 0 = rest, 1 = fully charged; the pop overshoots past 1, then the settle dips below 0. */
    internal val press = Animatable(0f)

    internal fun charge(durationMillis: Long) {
        scope.launch { press.animateTo(1f, tween(durationMillis.toInt(), easing = LinearEasing)) }
    }

    internal fun cancel() {
        scope.launch { press.animateTo(0f, SettleSpec) }
    }

    internal fun pop() {
        scope.launch {
            press.animateTo(POP_OVERSHOOT, PopSpec)
            press.animateTo(0f, PopSettleSpec)
        }
    }
}

@Composable
internal fun rememberHoldCharge(): HoldCharge {
    val scope = rememberCoroutineScope()
    return remember(scope) { HoldCharge(scope) }
}

/** Swells by [HoldCharge]'s progress: [MaxHoldSwell] on the longer side at full charge. */
internal fun Modifier.holdChargeScale(state: HoldCharge): Modifier = graphicsLayer {
    val swell = MaxHoldSwell.toPx() / maxOf(size.width, size.height, 1f)
    val scale = 1f + swell * state.press.value
    scaleX = scale
    scaleY = scale
}

private val MaxHoldSwell = 12.dp
private const val POP_OVERSHOOT = 1.5f
private val PopSpec = tween<Float>(durationMillis = 90, easing = LinearOutSlowInEasing)
private val PopSettleSpec = spring<Float>(dampingRatio = 0.45f, stiffness = 600f)
private val SettleSpec = spring<Float>(stiffness = Spring.StiffnessMediumLow)

/** A circular [FloatingBarChip] wrapping a centered icon — the bar's hamburger/options control. */
@Composable
internal fun FloatingBarIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    iconRotation: () -> Float = { 0f },
) {
    FloatingBarChip(onClick = onClick, modifier = modifier.size(FloatingBarChipSize)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = iconRotation() },
            )
        }
    }
}

/**
 * Top-down scrim painted behind the floating top bar (covering the status-bar region). Fades from
 * a mostly-opaque surface tint at the very top to transparent, so chat content scrolling up behind
 * the bar reads as gently dimmed rather than hidden by a solid app bar.
 */
@Composable
internal fun chatTopBarScrim(): Brush {
    val surface = MaterialTheme.colorScheme.surface
    return remember(surface) {
        Brush.verticalGradient(
            colors = listOf(
                surface.copy(alpha = 0.92f),
                surface.copy(alpha = 0.55f),
                Color.Transparent,
            ),
        )
    }
}
