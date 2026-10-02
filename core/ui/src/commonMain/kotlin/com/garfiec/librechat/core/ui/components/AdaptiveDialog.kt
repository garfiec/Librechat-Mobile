package com.garfiec.librechat.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.garfiec.librechat.core.ui.components.topbar.CoversNativeBars

/** A [Dialog] that hides native glass bars while up: they'd draw over its scrim. */
@Composable
fun AdaptiveDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    CoversNativeBars()
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        InSeparateWindow(content)
    }
}
