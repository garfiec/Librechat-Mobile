package com.garfiec.librechat.core.ui.components.topbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.GlassCapability
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.LocalInSeparateWindow
import com.garfiec.librechat.core.ui.components.MenuDragSelection
import com.garfiec.librechat.core.ui.components.consumeUnhandledTouches
import com.garfiec.librechat.core.ui.components.dragSelectRowIcon
import com.garfiec.librechat.core.ui.components.menuDragAnchor
import com.garfiec.librechat.core.ui.components.menuDragPressEffect
import com.garfiec.librechat.core.ui.components.menuDragTarget
import com.garfiec.librechat.core.ui.components.rememberMenuDragSelection
import com.garfiec.librechat.core.ui.glass.LocalGlassBackdrop
import com.garfiec.librechat.core.ui.glass.glassSurface
import com.garfiec.librechat.core.ui.glass.rememberGlassStyle
import com.garfiec.librechat.core.ui.theme.LocalGlassLevel

private val ControlSize = 44.dp

/**
 * An M3 `TopAppBar` in Material, the system glass bar on iOS 26+, a simulated glass bar elsewhere.
 * [belowBar] holds Compose content that can't be bar data (a search field, a tab row).
 */
@Composable
fun AdaptiveTopBar(
    spec: AdaptiveTopBarSpec,
    modifier: Modifier = Modifier,
    materialStyle: MaterialBarStyle = MaterialBarStyle(),
    belowBar: (@Composable () -> Unit)? = null,
) {
    val level = LocalGlassLevel.current
    if (level == null) {
        if (belowBar == null) {
            MaterialTopBar(spec, materialStyle, modifier)
        } else {
            Column(modifier = modifier) {
                MaterialTopBar(spec, materialStyle)
                belowBar()
            }
        }
        return
    }
    Column(modifier = modifier) {
        Box {
            // The native bar's scroll-edge effect only follows a UIScrollView, and a Compose list is not
            // one, so the fade that keeps the title legible over scrolling content is drawn here.
            Box(Modifier.matchParentSize().background(glassEdgeScrim()))
            if (level == GlassCapability.NATIVE && !LocalInSeparateWindow.current) {
                NativeGlassTopBar(spec = spec, modifier = Modifier.fillMaxWidth())
            } else {
                SimulatedGlassTopBar(spec = spec, modifier = Modifier.fillMaxWidth())
            }
        }
        belowBar?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialTopBar(spec: AdaptiveTopBarSpec, style: MaterialBarStyle, modifier: Modifier = Modifier) {
    var renaming by remember { mutableStateOf(false) }
    val title: @Composable () -> Unit = {
        val text = spec.title.text
        if (text != null) {
            val onTitleClick = spec.title.onClick ?: spec.title.rename?.let { { renaming = true } }
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier,
            )
        }
    }
    val navigationIcon: @Composable () -> Unit = {
        spec.navigation?.let { nav ->
            IconButton(onClick = nav.onClick) {
                Icon(imageVector = nav.icon.vector, contentDescription = nav.contentDescription)
            }
        }
    }
    val actions: @Composable RowScope.() -> Unit = {
        val colors = BarActionColors(default = Color.Unspecified, accent = MaterialTheme.colorScheme.primary)
        spec.actions.forEach { BarActionButton(it, colors) }
    }
    val customColor = style.containerColor.takeIf { it != Color.Unspecified }
    if (spec.title.alignment == BarTitleAlignment.CENTER) {
        CenterAlignedTopAppBar(
            title = title,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = if (customColor != null) {
                TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = customColor)
            } else {
                TopAppBarDefaults.centerAlignedTopAppBarColors()
            },
        )
    } else {
        TopAppBar(
            title = title,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = if (customColor != null) {
                TopAppBarDefaults.topAppBarColors(containerColor = customColor)
            } else {
                TopAppBarDefaults.topAppBarColors()
            },
        )
    }
    spec.title.rename?.let { rename ->
        if (renaming) BarRenameDialog(rename = rename, onDismiss = { renaming = false })
    }
}

/**
 * What separates the two Compose renderers' controls. An unspecified [default] keeps the button's
 * own content colour (Material), which dims a disabled icon.
 */
private class BarActionColors(val default: Color, val accent: Color, val glass: Boolean = false)

@Composable
private fun BarActionButton(action: BarAction, colors: BarActionColors) {
    when (action) {
        is BarAction.Icon -> IconButton(onClick = action.onClick, enabled = action.enabled && !action.busy) {
            if (action.busy) {
                AdaptiveCircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Icon(
                    imageVector = action.icon.vector,
                    contentDescription = action.label,
                    tint = when (action.tint) {
                        BarTint.DEFAULT -> colors.default.takeOrElse { LocalContentColor.current }
                        BarTint.DESTRUCTIVE -> if (action.enabled) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    },
                )
            }
        }

        is BarAction.Text -> TextButton(onClick = action.onClick, enabled = action.enabled) {
            // Glass labels its text actions in the accent, as iOS does; Material keeps TextButton's own.
            Text(action.label, color = if (colors.glass && action.enabled) colors.accent else Color.Unspecified)
        }

        is BarAction.Toggle -> IconButton(onClick = { action.onCheckedChange(!action.checked) }) {
            Icon(
                imageVector = if (action.checked) action.iconOn.vector else action.iconOff.vector,
                contentDescription = action.label,
                tint = if (colors.glass && action.checked) colors.accent else colors.default.takeOrElse { LocalContentColor.current },
            )
        }

        is BarAction.Menu -> {
            var expanded by remember { mutableStateOf(false) }
            val drag = if (action.dragToSelect) rememberMenuDragSelection() else null
            Box(
                modifier = if (drag != null) {
                    Modifier.menuDragAnchor(drag, onOpen = { expanded = true }, onCancel = { expanded = false })
                } else {
                    Modifier
                },
            ) {
                IconButton(
                    onClick = { expanded = true },
                    modifier = if (drag != null) Modifier.menuDragPressEffect(drag) else Modifier,
                ) {
                    Icon(imageVector = action.icon.vector, contentDescription = action.label)
                }
                BarMenu(action, expanded, onDismiss = { expanded = false }, drag, colors)
            }
        }
    }
}

@Composable
private fun BarMenu(
    action: BarAction.Menu,
    expanded: Boolean,
    onDismiss: () -> Unit,
    drag: MenuDragSelection?,
    colors: BarActionColors,
) {
    AdaptiveDropdownMenu(expanded = expanded, onDismissRequest = onDismiss, dragSelection = drag) {
        action.sections.filter { it.items.isNotEmpty() }.forEachIndexed { index, section ->
            if (index > 0) {
                AdaptiveDivider(modifier = if (colors.glass) Modifier.padding(vertical = 4.dp) else Modifier)
            }
            section.items.forEach { item ->
                BarDropdownItem(item = item, onSelect = onDismiss, checkTint = colors.accent, drag = drag)
            }
        }
    }
}

@Composable
private fun BarDropdownItem(item: BarMenuItem, onSelect: () -> Unit, checkTint: Color, drag: MenuDragSelection?) {
    val error = MaterialTheme.colorScheme.error
    val select = {
        onSelect()
        item.onClick()
    }
    DropdownMenuItem(
        modifier = if (item.enabled) Modifier.menuDragTarget(drag, select) else Modifier,
        text = {
            if (item.subtitle == null) {
                Text(item.label, color = if (item.destructive) error else Color.Unspecified)
            } else {
                Column {
                    Text(item.label, color = if (item.destructive) error else Color.Unspecified)
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        onClick = select,
        enabled = item.enabled,
        leadingIcon = item.icon?.let { icon ->
            {
                if (item.destructive) {
                    Icon(icon.vector, contentDescription = null, tint = error, modifier = Modifier.dragSelectRowIcon())
                } else {
                    Icon(icon.vector, contentDescription = null, modifier = Modifier.dragSelectRowIcon())
                }
            }
        },
        trailingIcon = if (item.checked) {
            { Icon(Icons.Default.Check, contentDescription = null, tint = checkTint) }
        } else {
            null
        },
    )
}

/** The UIKit bar on iOS; never reached on Android, whose actual forwards to the simulated bar. */
@Composable
internal expect fun NativeGlassTopBar(spec: AdaptiveTopBarSpec, modifier: Modifier = Modifier)

@Composable
private fun glassEdgeScrim(): Brush {
    val surface = MaterialTheme.colorScheme.surface
    return remember(surface) {
        // Nearly opaque through the bar, so a title never sits on legible text, then a short fade at
        // the bottom edge where content slides under.
        Brush.verticalGradient(
            0f to surface.copy(alpha = 0.97f),
            0.8f to surface.copy(alpha = 0.93f),
            1f to Color.Transparent,
        )
    }
}

@Composable
internal fun SimulatedGlassTopBar(spec: AdaptiveTopBarSpec, modifier: Modifier = Modifier) {
    val style = rememberGlassStyle() ?: return
    val backdrop = LocalGlassBackdrop.current
    var renaming by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .consumeUnhandledTouches()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        spec.navigation?.let { nav ->
            Box(Modifier.size(ControlSize).glassSurface(style, CircleShape, backdrop), contentAlignment = Alignment.Center) {
                IconButton(onClick = nav.onClick) {
                    Icon(nav.icon.vector, contentDescription = nav.contentDescription)
                }
            }
            Spacer(Modifier.width(8.dp))
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = when (spec.title.alignment) {
                BarTitleAlignment.CENTER -> Alignment.Center
                BarTitleAlignment.LEADING, BarTitleAlignment.FILL -> Alignment.CenterStart
            },
        ) {
            val title = spec.title
            val text = title.text
            if (text != null) {
                val onTitleClick = title.onClick ?: title.rename?.let { { renaming = true } }
                val fill = title.alignment == BarTitleAlignment.FILL
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .then(if (fill) Modifier.fillMaxWidth() else Modifier)
                        .then(
                            if (title.style == BarTitleStyle.CHIP) {
                                Modifier.glassSurface(style, CircleShape, backdrop)
                            } else {
                                Modifier
                            },
                        )
                        .then(if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }

        if (spec.actions.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Row(
                modifier = Modifier.glassSurface(style, CircleShape, backdrop),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val colors = BarActionColors(
                    default = MaterialTheme.colorScheme.onSurface,
                    accent = MaterialTheme.colorScheme.primary,
                    glass = true,
                )
                spec.actions.forEach { action -> BarActionButton(action, colors) }
            }
        }
    }

    spec.title.rename?.let { rename ->
        if (renaming) BarRenameDialog(rename = rename, onDismiss = { renaming = false })
    }
}

/** The text prompt behind [BarRename] on the Compose renderer; the native bar uses a UIAlertController. */
@Composable
internal fun BarRenameDialog(rename: BarRename, onDismiss: () -> Unit) {
    var value by remember(rename.initial) { mutableStateOf(rename.initial) }
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(rename.dialogTitle) },
        text = { AdaptiveOutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true) },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = value.trim()
                    if (trimmed.isNotEmpty() && trimmed != rename.initial) rename.onCommit(trimmed)
                    onDismiss()
                },
            ) { Text(rename.confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(rename.cancelLabel) } },
    )
}
