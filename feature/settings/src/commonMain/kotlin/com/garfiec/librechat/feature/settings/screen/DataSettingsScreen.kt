package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveGroupedPage
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.platform.TextFileSaver
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.DataSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.PrefetchActivityViewModel
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The Data tab's content, without a Scaffold or top bar. */
@Composable
fun DataSettingsContent(
    onNavigateToArchive: () -> Unit,
    onNavigateToSharedLinks: () -> Unit,
    onNavigateToArtifactShortcuts: () -> Unit,
    onNavigateToPrefetchSettings: () -> Unit,
    onNavigateToCodeExecution: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    viewModel: DataSettingsViewModel = koinViewModel(),
    prefetchViewModel: PrefetchActivityViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val prefetchState by prefetchViewModel.uiState.collectAsStateWithLifecycle()

    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showRevokeKeysDialog by remember { mutableStateOf(false) }

    // Diagnostic-log export → platform file saver (issue #96)
    var pendingLogsFileName by remember { mutableStateOf<String?>(null) }
    var pendingLogsContent by remember { mutableStateOf<String?>(null) }
    var logsSaverResult by remember { mutableStateOf<String?>(null) }
    val logsExportedMsg = stringResource(Res.string.logs_exported)
    val archivedAllMessage = uiState.archivedAllCount?.let {
        pluralStringResource(Res.plurals.archived_all_count, it, it)
    }

    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = error)
        viewModel.dismissError()
    }

    LaunchedEffect(archivedAllMessage) {
        val message = archivedAllMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = message)
        viewModel.consumeArchivedAllCount()
    }

    // When the ViewModel finishes reading the buffer, hand the payload to the platform saver.
    LaunchedEffect(uiState.logsExportReady) {
        val payload = uiState.logsExportReady ?: return@LaunchedEffect
        pendingLogsContent = payload.content
        pendingLogsFileName = payload.fileName
        viewModel.consumeLogsExport()
    }

    TextFileSaver(
        triggerFileName = pendingLogsFileName,
        content = pendingLogsContent,
        onComplete = { success, errorMessage ->
            if (success) {
                logsSaverResult = logsExportedMsg
            } else if (errorMessage != null) {
                logsSaverResult = errorMessage
            }
        },
        onReset = {
            pendingLogsFileName = null
            pendingLogsContent = null
        },
    )

    LaunchedEffect(logsSaverResult) {
        val msg = logsSaverResult ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = msg)
        logsSaverResult = null
    }

    AdaptiveGroupedPage(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            // Conversations section
            item(key = "conversations_header") {
                AdaptiveSectionHeader(stringResource(Res.string.section_conversations))
            }
            adaptiveSection {
                row(key = "data_settings") {
                    DataSettingsSection(
                        isClearing = uiState.isClearing,
                        onClearAllChats = viewModel::clearAllChats,
                        archiveAllSupported = uiState.archiveAllSupported,
                        isArchivingAll = uiState.isArchivingAll,
                        onArchiveAllChats = viewModel::archiveAllChats,
                        onViewArchive = onNavigateToArchive,
                        logsBufferBytes = uiState.logsBufferBytes,
                        isLogsExporting = uiState.isLogsExporting,
                        isLogsClearing = uiState.isLogsClearing,
                        onExportLogs = viewModel::exportLogs,
                        onClearLogs = viewModel::clearLogs,
                    )
                }
                // A summary row without a count: the list pages and never says how many links there are.
                row(key = "shared_chats_row") {
                    SettingsRow(
                        title = stringResource(Res.string.shared_chats_title),
                        summary = stringResource(Res.string.shared_chats_summary),
                        onClick = onNavigateToSharedLinks,
                    )
                }
                row(key = "data_extra_actions") {
                    DataExtraActions(
                        onArtifactShortcutsClick = onNavigateToArtifactShortcuts,
                        onClearCacheClick = { showClearCacheDialog = true },
                        isCacheClearing = uiState.isCacheClearing,
                        cacheSizeBytes = uiState.cacheSizeBytes,
                        onRevokeKeysClick = { showRevokeKeysDialog = true },
                        isKeyRevoking = uiState.isKeyRevoking,
                    )
                }
            }

            item(key = "prefetch_header") {
                AdaptiveSectionHeader(stringResource(Res.string.section_prefetch))
            }
            adaptiveSection {
                row(key = "prefetch_settings") {
                    SettingsRow(
                        title = stringResource(Res.string.prefetch_enabled),
                        summary = prefetchState.status.label(),
                        onClick = onNavigateToPrefetchSettings,
                    )
                }
            }

            // Web's Data controls › Code execution: the default workspace scope and the user's machines.
            if (uiState.codeExecutionEnabled) {
                item(key = "code_execution_header") {
                    AdaptiveSectionHeader(stringResource(Res.string.code_execution_title))
                }
                adaptiveSection {
                    row(key = "code_execution_row") {
                        SettingsRow(
                            title = stringResource(Res.string.code_environments),
                            summary = stringResource(Res.string.code_execution_summary),
                            onClick = onNavigateToCodeExecution,
                        )
                    }
                }
            }

            // Bottom spacing
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }

        // Clear cache confirmation
        if (showClearCacheDialog) {
            AdaptiveAlertDialog(
                onDismissRequest = { showClearCacheDialog = false },
                title = { Text(stringResource(Res.string.dialog_title_clear_cache)) },
                text = { Text(stringResource(Res.string.dialog_clear_cache_message)) },
                confirmButton = {
                    AdaptiveTextButton(
                        onClick = {
                            showClearCacheDialog = false
                            viewModel.clearCache()
                        },
                    ) {
                        Text(stringResource(Res.string.action_clear))
                    }
                },
                dismissButton = {
                    AdaptiveTextButton(onClick = { showClearCacheDialog = false }) {
                        Text(stringResource(Res.string.action_cancel))
                    }
                },
            )
        }

        // Revoke keys confirmation
        if (showRevokeKeysDialog) {
            AdaptiveAlertDialog(
                onDismissRequest = { showRevokeKeysDialog = false },
                title = { Text(stringResource(Res.string.dialog_title_revoke_keys)) },
                text = { Text(stringResource(Res.string.dialog_revoke_keys_message)) },
                confirmButton = {
                    AdaptiveTextButton(
                        onClick = {
                            showRevokeKeysDialog = false
                            viewModel.revokeAllKeys()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(stringResource(Res.string.action_revoke_all))
                    }
                },
                dismissButton = {
                    AdaptiveTextButton(onClick = { showRevokeKeysDialog = false }) {
                        Text(stringResource(Res.string.action_cancel))
                    }
                },
            )
        }
    }
}

@Composable
internal fun DataExtraActions(
    onArtifactShortcutsClick: () -> Unit,
    onClearCacheClick: () -> Unit,
    isCacheClearing: Boolean,
    cacheSizeBytes: Long?,
    onRevokeKeysClick: () -> Unit,
    isKeyRevoking: Boolean,
) {
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Home-screen artifact shortcuts
            AdaptiveOutlinedButton(
                onClick = onArtifactShortcutsClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                CenteredButtonLabel(
                    label = { Text(stringResource(Res.string.artifact_shortcuts_title), textAlign = TextAlign.Center) },
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }

            // Clear cache. The size is only shown once read and non-zero — a "(0 B)" on a cache that
            // simply hasn't been measured yet reads as a broken feature rather than an empty one.
            AdaptiveOutlinedButton(
                onClick = onClearCacheClick,
                enabled = !isCacheClearing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        isCacheClearing -> stringResource(Res.string.clearing)
                        cacheSizeBytes != null && cacheSizeBytes > 0 -> stringResource(
                            Res.string.clear_cache_with_size,
                            formatBytes(cacheSizeBytes),
                        )
                        else -> stringResource(Res.string.clear_cache)
                    },
                )
            }

            // Revoke API keys
            AdaptiveOutlinedButton(
                onClick = onRevokeKeysClick,
                enabled = !isKeyRevoking,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(if (isKeyRevoking) Res.string.revoking else Res.string.revoke_all_api_keys))
            }
        }
        AdaptiveDivider(modifier = Modifier.padding(top = 8.dp))
    }
}
