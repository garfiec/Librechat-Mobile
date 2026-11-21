package com.garfiec.librechat.feature.auth.diagnostics

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.garfiec.librechat.core.ui.platform.TextFileSaver
import com.garfiec.librechat.feature.auth.resources.Res
import com.garfiec.librechat.feature.auth.resources.diagnostic_log_export
import com.garfiec.librechat.feature.auth.resources.diagnostic_log_exported
import com.garfiec.librechat.feature.auth.resources.diagnostic_log_failed
import org.jetbrains.compose.resources.stringResource

/**
 * "Export diagnostic logs", shown under a sign-in error so a user who can't get in still has
 * something to attach to a report. [onHide] runs when the link leaves the composition.
 */
@Composable
fun DiagnosticLogLinkContent(
    state: DiagnosticLogExportState,
    onExport: () -> Unit,
    onSaveFinish: (success: Boolean, cancelled: Boolean) -> Unit,
    onConsumePendingFile: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var saveFileName by remember { mutableStateOf<String?>(null) }
    var saveContent by remember { mutableStateOf<String?>(null) }
    val currentOnHide by rememberUpdatedState(onHide)
    val currentOnConsumePendingFile by rememberUpdatedState(onConsumePendingFile)

    DisposableEffect(Unit) {
        onDispose { currentOnHide() }
    }

    LaunchedEffect(state.pendingFile) {
        val file = state.pendingFile ?: return@LaunchedEffect
        saveContent = file.content
        saveFileName = file.fileName
        currentOnConsumePendingFile()
    }

    TextFileSaver(
        triggerFileName = saveFileName,
        content = saveContent,
        onComplete = { success, message -> onSaveFinish(success, !success && message == null) },
        onReset = {
            saveFileName = null
            saveContent = null
        },
    )

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = onExport, enabled = !state.isExporting) {
            Text(stringResource(Res.string.diagnostic_log_export))
        }
        val outcome = when (state.outcome) {
            DiagnosticLogOutcome.Exported -> stringResource(Res.string.diagnostic_log_exported)
            DiagnosticLogOutcome.Failed -> stringResource(Res.string.diagnostic_log_failed)
            null -> null
        }
        if (outcome != null) {
            Text(
                text = outcome,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.outcome == DiagnosticLogOutcome.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
