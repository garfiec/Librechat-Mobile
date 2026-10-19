package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties
import com.garfiec.librechat.core.ui.components.topbar.CoversNativeBars
import com.garfiec.librechat.core.ui.glass.GlassFloatingDialog
import com.garfiec.librechat.core.ui.glass.GlassPortal
import com.garfiec.librechat.core.ui.glass.LocalGlassSheetHost
import com.garfiec.librechat.core.ui.theme.isLiquidGlass

/**
 * In Liquid Glass, a glass panel portalled into the app canvas; outside a host or inside another
 * window it falls back to M3. [shape], the colours and [tonalElevation] apply to M3 only.
 *
 * [text] scrolls when it is taller than the room left (in glass, together with [title]). Pass
 * [scrollableText] false when the slot scrolls itself, with a `verticalScroll` or a lazy list: a
 * second scroll around it would measure it unbounded.
 */
@Composable
fun AdaptiveAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
    scrollableText: Boolean = true,
) {
    CoversNativeBars()
    val host = LocalGlassSheetHost.current
    if (isLiquidGlass && host != null && !LocalInSeparateWindow.current) {
        GlassPortal(host) {
            GlassFloatingDialog(
                onDismissRequest = onDismissRequest,
                properties = properties,
                confirmButton = confirmButton,
                modifier = modifier,
                dismissButton = dismissButton,
                icon = icon,
                title = title,
                text = text,
                scrollableText = scrollableText,
            )
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { InSeparateWindow(confirmButton) },
        modifier = modifier,
        dismissButton = dismissButton?.let { slot -> { InSeparateWindow(slot) } },
        icon = icon?.let { slot -> { InSeparateWindow(slot) } },
        title = title?.let { slot -> { InSeparateWindow(slot) } },
        text = text?.let { slot ->
            {
                InSeparateWindow {
                    if (scrollableText) Column(Modifier.verticalScroll(rememberScrollState())) { slot() } else slot()
                }
            }
        },
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties,
    )
}
