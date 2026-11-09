package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.user_message_show_less
import com.garfiec.librechat.feature.chat.resources.user_message_show_more
import org.jetbrains.compose.resources.stringResource

/**
 * Settings › Chat "Collapse long user messages" (web's `collapseLongUserMessages`, on by default).
 * Provided by `ChatRoot`; outside it (previews) long messages collapse, as web's default does.
 */
val LocalCollapseLongUserMessages = compositionLocalOf { true }

/**
 * Upstream `CollapsibleText` (`packages/chat/src/components/CollapsibleText.tsx`): a user message
 * taller than [COLLAPSED_MAX_HEIGHT_DP] (scaled with the chat font size, as web scales with rem)
 * renders clamped with a fade and a "Show more" toggle, so pasted text or code cannot dominate the
 * thread. The clamp is visual only: the whole text stays composed, so it stays selectable, readable
 * by TalkBack/VoiceOver and findable by in-conversation search. [forceExpanded] (the focused search
 * match is in this message) shows it whole.
 *
 * The clamp is decided in the same layout pass that measures the content, so the first frame
 * already carries it (web uses a layout effect for the same reason). The [tolerance][OVERFLOW_TOLERANCE_DP]
 * only decides whether the toggle appears.
 */
@Composable
internal fun CollapsibleUserText(
    enabled: Boolean,
    stateKey: String,
    fontSizeMultiplier: Float,
    forceExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(stateKey, key = "collapse-user:$stateKey") { mutableStateOf(false) }
    var overflowing by remember { mutableStateOf(false) }
    val clampable = enabled && !expanded && !forceExpanded
    // One content() call site whatever the setting, so turning it on or off keeps the content's state.
    Column {
        // A Column, not a Box: the message body is several stacked children.
        Column(
            modifier = if (!enabled) {
                Modifier
            } else {
                // The fade sits before the layout, so it draws over the clamped height (not the
                // whole content, whose bottom the clip would discard) and its layer stays that size.
                Modifier
                    .clipToBounds()
                    .then(if (overflowing && clampable) Modifier.fadeOutBottom() else Modifier)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(maxHeight = Constraints.Infinity))
                        val maxHeight = (COLLAPSED_MAX_HEIGHT_DP * fontSizeMultiplier).dp.roundToPx()
                        val tolerance = (OVERFLOW_TOLERANCE_DP * fontSizeMultiplier).dp.roundToPx()
                        val over = placeable.height - maxHeight > tolerance
                        if (over != overflowing) overflowing = over
                        val height = if (over && clampable) maxHeight else placeable.height
                        layout(placeable.width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
                            placeable.place(0, 0)
                        }
                    }
            },
        ) {
            content()
        }
        if (enabled && overflowing && !forceExpanded) {
            val label = stringResource(
                if (expanded) Res.string.user_message_show_less else Res.string.user_message_show_more,
            )
            AdaptiveTextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(text = label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Fades the clamped content's last [FADE_HEIGHT_DP] to transparent, whatever the bubble behind it. */
private fun Modifier.fadeOutBottom(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = FADE_HEIGHT_DP.dp.toPx().coerceAtMost(size.height)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = size.height - fade,
                endY = size.height,
            ),
            topLeft = Offset(0f, size.height - fade),
            blendMode = BlendMode.DstOut,
        )
    }

/** MIRRORED from upstream `COLLAPSED_MAX_HEIGHT` (px at 1rem = 16px; dp here). */
internal const val COLLAPSED_MAX_HEIGHT_DP = 256

/** MIRRORED from upstream `OVERFLOW_TOLERANCE`. */
internal const val OVERFLOW_TOLERANCE_DP = 8

/** Web's `h-10` fade. */
private const val FADE_HEIGHT_DP = 40
