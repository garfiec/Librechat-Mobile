package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.data.repository.CodePairingError
import com.garfiec.librechat.core.data.repository.CodeRemovalError
import com.garfiec.librechat.core.model.code.CodePermissionDecision
import com.garfiec.librechat.core.model.code.CodeWorkerShell
import com.garfiec.librechat.core.model.code.StatefulCodeScope
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveGroupedPage
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader
import com.garfiec.librechat.core.ui.components.AdaptiveSegmentedChoice
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.components.UrlKeyboardOptions
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.theme.statusColors
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.util.rememberClipboardWriter
import com.garfiec.librechat.feature.settings.viewmodel.CodeExecutionEvent
import com.garfiec.librechat.feature.settings.viewmodel.CodeExecutionUiState
import com.garfiec.librechat.feature.settings.viewmodel.CodeExecutionViewModel
import com.garfiec.librechat.feature.settings.viewmodel.CodeMachineRow
import com.garfiec.librechat.feature.settings.viewmodel.CodeMachineStatusLabel
import com.garfiec.librechat.feature.settings.viewmodel.CodePairingView
import com.garfiec.librechat.feature.settings.viewmodel.CodePermissionCategory
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Settings › Data › Code execution (web's Data controls › Code execution): the default stateful
 * workspace for new agents, the user's machines, pairing a new one, and opening a worker's setup
 * link for approval.
 */
@Composable
fun CodeExecutionScreen(
    onNavigateBack: () -> Unit,
    onOpenSetup: (attemptId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CodeExecutionViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Machine status is polled only while the page is in front, as web stops in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.startPolling()
                Lifecycle.Event.ON_PAUSE -> viewModel.stopPolling()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopPolling()
        }
    }

    val openSetup by rememberUpdatedState(onOpenSetup)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is CodeExecutionEvent.OpenSetup) {
                openSetup(event.attemptId)
                return@collect
            }
            snackbarHostState.showSnackbar(getString(eventMessage(event)))
        }
    }

    AdaptiveScaffold(
        modifier = modifier,
        snackbarHost = { AdaptiveSnackbarHost(snackbarHostState) },
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), onNavigateBack),
                    title = BarTitle(stringResource(Res.string.code_execution_title)),
                    actions = emptyList(),
                ),
            )
        },
    ) { innerPadding ->
        AdaptiveGroupedPage(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                defaultScopeSection(uiState, viewModel)
                machinesSection(uiState, viewModel)
                pairingSection(uiState, viewModel)
                setupSection(uiState, viewModel)
                item(key = "bottom") { Spacer(Modifier.height(32.dp)) }
            }
        }
    }

    if (uiState.pendingRemoval != null) {
        AdaptiveAlertDialog(
            onDismissRequest = viewModel::dismissRemoval,
            title = { Text(stringResource(Res.string.code_environment_remove_title)) },
            text = { Text(stringResource(Res.string.code_environment_remove_description)) },
            confirmButton = {
                AdaptiveTextButton(onClick = viewModel::confirmRemoval) {
                    Text(stringResource(Res.string.code_environment_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                AdaptiveTextButton(onClick = viewModel::dismissRemoval) { Text(stringResource(Res.string.code_environment_cancel)) }
            },
        )
    }
}

private fun eventMessage(event: CodeExecutionEvent): StringResource = when (event) {
    CodeExecutionEvent.PairingCreated -> Res.string.code_environment_pairing_created
    is CodeExecutionEvent.PairingFailed -> when (event.error) {
        CodePairingError.INVALID_NAME -> Res.string.code_environment_pairing_error_name
        CodePairingError.DISABLED -> Res.string.code_environment_pairing_error_disabled
        CodePairingError.CONTROL_PLANE_GONE -> Res.string.code_environment_pairing_error_plane
        CodePairingError.LIMIT_REACHED -> Res.string.code_environment_pairing_error_limit
        CodePairingError.RATE_LIMITED -> Res.string.code_environment_pairing_error_rate
        CodePairingError.REFUSED, CodePairingError.UNAVAILABLE -> Res.string.code_environment_pairing_error
    }
    CodeExecutionEvent.PermissionsSaved -> Res.string.code_environment_permissions_saved
    CodeExecutionEvent.PermissionsFailed -> Res.string.code_environment_permissions_error
    CodeExecutionEvent.Removed -> Res.string.code_environment_removed
    is CodeExecutionEvent.RemoveFailed -> when (event.error) {
        CodeRemovalError.IN_USE -> Res.string.code_environment_remove_in_use
        CodeRemovalError.GONE -> Res.string.code_environment_removed
        CodeRemovalError.FAILED -> Res.string.code_environment_remove_error
    }
    CodeExecutionEvent.PreferencesUpdated -> Res.string.code_preferences_updated
    CodeExecutionEvent.PreferencesFailed -> Res.string.code_preferences_error
    is CodeExecutionEvent.OpenSetup -> Res.string.code_setup_title
}

private fun LazyListScope.defaultScopeSection(state: CodeExecutionUiState, viewModel: CodeExecutionViewModel) {
    if (state.scopeLoadFailed) {
        item(key = "scope_header") { AdaptiveSectionHeader(stringResource(Res.string.default_stateful_workspace)) }
        item(key = "scope_error") {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    stringResource(Res.string.memories_use_load_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                AdaptiveTextButton(onClick = viewModel::loadScope) { Text(stringResource(Res.string.code_environment_retry)) }
            }
        }
        return
    }
    val scope = state.scope ?: return
    item(key = "scope_header") { AdaptiveSectionHeader(stringResource(Res.string.default_stateful_workspace)) }
    adaptiveSection {
        row(key = "scope_row") {
            SettingsChoiceRow(
                title = stringResource(Res.string.default_stateful_workspace),
                value = scopeLabel(scope.selected),
                options = scope.options.map { it to scopeLabel(it) },
                disabledKeys = setOfNotNull(scope.disallowed),
                enabled = !scope.saving,
                onSelect = viewModel::selectScope,
            )
        }
    }
    item(key = "scope_info") { InfoText(stringResource(Res.string.default_stateful_workspace_description)) }
}

private fun LazyListScope.machinesSection(state: CodeExecutionUiState, viewModel: CodeExecutionViewModel) {
    item(key = "machines_header") { AdaptiveSectionHeader(stringResource(Res.string.code_environments)) }
    item(key = "machines_info") { InfoText(stringResource(Res.string.code_environments_description)) }
    if (state.machines.isEmpty()) return
    adaptiveSection {
        state.machines.forEach { machine ->
            row(key = "machine_${machine.id}") {
                MachineRow(
                    machine = machine,
                    removing = state.removingId == machine.id,
                    saving = state.savingPermissionsId == machine.id,
                    onRemove = { viewModel.requestRemoval(machine.id) },
                    onPermission = { category, decision -> viewModel.setPermission(machine.id, category, decision) },
                )
            }
        }
    }
}

private fun LazyListScope.pairingSection(state: CodeExecutionUiState, viewModel: CodeExecutionViewModel) {
    item(key = "pair_header") { AdaptiveSectionHeader(stringResource(Res.string.code_environment_pair)) }
    item(key = "pair_body") {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                state.loading -> AdaptiveCircularProgressIndicator(modifier = Modifier.size(24.dp))
                state.loadFailed -> {
                    Text(
                        stringResource(Res.string.code_environment_load_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    AdaptiveTextButton(onClick = viewModel::load) { Text(stringResource(Res.string.code_environment_retry)) }
                }
                state.controlPlanes.isEmpty() -> InfoText(stringResource(Res.string.code_environment_unavailable), padded = false)
                else -> PairingForm(
                    state = state,
                    onName = viewModel::setPairName,
                    onPlane = viewModel::selectControlPlane,
                    onPair = viewModel::pair,
                )
            }
            state.pairingCommand?.let { PairingCommand(it, viewModel::selectShell) }
        }
    }
}

@Composable
private fun PairingForm(
    state: CodeExecutionUiState,
    onName: (String) -> Unit,
    onPlane: (String) -> Unit,
    onPair: () -> Unit,
) {
    AdaptiveOutlinedTextField(
        value = state.pairName,
        onValueChange = onName,
        label = { Text(stringResource(Res.string.code_environment_name)) },
        placeholder = { Text(stringResource(Res.string.code_environment_name_placeholder)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.controlPlanes.size > 1) {
        SettingsChoiceRow(
            title = stringResource(Res.string.code_environment_control_plane),
            value = state.controlPlanes.firstOrNull { it.id == state.pairPlaneId }?.name.orEmpty(),
            options = state.controlPlanes.map { it.id to it.name },
            disabledKeys = emptySet(),
            enabled = !state.pairing,
            onSelect = onPlane,
        )
    }
    AdaptiveButton(onClick = onPair, enabled = state.canPair) {
        if (state.pairing) {
            AdaptiveCircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Text(stringResource(Res.string.code_environment_pair))
        }
    }
}

@Composable
private fun PairingCommand(pairing: CodePairingView, onShell: (CodeWorkerShell) -> Unit) {
    val clipboard = rememberClipboardWriter()
    var copied by remember(pairing.command) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.code_environment_pairing_ready), style = MaterialTheme.typography.titleSmall)
        InfoText(stringResource(Res.string.code_environment_pairing_description), padded = false)
        val shells = listOf(CodeWorkerShell.POSIX, CodeWorkerShell.POWERSHELL)
        AdaptiveSegmentedChoice(
            options = listOf(
                stringResource(Res.string.code_environment_shell_posix),
                stringResource(Res.string.code_environment_shell_powershell),
            ),
            selectedIndex = shells.indexOf(pairing.shell),
            onSelect = { onShell(shells[it]) },
            modifier = Modifier.fillMaxWidth(),
        )
        SelectionContainer {
            Text(
                text = pairing.command,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val label = stringResource(Res.string.code_environment_pairing_ready)
        AdaptiveTextButton(onClick = {
            clipboard.copy(pairing.command, label)
            copied = true
        }) {
            Text(stringResource(if (copied) Res.string.copied_to_clipboard else Res.string.code_environment_copy_command))
        }
    }
}

private fun LazyListScope.setupSection(state: CodeExecutionUiState, viewModel: CodeExecutionViewModel) {
    item(key = "setup_header") { AdaptiveSectionHeader(stringResource(Res.string.code_setup_title)) }
    item(key = "setup_body") {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InfoText(stringResource(Res.string.code_setup_link_info), padded = false)
            AdaptiveOutlinedTextField(
                value = state.setupLink,
                onValueChange = viewModel::setSetupLink,
                label = { Text(stringResource(Res.string.code_setup_link)) },
                singleLine = true,
                keyboardOptions = UrlKeyboardOptions,
                isError = state.setupLinkInvalid,
                supportingText = if (state.setupLinkInvalid) {
                    { Text(stringResource(Res.string.code_setup_link_invalid)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
            AdaptiveTextButton(onClick = viewModel::openSetupLink, enabled = state.setupLink.isNotBlank()) {
                Text(stringResource(Res.string.code_setup_open))
            }
        }
    }
}

@Composable
private fun MachineRow(
    machine: CodeMachineRow,
    removing: Boolean,
    saving: Boolean,
    onRemove: () -> Unit,
    onPermission: (CodePermissionCategory, String) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(machine.name, style = MaterialTheme.typography.bodyLarge)
                Text(machine.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatusLine(machine)
            }
            if (machine.canDelete) {
                if (removing) {
                    AdaptiveCircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    AdaptiveTextButton(onClick = onRemove) {
                        Text(stringResource(Res.string.code_environment_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        machine.permissions.forEach { control ->
            SettingsChoiceRow(
                title = stringResource(
                    if (control.category == CodePermissionCategory.FILE_WRITE) {
                        Res.string.code_environment_file_write
                    } else {
                        Res.string.code_environment_command_execution
                    },
                ),
                value = permissionLabel(control.selected),
                options = control.options.map { it to permissionLabel(it) },
                disabledKeys = emptySet(),
                enabled = !saving,
                onSelect = { onPermission(control.category, it) },
            )
        }
    }
}

@Composable
private fun StatusLine(machine: CodeMachineRow) {
    val colors = statusColors()
    val (text, color) = when (machine.status) {
        CodeMachineStatusLabel.CHECKING -> Res.string.code_environment_status_checking to MaterialTheme.colorScheme.onSurfaceVariant
        CodeMachineStatusLabel.UNAVAILABLE -> Res.string.code_environment_status_unavailable to colors.warning
        CodeMachineStatusLabel.OFFLINE -> Res.string.code_environment_status_offline to MaterialTheme.colorScheme.error
        CodeMachineStatusLabel.STARTING -> Res.string.code_environment_status_starting to colors.warning
        CodeMachineStatusLabel.READY -> Res.string.code_environment_status_ready to colors.success
    }
    val detail = machine.detail
    Text(
        text = listOfNotNull(stringResource(text), detail).joinToString(" · "),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun scopeLabel(scope: String): String = when (scope) {
    StatefulCodeScope.USER -> stringResource(Res.string.stateful_code_environment_user)
    StatefulCodeScope.AGENT_USER -> stringResource(Res.string.stateful_code_environment_agent_user)
    StatefulCodeScope.CONVERSATION -> stringResource(Res.string.stateful_code_environment_conversation)
    else -> scope
}

@Composable
private fun permissionLabel(decision: String): String = when (decision) {
    CodePermissionDecision.ALLOW -> stringResource(Res.string.code_environment_permission_allow)
    CodePermissionDecision.DENY -> stringResource(Res.string.code_environment_permission_deny)
    CodePermissionDecision.ASK -> stringResource(Res.string.code_environment_permission_ask)
    else -> decision
}

@Composable
private fun InfoText(text: String, padded: Boolean = true) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = if (padded) Modifier.padding(horizontal = 16.dp, vertical = 8.dp) else Modifier,
    )
}
