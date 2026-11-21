package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.notifications.rememberNotificationPermissionRequest
import com.garfiec.librechat.feature.settings.viewmodel.NotificationSettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Settings › Notifications: whether the app may notify, which notifications, and the system's own page. */
@Composable
fun NotificationSettingsPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: NotificationSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val requestPermission = rememberNotificationPermissionRequest(onResult = {})

    NotificationSettingsContent(
        uiState = uiState,
        onAllEnabledChange = { on ->
            viewModel.setAllEnabled(on)
            if (on) requestPermission()
        },
        onRepliesEnabledChange = viewModel::setRepliesEnabled,
        onScheduledEnabledChange = viewModel::setScheduledEnabled,
        onOpenSystemSettings = viewModel::openSystemSettings,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}
