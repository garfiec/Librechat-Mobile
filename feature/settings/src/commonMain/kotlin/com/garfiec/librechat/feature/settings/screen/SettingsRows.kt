package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveSwitch
import com.garfiec.librechat.core.ui.components.adaptiveSelectedRowColor

/** The colour pair behind a row's icon tile, taken from the theme's container roles. */
internal enum class SettingsTileTint { NEUTRAL, PRIMARY, SECONDARY, TERTIARY, ERROR }

/** A row's own padding inside a grouped cell. Pass zero where the row sits in an already padded column. */
internal val SettingsRowPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

/**
 * A settings row: title with an optional summary below it, an optional icon tile, and a chevron
 * when it opens something ([trailingContent] replaces the chevron). Without [onClick] it is a plain
 * label. [destructive] paints the title and tile in the error colours. A non-null [selected] makes
 * it one of a list whose open item it marks (filled, read as selected, no chevron).
 */
@Composable
internal fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    destructive: Boolean = false,
    tileTint: SettingsTileTint = if (destructive) SettingsTileTint.ERROR else SettingsTileTint.NEUTRAL,
    enabled: Boolean = true,
    selected: Boolean? = null,
    showChevron: Boolean = onClick != null && !destructive && selected == null,
    showDivider: Boolean = true,
    contentPadding: PaddingValues = SettingsRowPadding,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val click = if (onClick == null) Modifier else Modifier.settingsRowClick(onClick, selected, enabled)
    val fill = if (selected == true) Modifier.background(adaptiveSelectedRowColor) else Modifier
    Column(modifier = modifier.then(fill)) {
        SettingsRowLayout(
            title = title,
            summary = summary,
            icon = icon,
            tileTint = tileTint,
            titleColor = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified,
            enabled = enabled,
            contentPadding = contentPadding,
            modifier = click,
        ) {
            when {
                trailingContent != null -> trailingContent()
                showChevron -> SettingsChevron()
            }
        }
        if (showDivider) SettingsRowDivider(hasIcon = icon != null)
    }
}

/** A row's tap: a non-null [selected] makes it a tab of a list whose open item it marks, else a button. */
internal fun Modifier.settingsRowClick(onClick: () -> Unit, selected: Boolean?, enabled: Boolean = true): Modifier =
    if (selected != null) {
        selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = onClick)
    } else {
        clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    }

@Composable
internal fun SettingsChevron(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        modifier = modifier.size(20.dp),
        tint = tint,
    )
}

/**
 * A row that is one switch: the whole row toggles and reads as a single switch to screen readers,
 * so the switch itself takes no clicks.
 */
@Composable
internal fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    tileTint: SettingsTileTint = SettingsTileTint.NEUTRAL,
    enabled: Boolean = true,
    showDivider: Boolean = true,
    contentPadding: PaddingValues = SettingsRowPadding,
) {
    Column(modifier = modifier) {
        SettingsRowLayout(
            title = title,
            summary = description,
            icon = icon,
            tileTint = tileTint,
            titleColor = Color.Unspecified,
            enabled = enabled,
            contentPadding = contentPadding,
            modifier = Modifier.toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        ) {
            Spacer(modifier = Modifier.width(16.dp))
            AdaptiveSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
        if (showDivider) SettingsRowDivider(hasIcon = icon != null)
    }
}

@Composable
private fun SettingsRowLayout(
    title: String,
    summary: String?,
    icon: ImageVector?,
    tileTint: SettingsTileTint,
    titleColor: Color,
    enabled: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val contentAlpha = if (enabled) 1f else DISABLED_ALPHA
        if (icon != null) {
            SettingsIconTile(icon = icon, tint = tileTint, modifier = Modifier.alpha(contentAlpha))
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f).alpha(contentAlpha)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}

@Composable
private fun SettingsIconTile(icon: ImageVector, tint: SettingsTileTint, modifier: Modifier = Modifier) {
    val (container, content) = tint.colors()
    Box(
        modifier = modifier
            .size(TileSize)
            .background(container, TileShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = content)
    }
}

/** Starts under the text when the row has a tile, as grouped lists inset their separators. */
@Composable
private fun SettingsRowDivider(hasIcon: Boolean) {
    AdaptiveDivider(modifier = Modifier.padding(start = if (hasIcon) TileSize + 16.dp else 0.dp))
}

@Composable
@ReadOnlyComposable
private fun SettingsTileTint.colors(): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        SettingsTileTint.NEUTRAL -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
        SettingsTileTint.PRIMARY -> scheme.primaryContainer to scheme.onPrimaryContainer
        SettingsTileTint.SECONDARY -> scheme.secondaryContainer to scheme.onSecondaryContainer
        SettingsTileTint.TERTIARY -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        SettingsTileTint.ERROR -> scheme.errorContainer to scheme.onErrorContainer
    }
}

/** A title and its current value; a tap lists the choices, given as (key, label) pairs. */
@Composable
internal fun SettingsChoiceRow(
    title: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    disabledKeys: Set<String> = emptySet(),
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        SettingsRow(
            title = title,
            summary = value,
            onClick = { if (enabled) expanded = true },
        )
        AdaptiveDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    enabled = key !in disabledKeys,
                    onClick = {
                        expanded = false
                        onSelect(key)
                    },
                )
            }
        }
    }
}

/**
 * The outlined link that ends a category page and opens Advanced at that category's section: power
 * settings stay one tap away without sitting among the everyday ones.
 */
@Composable
internal fun AdvancedSettingsLink(
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AdaptiveOutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
        contentPadding = SettingsRowPadding,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        SettingsChevron(tint = LocalContentColor.current)
    }
}

/** A small label over a few rows inside one cell or dialog. */
@Composable
internal fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

private val TileSize = 32.dp
private val TileShape = RoundedCornerShape(8.dp)
private const val DISABLED_ALPHA = 0.38f
