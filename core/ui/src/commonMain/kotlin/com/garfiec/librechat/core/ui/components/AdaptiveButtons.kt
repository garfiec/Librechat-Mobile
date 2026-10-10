package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import com.garfiec.librechat.core.ui.glass.GlassControlColors
import com.garfiec.librechat.core.ui.theme.isLiquidGlass

/**
 * An M3 [Button] in Material. In Liquid Glass it is the iOS prominent button: a flat capsule in the
 * caller's colours (so a destructive red fill stays red) with a semibold label. [shape], [elevation]
 * and [border] only apply in Material.
 */
@Composable
fun AdaptiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (!isLiquidGlass) {
        Button(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, interactionSource, content)
        return
    }
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = CircleShape,
        colors = colors,
        elevation = null,
        border = null,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
    ) {
        val row = this
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)) { row.content() }
    }
}

/**
 * An M3 [OutlinedButton] in Material. In Liquid Glass it is the iOS bordered button, which despite
 * the name has no border: a capsule on the secondary fill, labelled in the caller's content colour
 * (the accent by default, red for a destructive action). [shape], [elevation] and [border] only
 * apply in Material.
 */
@Composable
fun AdaptiveOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (!isLiquidGlass) {
        OutlinedButton(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, interactionSource, content)
        return
    }
    val accentLabel = colors == ButtonDefaults.outlinedButtonColors()
    GlassBorderedButton(onClick, enabled, colors, accentLabel, contentPadding, interactionSource, modifier, content)
}

/**
 * An M3 [FilledTonalButton] in Material; the iOS bordered button in Liquid Glass, like
 * [AdaptiveOutlinedButton]. [shape], [elevation] and [border] only apply in Material.
 */
@Composable
fun AdaptiveFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.filledTonalShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (!isLiquidGlass) {
        FilledTonalButton(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, interactionSource, content)
        return
    }
    val accentLabel = colors == ButtonDefaults.filledTonalButtonColors()
    GlassBorderedButton(onClick, enabled, colors, accentLabel, contentPadding, interactionSource, modifier, content)
}

@Composable
private fun GlassBorderedButton(
    onClick: () -> Unit,
    enabled: Boolean,
    colors: ButtonColors,
    accentLabel: Boolean,
    contentPadding: PaddingValues,
    interactionSource: MutableInteractionSource?,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val fill = GlassControlColors.fill
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonColors(
            containerColor = fill,
            // iOS labels a bordered button in the accent. M3's defaults are neutral (outlined) or tonal,
            // so a caller that kept them gets the accent; one that chose a colour (red) keeps it.
            contentColor = if (accentLabel) MaterialTheme.colorScheme.primary else colors.contentColor,
            disabledContainerColor = fill.copy(alpha = fill.alpha * DISABLED_FILL_ALPHA),
            disabledContentColor = colors.disabledContentColor,
        ),
        elevation = null,
        border = null,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

private const val DISABLED_FILL_ALPHA = 0.5f
