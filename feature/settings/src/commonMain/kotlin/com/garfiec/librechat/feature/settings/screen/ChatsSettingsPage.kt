package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.data.datastore.ArtifactDisplayMode
import com.garfiec.librechat.core.ui.components.AdaptiveGroupedPage
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.feature.settings.navigation.AdvancedSection
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.ChatSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.ContextUsageSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Settings › Chats: how your own chats behave. Power settings sit behind the Advanced link. */
@Composable
fun ChatsSettingsPage(
    onNavigateToContextUsage: () -> Unit,
    onNavigateToArtifactShortcuts: () -> Unit,
    onOpenAdvancedSettings: (AdvancedSection) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: ChatSettingsViewModel = koinViewModel(),
    contextUsageViewModel: ContextUsageSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val gates by viewModel.gates.collectAsStateWithLifecycle()
    val contextUsage by contextUsageViewModel.uiState.collectAsStateWithLifecycle()
    val shortcutsSupported = rememberHomeScreenShortcutsSupported()
    var choosingArtifactViewer by remember { mutableStateOf(false) }

    AdaptiveGroupedPage(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
            item(key = "messages_header") {
                AdaptiveSectionHeader(stringResource(Res.string.section_messages))
            }
            adaptiveSection {
                row(key = "thinking_open_by_default") {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.thinking_open_by_default),
                        description = stringResource(Res.string.thinking_open_by_default_desc),
                        checked = uiState.thinkingOpenByDefault,
                        onCheckedChange = viewModel::setThinkingOpenByDefault,
                    )
                }
                row(key = "collapse_long_user_messages") {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.collapse_long_user_messages),
                        description = stringResource(Res.string.collapse_long_user_messages_desc),
                        checked = uiState.collapseLongUserMessages,
                        onCheckedChange = viewModel::setCollapseLongUserMessages,
                    )
                }
                row(key = "show_image_descriptions") {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.show_image_descriptions),
                        description = stringResource(Res.string.show_image_descriptions_desc),
                        checked = uiState.showImageDescriptions,
                        onCheckedChange = viewModel::setShowImageDescriptions,
                    )
                }
                row(key = "dismiss_keyboard_on_send") {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.dismiss_keyboard_on_send),
                        description = stringResource(Res.string.dismiss_keyboard_on_send_desc),
                        checked = uiState.dismissKeyboardOnSend,
                        onCheckedChange = viewModel::setDismissKeyboardOnSend,
                    )
                }
            }

            item(key = "artifacts_header") {
                AdaptiveSectionHeader(stringResource(Res.string.section_artifacts))
            }
            adaptiveSection {
                row(key = "artifact_viewer") {
                    SettingsRow(
                        title = stringResource(Res.string.artifact_viewer_title),
                        summary = artifactDisplayModeLabel(uiState.artifactDisplayPrefs.mode),
                        onClick = { choosingArtifactViewer = true },
                    )
                }
            }

            if (gates.contextUsage || shortcutsSupported) {
                item(key = "more_gap") { Spacer(modifier = Modifier.height(24.dp)) }
                adaptiveSection {
                    if (gates.contextUsage) {
                        row(key = "context_usage") {
                            SettingsRow(
                                title = stringResource(Res.string.context_bar_title),
                                summary = contextUsageSummary(
                                    contextUsage.placement,
                                    contextUsage.preset,
                                    contextUsage.advanced,
                                ),
                                onClick = onNavigateToContextUsage,
                            )
                        }
                    }
                    if (shortcutsSupported) {
                        row(key = "artifact_shortcuts") {
                            SettingsRow(
                                title = stringResource(Res.string.artifact_shortcuts_title),
                                onClick = onNavigateToArtifactShortcuts,
                            )
                        }
                    }
                }
            }

            item(key = "advanced_link") {
                AdvancedSettingsLink(
                    title = stringResource(Res.string.advanced_chat_settings),
                    summary = stringResource(Res.string.advanced_chat_settings_summary),
                    onClick = { onOpenAdvancedSettings(AdvancedSection.CHATS) },
                )
            }
        }

        if (choosingArtifactViewer) {
            RadioSelectionDialog(
                title = stringResource(Res.string.artifact_viewer_title),
                description = stringResource(Res.string.artifact_viewer_desc),
                options = ArtifactDisplayMode.entries,
                selected = uiState.artifactDisplayPrefs.mode,
                onSave = {
                    viewModel.setArtifactDisplayMode(it)
                    choosingArtifactViewer = false
                },
                onDismiss = { choosingArtifactViewer = false },
                optionLabel = { artifactDisplayModeLabel(it) },
                optionDescription = {
                    when (it) {
                        ArtifactDisplayMode.BOTTOM_SHEET -> stringResource(Res.string.artifact_mode_bottom_sheet_desc)
                        ArtifactDisplayMode.FULLSCREEN -> stringResource(Res.string.artifact_mode_fullscreen_desc)
                    }
                },
            )
        }
    }
}
