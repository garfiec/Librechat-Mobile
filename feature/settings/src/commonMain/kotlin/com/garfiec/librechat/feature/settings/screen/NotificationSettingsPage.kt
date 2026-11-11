package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.notifications.NotificationPermissionStatus
import com.garfiec.librechat.core.notifications.rememberNotificationPermissionRequest
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.notifications_off
import com.garfiec.librechat.feature.settings.resources.notifications_on
import com.garfiec.librechat.feature.settings.resources.notifications_permission
import com.garfiec.librechat.feature.settings.resources.notifications_replies
import com.garfiec.librechat.feature.settings.resources.notifications_replies_description
import com.garfiec.librechat.feature.settings.resources.notifications_replies_server_off
import com.garfiec.librechat.feature.settings.resources.notifications_scheduled
import com.garfiec.librechat.feature.settings.resources.notifications_scheduled_description
import com.garfiec.librechat.feature.settings.resources.notifications_system_settings
import com.garfiec.librechat.feature.settings.resources.notifications_system_settings_description
import com.garfiec.librechat.feature.settings.resources.notifications_turn_on
import com.garfiec.librechat.feature.settings.viewmodel.NotificationSettingsViewModel
import org.jetbrains.compose.resources.stringResource
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
    val allowed = uiState.permission == NotificationPermissionStatus.Allowed

    SettingsListPage(contentPadding = contentPadding, modifier = modifier) {
        item(key = "top_gap") { Spacer(modifier = Modifier.height(16.dp)) }
        adaptiveSection {
            row(key = "permission_row") {
                SettingsRow(
                    icon = Icons.Default.Notifications,
                    title = stringResource(Res.string.notifications_permission),
                    summary = stringResource(if (allowed) Res.string.notifications_on else Res.string.notifications_off),
                    trailingContent = if (allowed) {
                        null
                    } else {
                        {
                            AdaptiveTextButton(onClick = requestPermission) {
                                Text(stringResource(Res.string.notifications_turn_on))
                            }
                        }
                    },
                )
            }
            row(key = "replies_row") {
                SettingsSwitchRow(
                    icon = Icons.AutoMirrored.Filled.Reply,
                    title = stringResource(Res.string.notifications_replies),
                    description = stringResource(
                        if (uiState.serverAllowsReplies) {
                            Res.string.notifications_replies_description
                        } else {
                            Res.string.notifications_replies_server_off
                        },
                    ),
                    checked = uiState.repliesEnabled && uiState.serverAllowsReplies,
                    onCheckedChange = viewModel::setRepliesEnabled,
                    enabled = uiState.serverAllowsReplies,
                )
            }
            if (uiState.schedulesAvailable) {
                row(key = "scheduled_row") {
                    SettingsSwitchRow(
                        icon = Icons.Default.Schedule,
                        title = stringResource(Res.string.notifications_scheduled),
                        description = stringResource(
                            if (uiState.serverAllowsReplies) {
                                Res.string.notifications_scheduled_description
                            } else {
                                Res.string.notifications_replies_server_off
                            },
                        ),
                        checked = uiState.scheduledEnabled && uiState.serverAllowsReplies,
                        onCheckedChange = viewModel::setScheduledEnabled,
                        enabled = uiState.serverAllowsReplies,
                    )
                }
            }
            row(key = "system_settings_row") {
                SettingsRow(
                    icon = Icons.Default.Tune,
                    title = stringResource(Res.string.notifications_system_settings),
                    summary = stringResource(Res.string.notifications_system_settings_description),
                    onClick = viewModel::openSystemSettings,
                )
            }
        }
    }
}
