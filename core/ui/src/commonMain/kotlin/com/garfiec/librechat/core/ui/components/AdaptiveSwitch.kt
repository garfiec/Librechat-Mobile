package com.garfiec.librechat.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.glass.GlassControlColors
import com.garfiec.librechat.core.ui.glass.GlassDefaults
import com.garfiec.librechat.core.ui.theme.isLiquidGlass

/**
 * An M3 [Switch] in Material; an iOS-style switch (a pill track in the accent colour when on, with a
 * white thumb) in Liquid Glass. [thumbContent] and [colors] only apply in Material.
 */
@Composable
fun AdaptiveSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    if (!isLiquidGlass) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            thumbContent = thumbContent,
            enabled = enabled,
            colors = colors,
            interactionSource = interactionSource,
        )
        return
    }
    val track by animateColorAsState(
        if (checked) GlassControlColors.tint else GlassControlColors.fill,
        label = "switchTrack",
    )
    val thumbOffset by animateDpAsState(if (checked) ThumbTravel else 0.dp, label = "switchThumb")
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            interactionSource = interactionSource,
            indication = null,
            onValueChange = onCheckedChange,
        )
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .then(toggle)
            // Same footprint as the M3 switch, so rows keep their layout.
            .size(width = SwitchWidth, height = SwitchHeight)
            .alpha(if (enabled) 1f else GlassDefaults.DISABLED_ALPHA)
            .background(track, CircleShape),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(2.dp)
                .offset(x = thumbOffset)
                .size(28.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/**
 * Holds an [AdaptiveSwitch]'s place while its value is not known yet: the bare track, with no
 * thumb, so it shows no value it hasn't read. The same footprint as the switch, so nothing moves
 * when the switch replaces it, and the same track as an off switch in each style: the M3 unchecked
 * track (its fill and outline) in Material, the glass fill in Liquid Glass. Purely visual; the
 * caller says it is loading.
 */
@Composable
fun AdaptiveSwitchPlaceholder(modifier: Modifier = Modifier) {
    val base = modifier.size(width = SwitchWidth, height = SwitchHeight)
    if (isLiquidGlass) {
        Box(base.background(GlassControlColors.fill, CircleShape))
    } else {
        Box(
            base
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
    }
}

private val ThumbTravel = 20.dp
private val SwitchWidth = 52.dp
private val SwitchHeight = 32.dp
