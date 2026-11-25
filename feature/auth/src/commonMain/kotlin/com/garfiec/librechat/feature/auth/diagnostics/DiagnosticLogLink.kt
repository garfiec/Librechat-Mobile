package com.garfiec.librechat.feature.auth.diagnostics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * "Export diagnostic logs", shown under a sign-in error so a user who can't get in still has
 * something to attach to a report.
 */
@Composable
fun DiagnosticLogLink(
    modifier: Modifier = Modifier,
    viewModel: DiagnosticLogExportViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DiagnosticLogLinkContent(
        state = state,
        onExport = viewModel::export,
        onSaveFinish = viewModel::onSaveFinished,
        onConsumePendingFile = viewModel::consumePendingFile,
        onHide = viewModel::onHidden,
        modifier = modifier,
    )
}
