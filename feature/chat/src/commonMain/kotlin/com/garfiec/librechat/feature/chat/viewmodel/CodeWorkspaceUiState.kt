package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable

/**
 * The composer's code-workspace row (`CodeWorkspaceRow`) and its sheet, already mapped to what is
 * drawn: the row resolves strings and nothing else. Written only by `CodeWorkspaceDelegate`.
 * Hidden ([visible] false) unless an agent in the chat runs code on an attached machine and the
 * chat is choosing, cannot send, or runs without a workspace.
 */
@Immutable
data class CodeWorkspaceUiState(
    val visible: Boolean = false,
    /** The chat already recorded its decision: nothing here can change it. */
    val locked: Boolean = false,
    /** Send waits on this row; [requirements] say why. */
    val holdsSend: Boolean = false,
    /** Null hides the machine chip. */
    val machine: CodeMachineChip? = null,
    val workspace: CodeWorkspaceChip = CodeWorkspaceChip(),
    /** The selected workspace's branch, as the worker reports it. Read only. */
    val branch: String? = null,
    val checkout: CodeCheckoutChip? = null,
    val requirements: List<CodeRequirement> = emptyList(),
    /** "Every coding agent in this chat needs a workspace, including subagents." */
    val graphRequirement: Boolean = false,
    val footnote: CodeFootnote? = null,
    /** A rejection named the workspace: tint its chip until the state changes. */
    val attention: Boolean = false,
    val sheetOpen: Boolean = false,
    val sheet: CodeWorkspaceSheet = CodeWorkspaceSheet(),
)

@Immutable
data class CodeMachineChip(
    val name: String?,
    val choices: List<CodeMachineChoice>,
)

@Immutable
data class CodeMachineChoice(val id: String, val name: String, val selected: Boolean)

enum class CodeWorkspaceChipLabel { NAME, CHOOSE, NONE, LOADING, MISSING, UNAVAILABLE, UNSUPPORTED }

@Immutable
data class CodeWorkspaceChip(
    val label: CodeWorkspaceChipLabel = CodeWorkspaceChipLabel.CHOOSE,
    /** The workspace's name for [CodeWorkspaceChipLabel.NAME]. */
    val name: String? = null,
    /** False on a locked chat, where a tap refreshes the machine's status instead. */
    val opensSheet: Boolean = true,
)

enum class CodeCheckoutMode { AUTO, WORKTREE, REGISTERED }

@Immutable
data class CodeCheckoutChip(val mode: CodeCheckoutMode, val pickable: Boolean)

enum class CodeRequirementState { CHOOSE, LOADING, MISSING, UNAVAILABLE, UNSUPPORTED }

/** "Choose a workspace for {agents} on {machine}." or that machine's state. */
@Immutable
data class CodeRequirement(val agents: String, val machine: String, val state: CodeRequirementState)

enum class CodeFootnote { LOCKED_RECOVERY, WITHOUT_WORKSPACE }

@Immutable
data class CodeWorkspaceSheet(
    val sections: List<CodeMachineSection> = emptyList(),
    /** "No workspace" is offered (the decision version is advertised). */
    val offersNoWorkspace: Boolean = false,
    val noWorkspaceSelected: Boolean = false,
)

enum class CodeMachineStatus { READY, LOADING, UNAVAILABLE, UNSUPPORTED }

@Immutable
data class CodeMachineSection(
    val id: String,
    val name: String,
    /** Agent names joined, or null when none is known (a machine the user is browsing). */
    val usedBy: String?,
    val status: CodeMachineStatus,
    val rows: List<CodeWorkspaceRowView>,
)

@Immutable
data class CodeWorkspaceRowView(
    val id: String,
    val name: String,
    /** `repo · ref`, plus the id when two workspaces share a name. */
    val subtitle: String?,
    val selected: Boolean,
    /** The selected row's checkout choice, when the machine lets the user pick one. */
    val checkout: CodeCheckoutChoice?,
)

@Immutable
data class CodeCheckoutChoice(val selected: CodeCheckoutMode, val offersWorktree: Boolean)
