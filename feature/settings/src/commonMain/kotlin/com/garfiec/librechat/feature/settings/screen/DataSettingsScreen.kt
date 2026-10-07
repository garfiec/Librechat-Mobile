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
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import com.garfiec.librechat.feature.settings.viewmodel.DataSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.PrefetchActivityViewModel
import org.jetbrains.compose.resources.pluralStringResource
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
    onNavigateToMemories: () -> Unit,
    onNavigateToMcpServers: () -> Unit,
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
            onNavigateToMemories = onNavigateToMemories,
            onNavigateToMcpServers = onNavigateToMcpServers,
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
    onNavigateToMemories: () -> Unit,
    onNavigateToMcpServers: () -> Unit,
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

    // Fires on first show and on every return from the Memories / MCP pages.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshSummaries()
        onPauseOrDispose { }
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

            // Memories and MCP servers each live on their own page; the tab shows a summary row.
            // A row is hidden when the role denies its USE permission.
            if (uiState.serverMemoriesEnabled) {
                item(key = "memories_header") {
                    AdaptiveSectionHeader(stringResource(Res.string.section_memories))
                }
                adaptiveSection {
                    row(key = "memories_row") {
                        SelectorRow(
                            title = stringResource(Res.string.section_memories),
                            value = memoriesSummary(uiState.memoriesEnabled, uiState.memoryCount),
                            onClick = onNavigateToMemories,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            if (uiState.mcpServersEnabled) {
                item(key = "mcp_header") {
                    AdaptiveSectionHeader(stringResource(Res.string.section_mcp_servers))
                }
                adaptiveSection {
                    row(key = "mcp_row") {
                        SelectorRow(
                            title = stringResource(Res.string.section_mcp_servers),
                            value = mcpSummary(uiState.mcpServerCount),
                            onClick = onNavigateToMcpServers,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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

@Composable
private fun memoriesSummary(enabled: Boolean, count: Int?): String = when {
    !enabled -> stringResource(Res.string.data_summary_off)
    count == null -> ""
    count == 0 -> stringResource(Res.string.data_summary_none)
    else -> pluralStringResource(Res.plurals.data_memories_count, count, count)
}

@Composable
private fun mcpSummary(count: Int?): String = when (count) {
    null -> ""
    0 -> stringResource(Res.string.data_summary_none)
    else -> pluralStringResource(Res.plurals.data_mcp_servers_count, count, count)
}
