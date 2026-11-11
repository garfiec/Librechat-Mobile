package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.glass.GlassControlColors
import com.garfiec.librechat.core.ui.theme.isLiquidGlass

/**
 * A tile that is one switch: [icon], [label] (up to two lines) and a one-line [stateLabel] such as
 * "On" or "Set by …". A [locked] tile is announced disabled, shows a lock glyph and doesn't toggle.
 * [onLongClick] opens the tile's settings, announced with [onLongClickLabel].
 * Filled solid in both styles, as [AdaptiveTile]; [checked] fills it with the primary container in
 * Material and the control accent in Liquid Glass.
 */
@Composable
fun AdaptiveToggleTile(
    checked: Boolean,
    icon: ImageVector,
    label: String,
    stateLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    val glass = isLiquidGlass
    val scheme = MaterialTheme.colorScheme
    val fill = when {
        !checked -> tileFill()
        glass -> GlassControlColors.tint
        else -> scheme.primaryContainer
    }
    val contentColor = when {
        !checked -> scheme.onSurface
        glass -> GlassControlColors.onTint
        else -> scheme.onPrimaryContainer
    }
    val stateColor = if (checked) contentColor else GlassControlColors.secondaryLabel
    val click = when {
        locked -> Modifier
        onLongClick != null -> Modifier.combinedClickable(
            role = Role.Switch,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick,
            onClick = onClick,
        )
        else -> Modifier.clickable(role = Role.Switch, onClick = onClick)
    }
    Row(
        modifier = modifier
            .clip(TileShape)
            .background(fill)
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
                // The state line is read once, as the switch's state, not again as text.
                stateDescription = stateLabel
                if (locked) disabled()
            }
            .then(click)
            .heightIn(min = 60.dp)
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stateLabel,
                    modifier = Modifier.clearAndSetSemantics {},
                    style = MaterialTheme.typography.bodySmall,
                    color = stateColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (locked) Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * A tile on a sheet's page, one button when [onClick] is set. Solid in both styles: the sheet is
 * already glass, and a glass tile on it would double the effect.
 */
@Composable
fun AdaptiveTile(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(TileShape)
            .background(tileFill())
            .then(if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick)),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { content() }
    }
}

@Composable
@ReadOnlyComposable
private fun tileFill(): Color =
    if (isLiquidGlass) GlassControlColors.cell else MaterialTheme.colorScheme.surfaceContainerHigh

private val TileShape = RoundedCornerShape(16.dp)
