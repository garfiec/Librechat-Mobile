package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.garfiec.librechat.core.ui.glass.GlassDefaults
import com.garfiec.librechat.core.ui.glass.LocalGlassBackdrop
import com.garfiec.librechat.core.ui.glass.glassSurface
import com.garfiec.librechat.core.ui.glass.rememberGlassStyle
import com.garfiec.librechat.core.ui.theme.LocalDarkTheme

/**
 * A tile that is one switch: [icon], [label] (up to two lines) and a one-line [stateLabel] such as
 * "On" or "Set by …". A [locked] tile is announced disabled, shows a lock glyph and doesn't toggle.
 * Material fills it with the primary container when [checked]; Liquid Glass draws a glass panel,
 * tinted with the control accent when [checked].
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
) {
    val style = rememberGlassStyle()
    val scheme = MaterialTheme.colorScheme
    val surface = if (style == null) {
        Modifier.background(if (checked) scheme.primaryContainer else scheme.surfaceContainerHigh)
    } else {
        Modifier
            .glassSurface(style, ToggleTileShape, LocalGlassBackdrop.current)
            .background(
                if (checked) GlassControlColors.tint else GlassDefaults.panelThickening(scheme.surface, LocalDarkTheme.current),
            )
    }
    val contentColor = when {
        !checked -> scheme.onSurface
        style == null -> scheme.onPrimaryContainer
        else -> Color.White
    }
    val stateColor = if (checked) contentColor else GlassControlColors.secondaryLabel
    val click = if (locked) Modifier else Modifier.clickable(role = Role.Switch, onClick = onClick)
    Row(
        modifier = modifier
            .clip(ToggleTileShape)
            .then(surface)
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

private val ToggleTileShape = RoundedCornerShape(16.dp)
