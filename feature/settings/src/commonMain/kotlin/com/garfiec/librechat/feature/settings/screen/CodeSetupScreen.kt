package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveCard
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveRadioButton
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.datetime.rememberAbsoluteTimestamp
import com.garfiec.librechat.core.ui.theme.statusColors
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.CodeSetupDetails
import com.garfiec.librechat.feature.settings.viewmodel.CodeSetupStatusText
import com.garfiec.librechat.feature.settings.viewmodel.CodeSetupUiState
import com.garfiec.librechat.feature.settings.viewmodel.CodeSetupViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Approves or denies a machine's setup request (web's `/code/setup/:attemptId`): what the machine
 * asks for, how to check it is the one the user started, and Approve / Deny.
 */
@Composable
fun CodeSetupScreen(
    attemptId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CodeSetupViewModel = koinViewModel { parametersOf(attemptId) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AdaptiveScaffold(
        modifier = modifier,
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), onNavigateBack),
                    title = BarTitle(stringResource(Res.string.code_setup_title)),
                    actions = emptyList(),
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            uiState.account?.let { Hint(stringResource(Res.string.code_setup_account, it)) }
            SetupBody(
                state = uiState,
                onRetry = viewModel::load,
                onSelectPlane = viewModel::selectPlane,
                onApprove = viewModel::approve,
                onDeny = viewModel::deny,
            )
        }
    }
}

@Composable
private fun SetupBody(
    state: CodeSetupUiState,
    onRetry: () -> Unit,
    onSelectPlane: (String) -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    when {
        state.unavailable -> Hint(stringResource(Res.string.code_setup_unavailable))
        state.loading -> {
            AdaptiveCircularProgressIndicator(modifier = Modifier.size(24.dp))
            Hint(stringResource(Res.string.code_setup_loading))
        }
        state.loadFailed -> {
            Hint(stringResource(Res.string.code_setup_load_failed))
            AdaptiveTextButton(onClick = onRetry) { Text(stringResource(Res.string.code_environment_retry)) }
        }
        else -> {
            state.status?.let { StatusCard(it) }
            state.details?.let { Details(it) }
            Warnings()
            if (state.decisionFailed) {
                Text(
                    stringResource(Res.string.code_setup_action_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (state.decisionOpen) Decision(state, onSelectPlane, onApprove, onDeny)
        }
    }
}

@Composable
private fun StatusCard(status: CodeSetupStatusText) {
    val color = if (status == CodeSetupStatusText.COMPLETED) statusColors().success else MaterialTheme.colorScheme.onSurface
    AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(statusText(status)),
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = Modifier
                .padding(16.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

private fun statusText(status: CodeSetupStatusText): StringResource = when (status) {
    CodeSetupStatusText.PENDING -> Res.string.code_setup_pending
    CodeSetupStatusText.PROVISIONING -> Res.string.code_setup_provisioning
    CodeSetupStatusText.APPROVED -> Res.string.code_setup_approved
    CodeSetupStatusText.COMPLETED -> Res.string.code_setup_completed
    CodeSetupStatusText.DENIED -> Res.string.code_setup_denied
    CodeSetupStatusText.CANCELLED -> Res.string.code_setup_cancelled
    CodeSetupStatusText.EXPIRED -> Res.string.code_setup_expired
    CodeSetupStatusText.FAILED -> Res.string.code_setup_failed
    CodeSetupStatusText.RETRYABLE -> Res.string.code_setup_retryable
}

@Composable
private fun Details(details: CodeSetupDetails) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Detail(stringResource(Res.string.code_setup_machine), details.machineName)
        Detail(stringResource(Res.string.code_setup_code), details.verificationCode, monospace = true, large = true)
        Detail(stringResource(Res.string.code_setup_fingerprint), details.keyFingerprint, monospace = true)
        Detail(
            stringResource(Res.string.code_setup_workspace),
            stringResource(if (details.directoryWorkspace) Res.string.code_setup_directory else Res.string.code_setup_private),
        )
        details.expiresIso?.let { Detail(stringResource(Res.string.code_setup_expires), rememberAbsoluteTimestamp(it)) }
        Detail(stringResource(Res.string.code_setup_writes), enabledText(details.allowWrites))
        Detail(stringResource(Res.string.code_setup_commands), enabledText(details.allowCommands))
        when {
            details.planeMissing -> Detail(
                stringResource(Res.string.code_setup_plane),
                stringResource(Res.string.code_setup_plane_unavailable),
            )
            details.planeName != null -> Detail(stringResource(Res.string.code_setup_plane), details.planeName)
        }
    }
}

@Composable
private fun enabledText(enabled: Boolean) =
    stringResource(if (enabled) Res.string.code_setup_enabled else Res.string.code_setup_disabled)

@Composable
private fun Detail(label: String, value: String, monospace: Boolean = false, large: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer {
            Text(
                text = value,
                style = (if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium).copy(
                    fontFamily = if (monospace) FontFamily.Monospace else null,
                    fontWeight = if (large) FontWeight.SemiBold else null,
                ),
            )
        }
    }
}

@Composable
private fun Warnings() {
    AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Res.string.code_setup_verify,
                Res.string.code_setup_command_warning,
                Res.string.code_setup_trust_warning,
            ).forEach { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun Decision(state: CodeSetupUiState, onSelectPlane: (String) -> Unit, onApprove: () -> Unit, onDeny: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.planes.isEmpty()) {
            Hint(stringResource(Res.string.code_setup_no_planes))
        } else {
            Text(stringResource(Res.string.code_setup_plane), style = MaterialTheme.typography.titleSmall)
            state.planes.forEach { plane ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = plane.id == state.selectedPlaneId,
                            enabled = !state.deciding,
                            role = Role.RadioButton,
                            onClick = { onSelectPlane(plane.id) },
                        )
                        .padding(vertical = 4.dp),
                ) {
                    AdaptiveRadioButton(selected = plane.id == state.selectedPlaneId, onClick = null)
                    Text(plane.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (state.deciding) Hint(stringResource(Res.string.code_setup_saving))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AdaptiveButton(onClick = onApprove, enabled = state.canApprove) {
                Text(stringResource(Res.string.code_setup_approve))
            }
            AdaptiveOutlinedButton(onClick = onDeny, enabled = !state.deciding) {
                Text(stringResource(Res.string.code_setup_deny))
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
