package com.garfiec.librechat.feature.schedules.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.notifications.ScheduledChatsNotificationAsk
import com.garfiec.librechat.feature.schedules.viewmodel.SchedulesListViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SchedulesListScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SchedulesListViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Nav3 retains this ViewModel, so `init` will not run again after the editor pops. Everything
    // on a card also moves without this client acting, which is why nothing here is cached.
    LaunchedEffect(Unit) { viewModel.refresh() }

    ScheduledChatsNotificationAsk(available = uiState.schedules.isNotEmpty(), snackbarHostState = snackbarHostState)

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    SchedulesListScreenContent(
        uiState = uiState,
        actions = SchedulesListActions(
            onBack = onBack,
            onCreate = onCreate,
            onEdit = onEdit,
            onOpenConversation = onOpenConversation,
            onSetEnabled = viewModel::setEnabled,
            onRunNow = { schedule -> viewModel.runNow(schedule, onOpenConversation) },
            onDelete = viewModel::delete,
            onOpenConsent = viewModel::openConsent,
            onCloseConsent = viewModel::closeConsent,
            onConsentHoursChange = viewModel::onConsentHoursChanged,
            onConfirmConsent = viewModel::confirmConsent,
            onRevokeConsent = viewModel::revokeConsent,
            onReloadConsent = viewModel::reloadConsent,
        ),
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}
