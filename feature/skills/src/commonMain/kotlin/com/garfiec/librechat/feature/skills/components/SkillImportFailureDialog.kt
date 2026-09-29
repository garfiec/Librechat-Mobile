package com.garfiec.librechat.feature.skills.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.response.SkillImportFailedFile
import com.garfiec.librechat.core.model.response.SkillImportFailedResponse
import com.garfiec.librechat.core.model.response.SkillImportFailureReason
import com.garfiec.librechat.feature.skills.resources.*
import com.garfiec.librechat.feature.skills.resources.Res
import org.jetbrains.compose.resources.stringResource

/**
 * The per-file report of an import the server refused (v0.8.8-rc4). A dialog rather than a
 * snackbar, as the web keeps its upload dialog open: the archive has to be fixed before a retry
 * can succeed, and these paths are the only place the user learns which files it lost.
 */
@Composable
internal fun SkillImportFailureDialog(
    failure: SkillImportFailedResponse,
    onDismiss: () -> Unit,
) {
    val count = failure.failedFiles.size
    val (summary, heading) = when (failure.error) {
        SkillImportFailedResponse.ROLLBACK_FAILED ->
            stringResource(Res.string.skill_import_rollback_failed, count) to
                stringResource(Res.string.skill_import_rollback_failed_files)
        SkillImportFailedResponse.CLEANUP_INCOMPLETE ->
            stringResource(Res.string.skill_import_cleanup_incomplete, count) to
                stringResource(Res.string.skill_import_cleanup_incomplete_files)
        else ->
            stringResource(Res.string.skill_import_incomplete, count) to
                stringResource(Res.string.skill_import_failed_files)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.skill_import_failed_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(summary, style = MaterialTheme.typography.bodyMedium)
                if (failure.failedFiles.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(heading, style = MaterialTheme.typography.labelLarge)
                    failure.failedFiles.forEach { file ->
                        Column(Modifier.padding(top = 8.dp)) {
                            Text(file.path, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                failedFileReason(file),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.skill_conflict_dismiss)) }
        },
    )
}

@Composable
private fun failedFileReason(file: SkillImportFailedFile): String {
    val limit = file.limitMb?.let(::formatMegabytes)
    return when {
        file.reason == SkillImportFailureReason.INVALID_PATH ->
            stringResource(Res.string.skill_import_reason_invalid_path)
        file.reason == SkillImportFailureReason.FILE_TOO_LARGE && limit != null ->
            stringResource(Res.string.skill_import_reason_file_too_large, limit)
        file.reason == SkillImportFailureReason.ARCHIVE_TOO_LARGE && limit != null ->
            stringResource(Res.string.skill_import_reason_archive_too_large, limit)
        file.reason == SkillImportFailureReason.ARCHIVE_ENTRY_CHANGED ->
            stringResource(Res.string.skill_import_reason_archive_entry_changed)
        file.reason == SkillImportFailureReason.PERSISTENCE_FAILED ->
            stringResource(Res.string.skill_import_reason_persistence_failed)
        // A newer server's reason, or a size reason that arrived without its limit.
        else -> stringResource(Res.string.skill_import_reason_unknown)
    }
}

/** `5.0` reads as `5`; a fractional limit keeps its decimals. */
internal fun formatMegabytes(mb: Double): String =
    if (mb % 1.0 == 0.0) mb.toLong().toString() else mb.toString()
