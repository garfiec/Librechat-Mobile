package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.platform.formatFileSize
import com.garfiec.librechat.feature.settings.navigation.AdvancedSection
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.ChatSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.DataSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.PrefetchActivityViewModel
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The confirmations this page asks for, one at a time. */
private enum class PrivacyConfirm { ARCHIVE_ALL, CLEAR_CACHE, DELETE_ALL }

/** Settings › Privacy & data: your chats, what this phone keeps, website icons, and deleting every chat. */
@Composable
fun PrivacySettingsPage(
    onNavigateToArchive: () -> Unit,
    onNavigateToSharedLinks: () -> Unit,
    onNavigateToPrefetchSettings: () -> Unit,
    onOpenAdvancedSettings: (AdvancedSection) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    viewModel: DataSettingsViewModel = koinViewModel(),
    prefetchViewModel: PrefetchActivityViewModel = koinViewModel(),
    chatViewModel: ChatSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val prefetchState by prefetchViewModel.uiState.collectAsStateWithLifecycle()
    val chat by chatViewModel.uiState.collectAsStateWithLifecycle()
    var confirming by remember { mutableStateOf<PrivacyConfirm?>(null) }

    LaunchedEffect(Unit) { viewModel.loadCacheSize() }
    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = error)
        viewModel.dismissError()
    }
    val archivedAllMessage = uiState.archivedAllCount?.let {
        pluralStringResource(Res.plurals.archived_all_count, it, it)
    }
    LaunchedEffect(archivedAllMessage) {
        val message = archivedAllMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = message)
        viewModel.consumeArchivedAllCount()
    }

    SettingsListPage(contentPadding = contentPadding, modifier = modifier) {
        item(key = "your_chats_header") {
            AdaptiveSectionHeader(stringResource(Res.string.section_your_chats))
        }
        adaptiveSection {
            row(key = "archived_chats") {
                SettingsRow(
                    title = stringResource(Res.string.archived_conversations),
                    onClick = onNavigateToArchive,
                )
            }
            // A summary row without a count: the list pages and never says how many links there are.
            row(key = "shared_chats") {
                SettingsRow(
                    title = stringResource(Res.string.shared_chats_title),
                    summary = stringResource(Res.string.shared_chats_summary),
                    onClick = onNavigateToSharedLinks,
                )
            }
            if (uiState.archiveAllSupported) {
                row(key = "archive_all") {
                    SettingsRow(
                        title = stringResource(Res.string.archive_all_conversations),
                        summary = if (uiState.isArchivingAll) stringResource(Res.string.archiving_all) else null,
                        onClick = { confirming = PrivacyConfirm.ARCHIVE_ALL },
                        enabled = !uiState.isArchivingAll,
                        showChevron = false,
                    )
                }
            }
        }

        item(key = "on_this_phone_header") {
            AdaptiveSectionHeader(stringResource(Res.string.section_on_this_phone))
        }
        adaptiveSection {
            row(key = "offline_reading") {
                SettingsRow(
                    title = stringResource(Res.string.offline_reading),
                    summary = prefetchState.status.label(),
                    onClick = onNavigateToPrefetchSettings,
                )
            }
            row(key = "clear_cache") {
                val size = uiState.cacheSizeBytes
                SettingsRow(
                    title = stringResource(Res.string.clear_cache),
                    // The size shows only once read and non-zero: "0 B" before it is measured
                    // reads as broken rather than empty.
                    summary = when {
                        uiState.isCacheClearing -> stringResource(Res.string.clearing)
                        size != null && size > 0 -> stringResource(Res.string.clear_cache_summary, formatBytes(size))
                        else -> null
                    },
                    onClick = { confirming = PrivacyConfirm.CLEAR_CACHE },
                    enabled = !uiState.isCacheClearing,
                    showChevron = false,
                )
            }
        }

        item(key = "privacy_header") {
            AdaptiveSectionHeader(stringResource(Res.string.section_privacy))
        }
        adaptiveSection {
            row(key = "show_site_icons") {
                SettingsSwitchRow(
                    title = stringResource(Res.string.show_site_icons),
                    description = stringResource(Res.string.show_site_icons_desc),
                    checked = chat.showSiteIcons,
                    onCheckedChange = chatViewModel::setShowSiteIcons,
                )
            }
        }

        item(key = "danger_header") {
            AdaptiveSectionHeader(stringResource(Res.string.section_danger_zone))
        }
        adaptiveSection {
            row(key = "delete_all_chats") {
                SettingsRow(
                    title = stringResource(Res.string.clear_all_conversations),
                    summary = if (uiState.isClearing) stringResource(Res.string.clearing) else null,
                    onClick = { confirming = PrivacyConfirm.DELETE_ALL },
                    enabled = !uiState.isClearing,
                    destructive = true,
                )
            }
        }

        // Code environments are this category's only power setting, so the link follows their gate.
        if (uiState.codeExecutionEnabled) {
            item(key = "advanced_link") {
                AdvancedSettingsLink(
                    title = stringResource(Res.string.advanced_data_settings),
                    summary = stringResource(Res.string.code_environments),
                    onClick = { onOpenAdvancedSettings(AdvancedSection.CODE_ENVIRONMENTS) },
                )
            }
        }
    }

    when (confirming) {
        PrivacyConfirm.ARCHIVE_ALL -> ConfirmDialog(
            title = Res.string.dialog_title_archive_all,
            message = Res.string.dialog_archive_all_message,
            confirmLabel = Res.string.action_archive_all,
            destructive = false,
            onConfirm = viewModel::archiveAllChats,
            onDismiss = { confirming = null },
        )
        PrivacyConfirm.CLEAR_CACHE -> ConfirmDialog(
            title = Res.string.dialog_title_clear_cache,
            message = Res.string.dialog_clear_cache_message,
            confirmLabel = Res.string.action_clear,
            destructive = false,
            onConfirm = viewModel::clearCache,
            onDismiss = { confirming = null },
        )
        PrivacyConfirm.DELETE_ALL -> ConfirmDialog(
            title = Res.string.dialog_title_clear_conversations,
            message = Res.string.dialog_clear_conversations_message,
            confirmLabel = Res.string.clear_all,
            destructive = true,
            onConfirm = viewModel::clearAllChats,
            onDismiss = { confirming = null },
        )
        null -> Unit
    }
}

/** A byte size as the Files screen and the chat's file chips show it (e.g. `1.2 MB`). */
internal fun formatBytes(bytes: Long): String = formatFileSize(bytes)
