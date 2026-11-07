package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.garfiec.librechat.feature.chat.resources.cd_code_workspace_folded
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
import com.garfiec.librechat.feature.chat.resources.code_linked_worktrees
import com.garfiec.librechat.feature.chat.resources.code_linked_worktrees_info
import com.garfiec.librechat.feature.chat.resources.code_machine
import com.garfiec.librechat.feature.chat.resources.code_workspace
import com.garfiec.librechat.feature.chat.resources.code_workspace_agent_status
import com.garfiec.librechat.feature.chat.resources.code_workspace_choose
import com.garfiec.librechat.feature.chat.resources.code_workspace_graph_requirement
import com.garfiec.librechat.feature.chat.resources.code_workspace_graph_selection_conflict
import com.garfiec.librechat.feature.chat.resources.code_workspace_loading
import com.garfiec.librechat.feature.chat.resources.code_workspace_locked_recovery
import com.garfiec.librechat.feature.chat.resources.code_workspace_missing
import com.garfiec.librechat.feature.chat.resources.code_workspace_refresh
import com.garfiec.librechat.feature.chat.resources.code_workspace_required_for
import com.garfiec.librechat.feature.chat.resources.code_workspace_saved_on_send
import com.garfiec.librechat.feature.chat.resources.code_workspace_unavailable
import com.garfiec.librechat.feature.chat.resources.code_workspace_unsupported
import com.garfiec.librechat.feature.chat.resources.code_workspace_used_by
import com.garfiec.librechat.feature.chat.resources.code_workspace_without
import com.garfiec.librechat.feature.chat.resources.code_workspace_without_info
import com.garfiec.librechat.feature.chat.resources.code_workspaces_selected
import com.garfiec.librechat.feature.chat.resources.draft_subtitle_join
import com.garfiec.librechat.feature.chat.viewmodel.CodeApprovalOption
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
import org.jetbrains.compose.resources.pluralStringResource
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
    val onSelectTransitionWorkspace: (environmentId: String, workspaceId: String) -> Unit = { _, _ -> },
    val onSetTransitionCheckout: (environmentId: String, mode: CodeCheckoutMode) -> Unit = { _, _ -> },
    val onConfirmTransition: () -> Unit = {},
    val onDetach: () -> Unit = {},
    val onRetryReconcile: () -> Unit = {},
    val onSelectApprovalMode: (CodeApprovalOption) -> Unit = {},
)

/** Provided around the composer so both platform composers draw the workspace chips and notice. Null draws nothing. */
val LocalCodeWorkspaceComposer = compositionLocalOf<CodeWorkspaceComposer?> { null }

/** The line's chip names two of them ("workspace · machine") on a line that scrolls. */
private val LINE_NAME_MAX_WIDTH = 256.dp

internal fun codeWorkspaceLineLeading(composer: CodeWorkspaceComposer?): (@Composable () -> Unit)? =
    if (composer?.state?.onSettingsLine == true) ({ CodeWorkspaceLineChips(composer) }) else null

/**
 * The workspace chip keeps this place in every state; what needs the user is its tint and
 * [CodeWorkspaceNotice]. Draws [CodeWorkspaceComposer.state] only; every decision is the delegate's.
 */
@Composable
private fun CodeWorkspaceLineChips(composer: CodeWorkspaceComposer) {
    val state = composer.state
    if (state.shown) LineWorkspaceChip(state, composer)
    state.approval?.let { ApprovalChip(it, composer.onSelectApprovalMode) }
}

@Composable
private fun LineWorkspaceChip(state: CodeWorkspaceUiState, composer: CodeWorkspaceComposer) {
    val chip = state.workspace
    val transition = state.transition?.takeIf { it.renamesChip }
    val workspace = when {
        transition != null -> transitionLabel(transition)
        else -> when (chip.label) {
            CodeWorkspaceChipLabel.NAME -> chip.name.orEmpty()
            CodeWorkspaceChipLabel.COUNT -> pluralStringResource(Res.plurals.code_workspaces_selected, chip.count, chip.count)
            CodeWorkspaceChipLabel.CHOOSE -> stringResource(Res.string.code_workspace_choose)
            CodeWorkspaceChipLabel.NONE -> stringResource(Res.string.code_workspace_without)
            CodeWorkspaceChipLabel.LOADING -> stringResource(Res.string.code_workspace_loading)
            CodeWorkspaceChipLabel.MISSING -> stringResource(Res.string.code_workspace_missing)
            CodeWorkspaceChipLabel.UNAVAILABLE -> stringResource(Res.string.code_workspace_unavailable)
            CodeWorkspaceChipLabel.UNSUPPORTED -> stringResource(Res.string.code_workspace_unsupported)
            CodeWorkspaceChipLabel.GENERIC -> stringResource(Res.string.code_workspace)
        }
    }
    val machine = if (transition == null) state.lineMachineName() else null
    val description = if (machine != null) {
        stringResource(Res.string.cd_code_workspace_folded, workspace, machine)
    } else {
        stringResource(Res.string.cd_code_chip, stringResource(Res.string.code_workspace), workspace)
    }
    val icon = when {
        transition != null -> Icons.AutoMirrored.Filled.DriveFileMove
        chip.label == CodeWorkspaceChipLabel.NONE || chip.label == CodeWorkspaceChipLabel.MISSING ||
            chip.label == CodeWorkspaceChipLabel.UNAVAILABLE || chip.label == CodeWorkspaceChipLabel.CHOOSE ->
            Icons.Outlined.FolderOff
        else -> Icons.Outlined.Folder
    }
    val tint = when {
        state.attention -> MaterialTheme.colorScheme.error
        // Not saved yet: the next message seals it, as its sheet says.
        chip.label == CodeWorkspaceChipLabel.CHOOSE || !state.locked -> MaterialTheme.colorScheme.primary
        else -> null
    }
    var detailsOpen by remember { mutableStateOf(false) }
    val details = sealedDetails(state)
    val tap: (() -> Unit)? = when {
        chip.opensSheet -> ({ composer.onOpenSheet(null) })
        details.isNotEmpty() || chip.refreshes -> ({ detailsOpen = true })
        else -> null
    }
    Box {
        CodeChip(
            icon = icon,
            // The workspace first, so a label cut short loses the machine, not what the chat works in.
            label = listOfNotNull(workspace, machine).joinToString(" · "),
            description = description,
            isName = chip.label == CodeWorkspaceChipLabel.NAME && transition == null,
            tint = tint,
            onClick = tap,
        )
        AdaptiveDropdownMenu(expanded = detailsOpen, onDismissRequest = { detailsOpen = false }) {
            details.forEach { (title, body) ->
                Column(modifier = Modifier.widthIn(max = 280.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(title, style = MaterialTheme.typography.labelLarge)
                    if (body != null) {
                        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (chip.refreshes) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.code_workspace_refresh)) },
                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    onClick = {
                        detailsOpen = false
                        composer.onRefresh()
                    },
                )
            }
        }
        // A window of its own, in the chip's box so the line's spacing doesn't count it.
        CodeWorkspaceSheetHost(composer)
    }
}

@Composable
private fun sealedDetails(state: CodeWorkspaceUiState): List<Pair<String, String?>> = buildList {
    state.branch?.let {
        val title = stringResource(Res.string.draft_subtitle_join, stringResource(Res.string.code_branch), it)
        add(title to stringResource(Res.string.code_branch_info))
    }
    state.checkout?.let { checkout ->
        val (label, info) = when (checkout.mode) {
            CodeCheckoutMode.AUTO -> Res.string.code_checkout_auto_chip to Res.string.code_checkout_auto_info
            CodeCheckoutMode.WORKTREE -> Res.string.code_checkout_worktree to Res.string.code_checkout_worktree_info
            CodeCheckoutMode.REGISTERED -> Res.string.code_checkout_registered_chip to Res.string.code_checkout_registered_info
        }
        add(
            stringResource(Res.string.draft_subtitle_join, stringResource(Res.string.code_checkout_mode), stringResource(label)) to
                stringResource(info),
        )
    }
    if (state.linkedWorktrees) {
        add(stringResource(Res.string.code_linked_worktrees) to stringResource(Res.string.code_linked_worktrees_info))
    }
    if (state.footnote == CodeFootnote.WITHOUT_WORKSPACE) {
        add(stringResource(Res.string.code_workspace_without) to stringResource(Res.string.code_workspace_without_info))
    }
}

@Composable
private fun CodeWorkspaceSheetHost(composer: CodeWorkspaceComposer) {
    val state = composer.state
    if (!state.sheetOpen) return
    val transition = state.transition
    if (transition != null) TransitionSheet(transition, composer) else CodeWorkspaceSheet(composer)
}

@Composable
internal fun CodeWorkspaceNotice(composer: CodeWorkspaceComposer) {
    val state = composer.state
    val confirming = state.confirming
    if (confirming != null) {
        ConfirmingRow(confirming, composer.onRetryReconcile, modifier = Modifier.padding(horizontal = 12.dp))
        return
    }
    val lines = buildList {
        if (state.graphRequirement) add(stringResource(Res.string.code_workspace_graph_requirement))
        state.requirements.forEach { add(requirementText(it)) }
        if (state.selectionConflict) add(stringResource(Res.string.code_workspace_graph_selection_conflict))
        if (state.footnote == CodeFootnote.LOCKED_RECOVERY) add(stringResource(Res.string.code_workspace_locked_recovery))
    }
    if (lines.isEmpty()) return
    Text(
        text = lines.joinToString("\n"),
        style = MaterialTheme.typography.bodySmall,
        color = if (state.attention) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
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

/** A code chip on the settings line, filled like the line's "on" chips, with no border. */
@Composable
internal fun CodeChip(
    icon: ImageVector,
    label: String,
    description: String,
    onClick: (() -> Unit)?,
    /** A user-chosen name, capped in width. A translated label keeps its full length. */
    isName: Boolean = false,
    nameMaxWidth: Dp = LINE_NAME_MAX_WIDTH,
    tint: androidx.compose.ui.graphics.Color? = null,
    /** Draws [icon] alone; [description] still names the label. */
    iconOnly: Boolean = false,
) {
    val overlay = AdaptiveChipDefaults.overlayTonalChipColors(tint)
    val iconContent: @Composable () -> Unit = {
        Icon(icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
    }
    AdaptiveAssistChip(
        onClick = onClick ?: {},
        // With nothing to pick, the chip is a label: no ripple, and announced as text rather than a
        // button a double-tap does nothing with. Drawn in its enabled colours.
        enabled = onClick != null,
        // Icon-only, the icon is the label, so M3's label padding centres it.
        label = if (iconOnly) {
            iconContent
        } else {
            {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (isName) Modifier.widthIn(max = nameMaxWidth) else Modifier,
                )
            }
        },
        modifier = if (onClick != null) {
            Modifier.semantics {
                contentDescription = description
                role = Role.Button
            }
        } else {
            Modifier.clearAndSetSemantics { contentDescription = description }
        },
        colors = overlay.copy(
            disabledLabelColor = overlay.labelColor,
            disabledLeadingIconContentColor = overlay.leadingIconContentColor,
            disabledTrailingIconContentColor = overlay.trailingIconContentColor,
        ),
        border = null,
        leadingIcon = if (iconOnly) null else iconContent,
    )
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
            val state = composer.state
            if (!state.locked) {
                Text(
                    text = stringResource(Res.string.code_workspace_saved_on_send),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
                )
            }
            val machineChoices = state.machine?.choices.orEmpty()
            if (machineChoices.isNotEmpty()) {
                AdaptiveSectionHeader(stringResource(Res.string.code_machine), horizontalInset = 24.dp)
                machineChoices.forEach { choice ->
                    RadioRow(
                        title = choice.name,
                        subtitle = null,
                        selected = choice.selected,
                        onClick = { composer.onOpenSheet(choice.id) },
                    )
                }
                AdaptiveDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            state.checkoutSummaries.forEach { summary ->
                val mode = stringResource(
                    if (summary.mode == CodeCheckoutMode.WORKTREE) {
                        Res.string.code_checkout_worktree
                    } else {
                        Res.string.code_checkout_registered_chip
                    },
                )
                SheetNote(listOf(summary.machine, summary.workspace, mode).joinToString(" · "))
            }
            if (state.linkedWorktrees) SheetNote(stringResource(Res.string.code_linked_worktrees_info))
            sheet.sections.forEach { section ->
                MachineSection(section, onSelect = composer.onSelectWorkspace, onSetCheckout = composer.onSetCheckout)
            }
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
private fun SheetNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )
}

@Composable
internal fun MachineSection(
    section: CodeMachineSection,
    onSelect: (environmentId: String, workspaceId: String) -> Unit,
    onSetCheckout: (environmentId: String, mode: CodeCheckoutMode) -> Unit,
) {
    Column {
        AdaptiveSectionHeader(section.name, horizontalInset = 24.dp)
        section.usedBy?.let {
            Text(
                text = stringResource(Res.string.code_workspace_used_by, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 4.dp),
            )
        }
        when (section.status) {
            CodeMachineStatus.READY -> section.rows.forEach { row -> WorkspaceRow(section.id, row, onSelect, onSetCheckout) }
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
private fun WorkspaceRow(
    environmentId: String,
    row: CodeWorkspaceRowView,
    onSelect: (environmentId: String, workspaceId: String) -> Unit,
    onSetCheckout: (environmentId: String, mode: CodeCheckoutMode) -> Unit,
) {
    Column {
        RadioRow(
            title = row.name,
            subtitle = row.subtitle,
            selected = row.selected,
            onClick = { onSelect(environmentId, row.id) },
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
                onSelect = { index -> onSetCheckout(environmentId, modes[index]) },
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
