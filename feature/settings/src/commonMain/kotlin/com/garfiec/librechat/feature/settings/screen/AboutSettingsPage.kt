package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.settings.viewmodel.DataSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.GeneralSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.UpdateCheckViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Settings › About, after web's About tab: the app and server builds, Copy diagnostics, Report a
 * problem and the diagnostic logs, which every user may need for a bug report; then the privacy
 * policy and updates.
 */
@Composable
fun AboutSettingsPage(
    onNavigateToWhatsNew: () -> Unit,
    onNavigateToReleaseNotes: () -> Unit,
    onNavigateToReportProblem: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    viewModel: GeneralSettingsViewModel = koinViewModel(),
    updateViewModel: UpdateCheckViewModel = koinViewModel(),
    dataViewModel: DataSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()
    val data by dataViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(data.error) {
        val error = data.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = error)
        dataViewModel.dismissError()
    }
    DiagnosticLogsSaver(
        payload = data.logsExportReady,
        onConsume = dataViewModel::consumeLogsExport,
        snackbarHostState = snackbarHostState,
    )

    AboutSettingsContent(
        uiState = uiState,
        updateState = updateState,
        data = data,
        releaseNotesAvailable = updateViewModel.releaseNotesAvailable,
        updatesSupported = updateViewModel.isSupported,
        onNavigateToWhatsNew = onNavigateToWhatsNew,
        onNavigateToReleaseNotes = onNavigateToReleaseNotes,
        onNavigateToReportProblem = onNavigateToReportProblem,
        onExportLogs = dataViewModel::exportLogs,
        onClearLogs = dataViewModel::clearLogs,
        onCheckForUpdate = updateViewModel::check,
        onAutoCheckEnabledChange = updateViewModel::setAutoCheckEnabled,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}
