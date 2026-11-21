package com.garfiec.librechat.feature.files.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.preview.DialogPreviewHost
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.feature.files.resources.Res
import com.garfiec.librechat.feature.files.resources.cancel
import com.garfiec.librechat.feature.files.resources.delete
import com.garfiec.librechat.feature.files.resources.delete_file_message
import com.garfiec.librechat.feature.files.resources.delete_file_question
import com.garfiec.librechat.feature.files.resources.discard
import com.garfiec.librechat.feature.files.resources.discard_selection_message
import com.garfiec.librechat.feature.files.resources.discard_selection_title
import com.garfiec.librechat.feature.files.resources.keep_selecting
import org.jetbrains.compose.resources.stringResource

// FilesScreen opens these inline, behind its ViewModel, so the previews repeat its calls with its
// strings.

@PreviewNarrowScreens
@Composable
private fun DiscardSelectionGlassPreview() = DiscardSelectionHost(UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun DeleteFileGlassPreview() = DeleteFileHost(UiStyle.LIQUID_GLASS)

@Composable
private fun DiscardSelectionHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    AdaptiveAlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(Res.string.discard_selection_title)) },
        text = { Text(stringResource(Res.string.discard_selection_message), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = {}) {
                Text(stringResource(Res.string.discard), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = {}) { Text(stringResource(Res.string.keep_selecting)) } },
    )
}

@Composable
private fun DeleteFileHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    val fileName = "2026-q3-regional-sales-forecast-with-supplier-breakdown-and-annotated-assumptions-final-v7-reviewed.xlsx"
    AdaptiveAlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(Res.string.delete_file_question)) },
        text = { Text(stringResource(Res.string.delete_file_message, fileName), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = {}) {
                Text(stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = {}) { Text(stringResource(Res.string.cancel)) } },
    )
}
