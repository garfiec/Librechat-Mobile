package com.garfiec.librechat.feature.agents.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.code.StatefulCodeScope
import com.garfiec.librechat.core.ui.components.AdaptiveCheckbox
import com.garfiec.librechat.core.ui.components.AdaptiveExposedDropdownMenu
import com.garfiec.librechat.core.ui.components.AdaptiveExposedDropdownMenuBox
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveSwitch
import com.garfiec.librechat.core.ui.components.VerbatimKeyboardOptions
import com.garfiec.librechat.feature.agents.resources.Res
import com.garfiec.librechat.feature.agents.resources.agent_git_email
import com.garfiec.librechat.feature.agents.resources.agent_git_identity
import com.garfiec.librechat.feature.agents.resources.agent_git_identity_both_required
import com.garfiec.librechat.feature.agents.resources.agent_git_identity_info
import com.garfiec.librechat.feature.agents.resources.agent_git_name
import com.garfiec.librechat.feature.agents.resources.code_background
import com.garfiec.librechat.feature.agents.resources.code_background_info
import com.garfiec.librechat.feature.agents.resources.code_environment
import com.garfiec.librechat.feature.agents.resources.code_environment_attached_name
import com.garfiec.librechat.feature.agents.resources.code_environment_choice_limit
import com.garfiec.librechat.feature.agents.resources.code_environment_default_machine
import com.garfiec.librechat.feature.agents.resources.code_environment_deployment_default
import com.garfiec.librechat.feature.agents.resources.code_environment_info
import com.garfiec.librechat.feature.agents.resources.code_environment_per_chat
import com.garfiec.librechat.feature.agents.resources.code_environment_per_chat_info
import com.garfiec.librechat.feature.agents.resources.code_environment_unavailable_choice
import com.garfiec.librechat.feature.agents.resources.code_stateful_sessions
import com.garfiec.librechat.feature.agents.resources.code_stateful_sessions_info
import com.garfiec.librechat.feature.agents.resources.code_workspace_default
import com.garfiec.librechat.feature.agents.resources.code_workspace_default_info
import com.garfiec.librechat.feature.agents.resources.code_workspace_last_used
import com.garfiec.librechat.feature.agents.resources.code_workspace_saved_loading
import com.garfiec.librechat.feature.agents.resources.code_workspace_saved_missing
import com.garfiec.librechat.feature.agents.resources.code_workspace_saved_unavailable
import com.garfiec.librechat.feature.agents.resources.code_workspace_saved_unsupported
import com.garfiec.librechat.feature.agents.resources.repository_instructions
import com.garfiec.librechat.feature.agents.resources.repository_instructions_defer
import com.garfiec.librechat.feature.agents.resources.repository_instructions_info
import com.garfiec.librechat.feature.agents.resources.repository_instructions_off
import com.garfiec.librechat.feature.agents.resources.repository_instructions_prefer
import com.garfiec.librechat.feature.agents.resources.stateful_code_environment
import com.garfiec.librechat.feature.agents.resources.stateful_code_environment_agent_user
import com.garfiec.librechat.feature.agents.resources.stateful_code_environment_conversation
import com.garfiec.librechat.feature.agents.resources.stateful_code_environment_info
import com.garfiec.librechat.feature.agents.resources.stateful_code_environment_user
import com.garfiec.librechat.feature.agents.viewmodel.AgentCodeAttachedView
import com.garfiec.librechat.feature.agents.viewmodel.AgentCodeSectionView
import com.garfiec.librechat.feature.agents.viewmodel.AgentCodeWorkspaceState
import com.garfiec.librechat.feature.agents.viewmodel.RepositoryInstructions
import com.garfiec.librechat.feature.agents.viewmodel.delegate.AgentCodeEvent
import org.jetbrains.compose.resources.stringResource

private const val DEPLOYMENT_DEFAULT = "__deployment_default__"

/** One option of a [CodeDropdownField]. */
private class CodeFieldOption(val key: String, val label: String, val enabled: Boolean = true)

/**
 * Web's Code settings (`SidePanel/Agents/Code/Settings.tsx`), under the code interpreter switch:
 * stateful sessions, the machine and the machines new chats may pick, the workspace scope and,
 * for an attached machine, the default workspace, repository instructions and Git identity.
 * Draws [view] and nothing more; every rule is in `agentCodeSection`.
 */
@Composable
fun AgentCodeSettingsSection(
    view: AgentCodeSectionView,
    onEvent: (AgentCodeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        view.unavailableMachines.forEach { id ->
            CheckRow(
                label = stringResource(Res.string.code_environment_unavailable_choice, id),
                checked = true,
                enabled = true,
                onToggle = { onEvent(AgentCodeEvent.RemoveUnavailableMachine(id)) },
            )
        }
        SwitchRow(
            title = stringResource(Res.string.code_stateful_sessions),
            info = stringResource(Res.string.code_stateful_sessions_info),
            checked = view.statefulSessions,
            onToggle = { onEvent(AgentCodeEvent.StatefulSessions(it)) },
        )
        if (!view.statefulSessions) return@Column
        view.machines?.let { machines ->
            val options = listOf(CodeFieldOption(DEPLOYMENT_DEFAULT, stringResource(Res.string.code_environment_deployment_default))) +
                machines.map { machine ->
                    val label = if (machine.attached) {
                        stringResource(Res.string.code_environment_attached_name, machine.name)
                    } else {
                        machine.name
                    }
                    CodeFieldOption(machine.id, label)
                }
            CodeDropdownField(
                label = stringResource(Res.string.code_environment),
                selectedKey = view.machineId ?: DEPLOYMENT_DEFAULT,
                options = options,
                onSelect = { key -> onEvent(AgentCodeEvent.Machine(key.takeUnless { it == DEPLOYMENT_DEFAULT })) },
                help = stringResource(Res.string.code_environment_info),
            )
        }
        view.machineChoices?.let { choices ->
            Column {
                Text(stringResource(Res.string.code_environment_per_chat), style = MaterialTheme.typography.titleSmall)
                HelpText(stringResource(Res.string.code_environment_per_chat_info))
                HelpText(stringResource(Res.string.code_environment_choice_limit, view.machineChoiceLimit))
                choices.forEach { choice ->
                    CheckRow(
                        label = if (choice.isDefault) {
                            stringResource(Res.string.code_environment_default_machine, choice.name)
                        } else {
                            choice.name
                        },
                        checked = choice.checked,
                        enabled = choice.enabled,
                        onToggle = { onEvent(AgentCodeEvent.MachineChoice(choice.id, !choice.checked)) },
                    )
                }
            }
        }
        if (view.scopes.isNotEmpty()) {
            val scopeOptions = view.scopes.map { CodeFieldOption(it, scopeLabel(it)) } +
                listOfNotNull(view.scope?.takeIf { view.scopeDisallowed }?.let { CodeFieldOption(it, scopeLabel(it), enabled = false) })
            CodeDropdownField(
                label = stringResource(Res.string.stateful_code_environment),
                selectedKey = view.scope.orEmpty(),
                options = scopeOptions,
                onSelect = { onEvent(AgentCodeEvent.Scope(it)) },
                help = stringResource(Res.string.stateful_code_environment_info),
            )
        }
        view.attached?.let { AttachedMachineFields(it, onEvent) }
    }
}

@Composable
private fun AttachedMachineFields(
    attached: AgentCodeAttachedView,
    onEvent: (AgentCodeEvent) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    val savedState = attached.workspaceState?.let { workspaceStateLabel(it) }
    val workspaceOptions = listOf(CodeFieldOption("", stringResource(Res.string.code_workspace_last_used))) +
        attached.workspaces.map { CodeFieldOption(it.id, it.name) } +
        listOfNotNull(savedState?.let { CodeFieldOption(attached.workspaceId, "${attached.workspaceId} · $it", enabled = false) })
    CodeDropdownField(
        label = stringResource(Res.string.code_workspace_default),
        selectedKey = attached.workspaceId,
        options = workspaceOptions,
        onSelect = { onEvent(AgentCodeEvent.Workspace(it)) },
        help = listOfNotNull(
            attached.discovery?.let { workspaceStateLabel(it) },
            stringResource(Res.string.code_workspace_default_info),
        ).joinToString("\n"),
    )
    CodeDropdownField(
        label = stringResource(Res.string.repository_instructions),
        selectedKey = attached.repositoryInstructions,
        options = RepositoryInstructions.ALL.map { CodeFieldOption(it, repositoryInstructionsLabel(it)) },
        onSelect = { onEvent(AgentCodeEvent.RepositoryInstructions(it)) },
        help = stringResource(Res.string.repository_instructions_info),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.agent_git_identity), style = MaterialTheme.typography.titleSmall)
        AdaptiveOutlinedTextField(
            value = attached.gitName,
            onValueChange = { onEvent(AgentCodeEvent.GitName(it.take(GIT_NAME_MAX))) },
            label = { Text(stringResource(Res.string.agent_git_name)) },
            singleLine = true,
            isError = attached.gitIdentityError,
            modifier = Modifier.fillMaxWidth(),
        )
        AdaptiveOutlinedTextField(
            value = attached.gitEmail,
            onValueChange = { onEvent(AgentCodeEvent.GitEmail(it.take(GIT_EMAIL_MAX))) },
            label = { Text(stringResource(Res.string.agent_git_email)) },
            singleLine = true,
            isError = attached.gitIdentityError,
            keyboardOptions = VerbatimKeyboardOptions.copy(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        if (attached.gitIdentityError) {
            Text(
                stringResource(Res.string.agent_git_identity_both_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        HelpText(stringResource(Res.string.agent_git_identity_info))
    }
}

private const val GIT_NAME_MAX = 128
private const val GIT_EMAIL_MAX = 254

@Composable
private fun scopeLabel(scope: String): String = when (scope) {
    StatefulCodeScope.USER -> stringResource(Res.string.stateful_code_environment_user)
    StatefulCodeScope.AGENT_USER -> stringResource(Res.string.stateful_code_environment_agent_user)
    StatefulCodeScope.CONVERSATION -> stringResource(Res.string.stateful_code_environment_conversation)
    else -> scope
}

@Composable
private fun repositoryInstructionsLabel(value: String): String = when (value) {
    RepositoryInstructions.DEFER -> stringResource(Res.string.repository_instructions_defer)
    RepositoryInstructions.OFF -> stringResource(Res.string.repository_instructions_off)
    else -> stringResource(Res.string.repository_instructions_prefer)
}

@Composable
private fun workspaceStateLabel(state: AgentCodeWorkspaceState): String = stringResource(
    when (state) {
        AgentCodeWorkspaceState.LOADING -> Res.string.code_workspace_saved_loading
        AgentCodeWorkspaceState.MISSING -> Res.string.code_workspace_saved_missing
        AgentCodeWorkspaceState.UNAVAILABLE -> Res.string.code_workspace_saved_unavailable
        AgentCodeWorkspaceState.UNSUPPORTED -> Res.string.code_workspace_saved_unsupported
    },
)

@Composable
private fun HelpText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

/** Web's `Code/Background.tsx`: one switch for the code tools' background execution. */
@Composable
fun AgentCodeBackgroundRow(checked: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth()) {
        SwitchRow(
            title = stringResource(Res.string.code_background),
            info = stringResource(Res.string.code_background_info),
            checked = checked,
            onToggle = onToggle,
        )
    }
}

@Composable
private fun SwitchRow(title: String, info: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            HelpText(info)
        }
        AdaptiveSwitch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AdaptiveCheckbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodeDropdownField(
    label: String,
    selectedKey: String,
    options: List<CodeFieldOption>,
    onSelect: (String) -> Unit,
    help: String,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        AdaptiveExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            AdaptiveOutlinedTextField(
                value = options.firstOrNull { it.key == selectedKey }?.label ?: selectedKey,
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            AdaptiveExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        enabled = option.enabled,
                        onClick = {
                            expanded = false
                            onSelect(option.key)
                        },
                    )
                }
            }
        }
        HelpText(help)
    }
}
