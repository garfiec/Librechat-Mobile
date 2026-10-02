package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ChipColors
import androidx.compose.material3.ChipElevation
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.garfiec.librechat.core.ui.glass.GlassControlColors
import com.garfiec.librechat.core.ui.theme.isLiquidGlass

// In Liquid Glass every chip is an iOS pill: a borderless capsule on the secondary fill, and a
// selected one in the accent with white content. Each wrapper mirrors its M3 signature and passes
// straight through in Material; [shape], [elevation] and [border] only apply there.

@Composable
fun AdaptiveFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = FilterChipDefaults.shape,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    elevation: SelectableChipElevation? = FilterChipDefaults.filterChipElevation(),
    border: BorderStroke? = FilterChipDefaults.filterChipBorder(enabled, selected),
    interactionSource: MutableInteractionSource? = null,
) {
    val glass = isLiquidGlass
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = if (glass) CircleShape else shape,
        colors = if (glass) glassSelectableChipColors(colors == FilterChipDefaults.filterChipColors(), colors) else colors,
        elevation = if (glass) null else elevation,
        border = if (glass) null else border,
        interactionSource = interactionSource,
    )
}

@Composable
fun AdaptiveInputChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    avatar: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = InputChipDefaults.shape,
    colors: SelectableChipColors = InputChipDefaults.inputChipColors(),
    elevation: SelectableChipElevation? = InputChipDefaults.inputChipElevation(),
    border: BorderStroke? = InputChipDefaults.inputChipBorder(enabled, selected),
    interactionSource: MutableInteractionSource? = null,
) {
    val glass = isLiquidGlass
    InputChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        avatar = avatar,
        trailingIcon = trailingIcon,
        shape = if (glass) CircleShape else shape,
        colors = if (glass) glassSelectableChipColors(colors == InputChipDefaults.inputChipColors(), colors) else colors,
        elevation = if (glass) null else elevation,
        border = if (glass) null else border,
        interactionSource = interactionSource,
    )
}

@Composable
fun AdaptiveAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = AssistChipDefaults.shape,
    colors: ChipColors = AssistChipDefaults.assistChipColors(),
    elevation: ChipElevation? = AssistChipDefaults.assistChipElevation(),
    border: BorderStroke? = AssistChipDefaults.assistChipBorder(enabled),
    interactionSource: MutableInteractionSource? = null,
) {
    val glass = isLiquidGlass
    AssistChip(
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = if (glass) CircleShape else shape,
        colors = if (glass) glassChipColors(colors == AssistChipDefaults.assistChipColors(), colors) else colors,
        elevation = if (glass) null else elevation,
        border = if (glass) null else border,
        interactionSource = interactionSource,
    )
}

@Composable
fun AdaptiveSuggestionChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    shape: Shape = SuggestionChipDefaults.shape,
    colors: ChipColors = SuggestionChipDefaults.suggestionChipColors(),
    elevation: ChipElevation? = SuggestionChipDefaults.suggestionChipElevation(),
    border: BorderStroke? = SuggestionChipDefaults.suggestionChipBorder(enabled),
    interactionSource: MutableInteractionSource? = null,
) {
    val glass = isLiquidGlass
    SuggestionChip(
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        shape = if (glass) CircleShape else shape,
        colors = if (glass) glassChipColors(colors == SuggestionChipDefaults.suggestionChipColors(), colors) else colors,
        elevation = if (glass) null else elevation,
        border = if (glass) null else border,
        interactionSource = interactionSource,
    )
}

/** Fill when resting, accent with white content when selected; custom colours keep their content colours. */
@Composable
private fun glassSelectableChipColors(isDefault: Boolean, colors: SelectableChipColors): SelectableChipColors {
    val fill = GlassControlColors.fill
    val tint = GlassControlColors.tint
    val base = if (isDefault) {
        colors.copy(labelColor = MaterialTheme.colorScheme.onSurface, leadingIconColor = MaterialTheme.colorScheme.primary)
    } else {
        colors
    }
    return base.copy(
        containerColor = fill,
        disabledContainerColor = fill,
        selectedContainerColor = tint,
        disabledSelectedContainerColor = tint,
        selectedLabelColor = Color.White,
        selectedLeadingIconColor = Color.White,
        selectedTrailingIconColor = Color.White,
    )
}

/** The secondary fill for a chip that kept M3's defaults; a caller's own colours are left alone. */
@Composable
private fun glassChipColors(isDefault: Boolean, colors: ChipColors): ChipColors =
    if (isDefault) colors.copy(containerColor = GlassControlColors.fill, disabledContainerColor = GlassControlColors.fill) else colors
