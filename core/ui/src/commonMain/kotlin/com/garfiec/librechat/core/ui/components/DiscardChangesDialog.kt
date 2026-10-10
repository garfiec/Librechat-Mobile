package com.garfiec.librechat.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.discard_changes_cancel
import com.garfiec.librechat.core.ui.resources.discard_changes_confirm
import com.garfiec.librechat.core.ui.resources.discard_changes_message
import com.garfiec.librechat.core.ui.resources.discard_changes_title
import org.jetbrains.compose.resources.stringResource

/**
 * Asks before an editor with unsaved edits closes. Pair it with a [PlatformBackHandler] enabled only
 * while the form is dirty, and route the top bar's back through the same check: the handler never
 * fires on iOS, so the bar button is the only guarded exit there.
 */
@Composable
fun DiscardChangesDialog(onDiscard: () -> Unit, onDismiss: () -> Unit) {
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.discard_changes_title)) },
        text = { Text(stringResource(Res.string.discard_changes_message)) },
        confirmButton = {
            TextButton(onClick = onDiscard) {
                Text(stringResource(Res.string.discard_changes_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.discard_changes_cancel))
            }
        },
    )
}
