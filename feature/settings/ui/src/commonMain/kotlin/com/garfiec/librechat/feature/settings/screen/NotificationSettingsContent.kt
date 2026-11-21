package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.notifications_permission
import com.garfiec.librechat.feature.settings.resources.notifications_replies
import com.garfiec.librechat.feature.settings.resources.notifications_replies_description
import com.garfiec.librechat.feature.settings.resources.notifications_replies_server_off
import com.garfiec.librechat.feature.settings.resources.notifications_scheduled
import com.garfiec.librechat.feature.settings.resources.notifications_scheduled_description
import com.garfiec.librechat.feature.settings.resources.notifications_system_settings
import com.garfiec.librechat.feature.settings.resources.notifications_system_settings_description
import com.garfiec.librechat.feature.settings.viewmodel.NotificationSettingsUiState
import org.jetbrains.compose.resources.stringResource

/** Settings › Notifications' rows for [uiState]; [onAllEnabledChange] is the master switch. */
@Composable
fun NotificationSettingsContent(
    uiState: NotificationSettingsUiState,
    onAllEnabledChange: (Boolean) -> Unit,
    onRepliesEnabledChange: (Boolean) -> Unit,
    onScheduledEnabledChange: (Boolean) -> Unit,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    SettingsListPage(contentPadding = contentPadding, modifier = modifier) {
        item(key = "top_gap") { Spacer(modifier = Modifier.height(16.dp)) }
        adaptiveSection {
            row(key = "permission_row") {
                SettingsSwitchRow(
                    icon = Icons.Default.Notifications,
                    title = stringResource(Res.string.notifications_permission),
                    checked = uiState.on,
                    onCheckedChange = onAllEnabledChange,
                    enabled = uiState.serverAllowsReplies,
                )
            }
        }
        item(key = "kinds_gap") { Spacer(modifier = Modifier.height(24.dp)) }
        adaptiveSection {
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
                    onCheckedChange = onRepliesEnabledChange,
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
                        onCheckedChange = onScheduledEnabledChange,
                        enabled = uiState.serverAllowsReplies,
                    )
                }
            }
        }
        item(key = "system_settings_gap") { Spacer(modifier = Modifier.height(24.dp)) }
        adaptiveSection {
            row(key = "system_settings_row") {
                SettingsRow(
                    icon = Icons.Default.Tune,
                    title = stringResource(Res.string.notifications_system_settings),
                    summary = stringResource(Res.string.notifications_system_settings_description),
                    onClick = onOpenSystemSettings,
                )
            }
        }
    }
}
