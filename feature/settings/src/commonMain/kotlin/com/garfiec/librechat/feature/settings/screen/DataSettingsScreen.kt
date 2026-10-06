package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveGroupedPage
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.feature.settings.platform.LogFileSaver
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.PrefetchActivityViewModel
import com.garfiec.librechat.feature.settings.viewmodel.SettingsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToArchive: () -> Unit,
    onNavigateToSharedLinks: () -> Unit,
    onNavigateToArtifactShortcuts: () -> Unit,
    onNavigateToPrefetchSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    AdaptiveScaffold(
        modifier = modifier,
        snackbarHost = { AdaptiveSnackbarHost(snackbarHostState) },
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), onNavigateBack),
                    title = BarTitle(stringResource(Res.string.title_data)),
                    actions = emptyList(),
                ),
            )
        },
    ) { innerPadding ->
        DataSettingsContent(
            onNavigateToArchive = onNavigateToArchive,
            onNavigateToSharedLinks = onNavigateToSharedLinks,
            onNavigateToArtifactShortcuts = onNavigateToArtifactShortcuts,
            onNavigateToPrefetchSettings = onNavigateToPrefetchSettings,
            snackbarHostState = snackbarHostState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

/**
 * Reusable Data settings content (without Scaffold/TopAppBar).
 * Used by both the standalone screen and the tabbed settings screen.
 */
@Composable
fun DataSettingsContent(
    onNavigateToArchive: () -> Unit,
    onNavigateToSharedLinks: () -> Unit,
    onNavigateToArtifactShortcuts: () -> Unit,
    onNavigateToPrefetchSettings: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    viewModel: SettingsViewModel = koinViewModel(),
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
        stringResource(Res.string.archived_all_count, it)
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

    LaunchedEffect(uiState.showExportComingSoon) {
        if (uiState.showExportComingSoon) {
            snackbarHostState.showSnackbar(message = "Export is coming soon")
            viewModel.dismissExportComingSoon()
        }
    }

    // When the ViewModel finishes reading the buffer, hand the payload to the platform saver.
    LaunchedEffect(uiState.logsExportReady) {
        val payload = uiState.logsExportReady ?: return@LaunchedEffect
        pendingLogsContent = payload.content
        pendingLogsFileName = payload.fileName
        viewModel.consumeLogsExport()
    }

    LogFileSaver(
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

    LaunchedEffect(uiState.mcpReinitializeMessage) {
        val message = uiState.mcpReinitializeMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissMcpReinitializeMessage()
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
                        archivedCount = uiState.archivedCount,
                        isClearing = uiState.isClearing,
                        onClearAllChats = viewModel::clearAllChats,
                        archiveAllSupported = uiState.archiveAllSupported,
                        isArchivingAll = uiState.isArchivingAll,
                        onArchiveAllChats = viewModel::archiveAllChats,
                        onViewArchive = onNavigateToArchive,
                        onExportAllData = viewModel::exportAllData,
                        logsBufferBytes = uiState.logsBufferBytes,
                        isLogsExporting = uiState.isLogsExporting,
                        isLogsClearing = uiState.isLogsClearing,
                        onExportLogs = viewModel::exportLogs,
                        onClearLogs = viewModel::clearLogs,
                    )
                }
                row(key = "data_extra_actions") {
                    DataExtraActions(
                        onSharedLinksClick = onNavigateToSharedLinks,
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
                    SelectorRow(
                        title = stringResource(Res.string.prefetch_enabled),
                        value = prefetchState.status.label(),
                        onClick = onNavigateToPrefetchSettings,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            // Memories section — hidden entirely when the server's MEMORIES.USE role
            // permission is denied. The user-level opt-out (`memoriesEnabled`) stays
            // independent and only hides the list inside this section.
            if (uiState.serverMemoriesEnabled) {
                item(key = "memories_header") {
                    AdaptiveSectionHeader(stringResource(Res.string.section_memories))
                }
                adaptiveSection {
                    row(key = "memories_settings") {
                        MemoriesSettingsSection(
                            memories = uiState.memories,
                            memoriesEnabled = uiState.memoriesEnabled,
                            showMemoryDialog = uiState.showMemoryDialog,
                            editingMemory = uiState.editingMemory,
                            enforceKeyPattern = uiState.memoryKeyPatternEnforced,
                            onToggleEnable = viewModel::toggleMemoriesEnabled,
                            onAddMemory = viewModel::showAddMemoryDialog,
                            onEditMemory = viewModel::showEditMemoryDialog,
                            onDeleteMemory = viewModel::deleteMemory,
                            onDismissDialog = viewModel::dismissMemoryDialog,
                            onSaveMemory = viewModel::saveMemory,
                        )
                    }
                }
            }

            // MCP section — always shown, but the section body degrades to
            // "not available" and the "+ Add" button disappears when role denies.
            item(key = "mcp_header") {
                AdaptiveSectionHeader(stringResource(Res.string.section_mcp_servers))
            }
            adaptiveSection {
                row(key = "mcp_settings") {
                    McpSettingsSection(
                        servers = uiState.mcpServers,
                        connectionStatus = uiState.mcpConnectionStatus,
                        reinitializingServers = uiState.mcpReinitializingServers,
                        error = uiState.mcpError,
                        mcpServersEnabled = uiState.mcpServersEnabled,
                        mcpServersCreateEnabled = uiState.mcpServersCreateEnabled,
                        onAddServer = viewModel::showAddMcpServerDialog,
                        onEditServer = viewModel::showEditMcpServerDialog,
                        onDeleteServer = viewModel::deleteMcpServer,
                        onReinitialize = viewModel::reinitializeMcpServer,
                    )
                }
            }

            // Bottom spacing
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }

        // MCP server add/edit dialog
        if (uiState.showMcpServerDialog) {
            McpServerDialog(
                editingServer = uiState.editingMcpServer,
                oauthSecretReentryRequired = uiState.mcpOAuthSecretReentryRequired,
                apiKeyReentryRequired = uiState.mcpApiKeyReentryRequired,
                saveError = uiState.mcpServerDialogError,
                isSaving = uiState.mcpServerSaving,
                onDismiss = viewModel::dismissMcpServerDialog,
                onSave = { name, description, url, type, apiKey, oauth ->
                    viewModel.saveMcpServer(name, description, url, type, apiKey, oauth)
                },
            )
        }

        // Clear cache confirmation
        if (showClearCacheDialog) {
            AdaptiveAlertDialog(
                onDismissRequest = { showClearCacheDialog = false },
                title = { Text(stringResource(Res.string.dialog_title_clear_cache)) },
                text = { Text(stringResource(Res.string.dialog_clear_cache_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearCacheDialog = false
                            viewModel.clearCache()
                        },
                    ) {
                        Text(stringResource(Res.string.action_clear))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearCacheDialog = false }) {
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
                    TextButton(
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
                    TextButton(onClick = { showRevokeKeysDialog = false }) {
                        Text(stringResource(Res.string.action_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun DataExtraActions(
    onSharedLinksClick: () -> Unit,
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
            // Shared Links
            AdaptiveOutlinedButton(
                onClick = onSharedLinksClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(Res.string.shared_links))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Home-screen artifact shortcuts
            AdaptiveOutlinedButton(
                onClick = onArtifactShortcutsClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(Res.string.artifact_shortcuts_title))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
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
