package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CopyAll
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveAssistChip
import com.garfiec.librechat.core.ui.components.AdaptiveChipDefaults
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.AdaptiveRadioButton
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader
import com.garfiec.librechat.core.ui.components.AdaptiveSegmentedChoice
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_code_chip
import com.garfiec.librechat.feature.chat.resources.code_branch
import com.garfiec.librechat.feature.chat.resources.code_branch_info
import com.garfiec.librechat.feature.chat.resources.code_checkout_auto
import com.garfiec.librechat.feature.chat.resources.code_checkout_auto_chip
import com.garfiec.librechat.feature.chat.resources.code_checkout_auto_info
import com.garfiec.librechat.feature.chat.resources.code_checkout_mode
import com.garfiec.librechat.feature.chat.resources.code_checkout_registered
import com.garfiec.librechat.feature.chat.resources.code_checkout_registered_chip
import com.garfiec.librechat.feature.chat.resources.code_checkout_registered_info
import com.garfiec.librechat.feature.chat.resources.code_checkout_worktree
import com.garfiec.librechat.feature.chat.resources.code_checkout_worktree_info
import com.garfiec.librechat.feature.chat.resources.code_machine
import com.garfiec.librechat.feature.chat.resources.code_machine_choose
import com.garfiec.librechat.feature.chat.resources.code_workspace
import com.garfiec.librechat.feature.chat.resources.code_workspace_agent_status
import com.garfiec.librechat.feature.chat.resources.code_workspace_choose
import com.garfiec.librechat.feature.chat.resources.code_workspace_graph_requirement
import com.garfiec.librechat.feature.chat.resources.code_workspace_loading
import com.garfiec.librechat.feature.chat.resources.code_workspace_locked_recovery
import com.garfiec.librechat.feature.chat.resources.code_workspace_missing
import com.garfiec.librechat.feature.chat.resources.code_workspace_refresh
import com.garfiec.librechat.feature.chat.resources.code_workspace_required_for
import com.garfiec.librechat.feature.chat.resources.code_workspace_unavailable
import com.garfiec.librechat.feature.chat.resources.code_workspace_unsupported
import com.garfiec.librechat.feature.chat.resources.code_workspace_used_by
import com.garfiec.librechat.feature.chat.resources.code_workspace_without
import com.garfiec.librechat.feature.chat.resources.code_workspace_without_info
import com.garfiec.librechat.feature.chat.viewmodel.CodeCheckoutMode
import com.garfiec.librechat.feature.chat.viewmodel.CodeFootnote
import com.garfiec.librechat.feature.chat.viewmodel.CodeMachineSection
import com.garfiec.librechat.feature.chat.viewmodel.CodeMachineStatus
import com.garfiec.librechat.feature.chat.viewmodel.CodeRequirement
import com.garfiec.librechat.feature.chat.viewmodel.CodeRequirementState
import com.garfiec.librechat.feature.chat.viewmodel.CodeWorkspaceChipLabel
import com.garfiec.librechat.feature.chat.viewmodel.CodeWorkspaceRowView
import com.garfiec.librechat.feature.chat.viewmodel.CodeWorkspaceUiState
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** What the composer's code-workspace row draws and does. */
@Immutable
class CodeWorkspaceComposer(
    val state: CodeWorkspaceUiState,
    val onOpenSheet: (machineId: String?) -> Unit,
    val onDismissSheet: () -> Unit,
    val onSelectWorkspace: (environmentId: String, workspaceId: String) -> Unit,
    val onSetCheckout: (environmentId: String, mode: CodeCheckoutMode) -> Unit,
    val onSelectNoWorkspace: () -> Unit,
    val onRefresh: () -> Unit,
)

/**
 * Provided by the chat screen around its composer, so both platform composers draw the row without
 * threading it through their signatures. Null draws nothing.
 */
val LocalCodeWorkspaceComposer = compositionLocalOf<CodeWorkspaceComposer?> { null }

private const val CHIP_TEXT_MAX = 16

private fun String.clipped(): String = if (length > CHIP_TEXT_MAX) take(CHIP_TEXT_MAX - 1) + "…" else this

/**
 * The attached-machine controls above the pinned tools: machine, workspace, branch and checkout
 * chips, and the lines that say what a held send is waiting on. Draws [CodeWorkspaceComposer.state]
 * and nothing more; every decision is the delegate's.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CodeWorkspaceRow(composer: CodeWorkspaceComposer, modifier: Modifier = Modifier) {
    val state = composer.state
    if (!state.visible) return
    Column(modifier = modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.machine?.let { machine -> MachineChip(machine.name, machine.choices, composer) }
            WorkspaceChip(state, composer)
            state.branch?.let { BranchChip(it) }
            state.checkout?.let { checkout ->
                val label = stringResource(
                    when (checkout.mode) {
                        CodeCheckoutMode.AUTO -> Res.string.code_checkout_auto_chip
                        CodeCheckoutMode.WORKTREE -> Res.string.code_checkout_worktree
                        CodeCheckoutMode.REGISTERED -> Res.string.code_checkout_registered_chip
                    },
                )
                val description =
                    stringResource(Res.string.cd_code_chip, stringResource(Res.string.code_checkout_mode), label)
                CodeChip(
                    icon = checkoutIcon(checkout.mode),
                    label = label,
                    description = description,
                    caret = checkout.pickable,
                    onClick = if (checkout.pickable) ({ composer.onOpenSheet(null) }) else null,
                )
            }
        }
        val lines = buildList {
            if (state.graphRequirement) add(stringResource(Res.string.code_workspace_graph_requirement))
            state.requirements.forEach { add(requirementText(it)) }
            when (state.footnote) {
                CodeFootnote.LOCKED_RECOVERY -> add(stringResource(Res.string.code_workspace_locked_recovery))
                CodeFootnote.WITHOUT_WORKSPACE -> add(stringResource(Res.string.code_workspace_without_info))
                null -> Unit
            }
        }
        if (lines.isNotEmpty()) {
            Text(
                text = lines.joinToString(" "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 4.dp, start = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    if (state.sheetOpen) CodeWorkspaceSheet(composer)
}

@Composable
private fun requirementText(requirement: CodeRequirement): String =
    if (requirement.state == CodeRequirementState.CHOOSE) {
        stringResource(Res.string.code_workspace_required_for, requirement.agents, requirement.machine)
    } else {
        val status = stringResource(
            when (requirement.state) {
                CodeRequirementState.LOADING -> Res.string.code_workspace_loading
                CodeRequirementState.MISSING -> Res.string.code_workspace_missing
                CodeRequirementState.UNSUPPORTED -> Res.string.code_workspace_unsupported
                else -> Res.string.code_workspace_unavailable
            },
        )
        stringResource(Res.string.code_workspace_agent_status, requirement.agents, requirement.machine, status)
    }

@Composable
private fun MachineChip(
    name: String?,
    choices: List<com.garfiec.librechat.feature.chat.viewmodel.CodeMachineChoice>,
    composer: CodeWorkspaceComposer,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = name ?: stringResource(Res.string.code_machine_choose)
    val description = stringResource(Res.string.cd_code_chip, stringResource(Res.string.code_machine), label)
    Box {
        CodeChip(
            icon = Icons.Default.Computer,
            label = label,
            description = description,
            caret = choices.isNotEmpty(),
            onClick = if (choices.isNotEmpty()) ({ expanded = true }) else null,
        )
        AdaptiveDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                text = stringResource(Res.string.code_machine),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Default.Computer, contentDescription = null) },
                    trailingIcon = if (choice.selected) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        composer.onOpenSheet(choice.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun WorkspaceChip(state: CodeWorkspaceUiState, composer: CodeWorkspaceComposer) {
    val chip = state.workspace
    val label = when (chip.label) {
        CodeWorkspaceChipLabel.NAME -> chip.name.orEmpty()
        CodeWorkspaceChipLabel.CHOOSE -> stringResource(Res.string.code_workspace_choose)
        CodeWorkspaceChipLabel.NONE -> stringResource(Res.string.code_workspace_without)
        CodeWorkspaceChipLabel.LOADING -> stringResource(Res.string.code_workspace_loading)
        CodeWorkspaceChipLabel.MISSING -> stringResource(Res.string.code_workspace_missing)
        CodeWorkspaceChipLabel.UNAVAILABLE -> stringResource(Res.string.code_workspace_unavailable)
        CodeWorkspaceChipLabel.UNSUPPORTED -> stringResource(Res.string.code_workspace_unsupported)
    }
    val icon = when (chip.label) {
        CodeWorkspaceChipLabel.NONE, CodeWorkspaceChipLabel.MISSING,
        CodeWorkspaceChipLabel.UNAVAILABLE, CodeWorkspaceChipLabel.CHOOSE -> Icons.Outlined.FolderOff
        else -> Icons.Outlined.Folder
    }
    val description = stringResource(Res.string.cd_code_chip, stringResource(Res.string.code_workspace), label)
    val tint = when {
        state.attention -> MaterialTheme.colorScheme.error
        chip.label == CodeWorkspaceChipLabel.CHOOSE -> MaterialTheme.colorScheme.primary
        else -> null
    }
    CodeChip(
        icon = icon,
        label = label,
        description = description,
        caret = chip.opensSheet,
        // A locked chat cannot change its decision here: a tap re-reads the machine's status.
        trailing = if (chip.opensSheet) null else Icons.Default.Refresh,
        tint = tint,
        onClick = { if (chip.opensSheet) composer.onOpenSheet(null) else composer.onRefresh() },
    )
}

@Composable
private fun BranchChip(branch: String) {
    var expanded by remember { mutableStateOf(false) }
    val description = stringResource(Res.string.cd_code_chip, stringResource(Res.string.code_branch), branch)
    Box {
        CodeChip(
            icon = Icons.Default.AccountTree,
            label = branch,
            description = description,
            caret = false,
            // Read only: the tap only explains why the branch can't be changed.
            onClick = { expanded = true },
        )
        AdaptiveDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                text = stringResource(Res.string.code_branch_info),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun CodeChip(
    icon: ImageVector,
    label: String,
    description: String,
    caret: Boolean,
    onClick: (() -> Unit)?,
    trailing: ImageVector? = null,
    tint: androidx.compose.ui.graphics.Color? = null,
) {
    AdaptiveAssistChip(
        onClick = onClick ?: {},
        label = { Text(label.clipped(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = Modifier.semantics {
            contentDescription = description
            role = Role.Button
        },
        // The composer overlays the list, so the chip needs a container; the tint shows in both styles.
        colors = AdaptiveChipDefaults.overlayAssistChipColors(tint),
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
        trailingIcon = when {
            trailing != null -> {
                { Icon(trailing, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) }
            }
            caret -> {
                { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) }
            }
            else -> null
        },
    )
}

private fun checkoutIcon(mode: CodeCheckoutMode): ImageVector = when (mode) {
    CodeCheckoutMode.AUTO -> Icons.Default.AutoAwesome
    CodeCheckoutMode.WORKTREE -> Icons.Outlined.CopyAll
    CodeCheckoutMode.REGISTERED -> Icons.Outlined.Inventory2
}

/** The workspace picker: one section per machine, radio rows, then "No workspace" and Refresh. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodeWorkspaceSheet(composer: CodeWorkspaceComposer) {
    val sheet = composer.state.sheet
    AdaptiveModalBottomSheet(onDismissRequest = composer.onDismissSheet) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(Res.string.code_workspace),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            sheet.sections.forEach { section -> MachineSection(section, composer) }
            if (sheet.offersNoWorkspace) {
                AdaptiveDivider(modifier = Modifier.padding(vertical = 8.dp))
                RadioRow(
                    title = stringResource(Res.string.code_workspace_without),
                    subtitle = stringResource(Res.string.code_workspace_without_info),
                    selected = sheet.noWorkspaceSelected,
                    onClick = composer.onSelectNoWorkspace,
                )
            }
            AdaptiveTextButton(
                onClick = composer.onRefresh,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.code_workspace_refresh))
            }
        }
    }
}

@Composable
private fun MachineSection(section: CodeMachineSection, composer: CodeWorkspaceComposer) {
    Column {
        AdaptiveSectionHeader(section.name)
        section.usedBy?.let {
            Text(
                text = stringResource(Res.string.code_workspace_used_by, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 4.dp),
            )
        }
        when (section.status) {
            CodeMachineStatus.READY -> section.rows.forEach { row -> WorkspaceRow(section.id, row, composer) }
            else -> {
                val text: StringResource = when (section.status) {
                    CodeMachineStatus.LOADING -> Res.string.code_workspace_loading
                    CodeMachineStatus.UNSUPPORTED -> Res.string.code_workspace_unsupported
                    else -> Res.string.code_workspace_unavailable
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Icon(
                        Icons.Outlined.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceRow(environmentId: String, row: CodeWorkspaceRowView, composer: CodeWorkspaceComposer) {
    Column {
        RadioRow(
            title = row.name,
            subtitle = row.subtitle,
            selected = row.selected,
            onClick = { composer.onSelectWorkspace(environmentId, row.id) },
        )
        val checkout = row.checkout ?: return@Column
        val modes = buildList {
            add(CodeCheckoutMode.AUTO)
            if (checkout.offersWorktree) add(CodeCheckoutMode.WORKTREE)
            add(CodeCheckoutMode.REGISTERED)
        }
        Column(modifier = Modifier.padding(start = 64.dp, end = 24.dp, bottom = 8.dp)) {
            Text(
                stringResource(Res.string.code_checkout_mode),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            val labels = modes.map {
                stringResource(
                    when (it) {
                        CodeCheckoutMode.AUTO -> Res.string.code_checkout_auto
                        CodeCheckoutMode.WORKTREE -> Res.string.code_checkout_worktree
                        CodeCheckoutMode.REGISTERED -> Res.string.code_checkout_registered
                    },
                )
            }
            AdaptiveSegmentedChoice(
                options = labels,
                selectedIndex = modes.indexOf(checkout.selected).coerceAtLeast(0),
                onSelect = { index -> composer.onSetCheckout(environmentId, modes[index]) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(
                    when (checkout.selected) {
                        CodeCheckoutMode.AUTO -> Res.string.code_checkout_auto_info
                        CodeCheckoutMode.WORKTREE -> Res.string.code_checkout_worktree_info
                        CodeCheckoutMode.REGISTERED -> Res.string.code_checkout_registered_info
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun RadioRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        AdaptiveRadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
