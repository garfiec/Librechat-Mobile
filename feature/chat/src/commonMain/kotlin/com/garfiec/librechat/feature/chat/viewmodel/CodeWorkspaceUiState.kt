package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable

/**
 * Already mapped to what is drawn: the composables resolve strings and nothing else. Written only by
 * `CodeWorkspaceDelegate`.
 */
@Immutable
data class CodeWorkspaceUiState(
    /**
     * The workspace needs the user (choosing, cannot send, runs without a workspace, a line to read,
     * a machine to pick). Never with [folded].
     */
    val visible: Boolean = false,
    /**
     * The workspace is settled (a saved decision, or an undecided chat's ready pick, [locked] false)
     * and nothing needs the user: the chip reads "workspace · machine" ([workspace], [machine]).
     */
    val folded: Boolean = false,
    /** The chat already recorded its decision: nothing here can change it. */
    val locked: Boolean = false,
    /** Send waits on this row; [requirements] say why. */
    val holdsSend: Boolean = false,
    /** The machine the chat runs on, named after the workspace; its choices go in the sheet. */
    val machine: CodeMachineChip? = null,
    val workspace: CodeWorkspaceChip = CodeWorkspaceChip(),
    /** The selected workspace's branch, as the worker reports it. Read only. */
    val branch: String? = null,
    val checkout: CodeCheckoutChip? = null,
    /**
     * "Linked worktrees": commands under `.worktrees/<name>` run in that worktree's lane. Shown for
     * a workspace that advertises `git_linked_worktree` when the chat is not isolated in its own.
     */
    val linkedWorktrees: Boolean = false,
    val requirements: List<CodeRequirement> = emptyList(),
    /** "Every coding agent in this chat needs a workspace, including subagents." */
    val graphRequirement: Boolean = false,
    /**
     * Each machine has a pick, but they don't add up to what every agent needs (web's
     * `graph_selection_conflict`): an agent's machine choice left another agent without one.
     */
    val selectionConflict: Boolean = false,
    /** With several machines, each pick's explicit checkout, one line per machine. */
    val checkoutSummaries: List<CodeCheckoutSummary> = emptyList(),
    val footnote: CodeFootnote? = null,
    /** A rejection named the workspace: tint its chip until the state changes. */
    val attention: Boolean = false,
    val sheetOpen: Boolean = false,
    val sheet: CodeWorkspaceSheet = CodeWorkspaceSheet(),
    /**
     * The chat is confirming its decision (after a failed turn, or a change whose outcome is
     * unknown). While set, the row shows only this line, and Send waits.
     */
    val confirming: CodeConfirming? = null,
    /** A change of the saved chat's decision on offer; the workspace chip opens its sheet. */
    val transition: CodeTransitionView? = null,
    /**
     * The code approval mode picker. Independent of [visible]: a chat that shows no workspace still
     * picks how much its agent may do.
     */
    val approval: CodeApprovalChip? = null,
) {
    /** The workspace chip is on the settings line. */
    val shown: Boolean get() = visible || folded

    val onSettingsLine: Boolean get() = shown || approval != null

    /**
     * Null for a machine named as its workspace (the server's own environment is often both "Server
     * workspace"), so the chip never reads the name twice.
     */
    fun lineMachineName(): String? {
        if (workspace.label != CodeWorkspaceChipLabel.NAME) return null
        return machine?.name?.takeUnless { it.equals(workspace.name, ignoreCase = true) }
    }
}

enum class CodeApprovalOption { ASK, ACCEPT_EDITS, FULL_ACCESS }

@Immutable
data class CodeApprovalChip(val selected: CodeApprovalOption, val options: List<CodeApprovalOption>)

enum class CodeConfirming { PENDING, FAILED }

enum class CodeTransitionAction { MOVE, ATTACH, RECOVER, DETACH }

/** What a transition's explanation says; the names it needs are on [CodeTransitionView]. */
enum class CodeTransitionInfo { MOVE, MOVE_REMOVED, MOVE_ADDED, DETACH_PROMPT, ATTACH, RECOVER, DETACH }

/** Web's transition menu: a heading, an explanation, a section per target, Confirm and Detach. */
@Immutable
data class CodeTransitionView(
    val action: CodeTransitionAction,
    /** The target machines' names, joined. With [singleTarget] the label names it ("Move chat to …"). */
    val targetNames: String,
    val singleTarget: Boolean,
    /** Machines the recorded decision used that the agents no longer do, joined. */
    val previousNames: String,
    val info: CodeTransitionInfo,
    val sections: List<CodeMachineSection>,
    /** A move, attach or recovery to confirm (else only "Continue without workspace"). */
    val offersConfirm: Boolean,
    val canConfirm: Boolean,
    val detachable: Boolean,
    /** The change is being written; nothing here can be tapped. */
    val busy: Boolean,
    /** The workspace chip names the move instead of the chat's state, as web's rail does. */
    val renamesChip: Boolean,
)

@Immutable
data class CodeMachineChip(
    /** The one machine the chat runs on; null with none (or several, see [count]). */
    val name: String?,
    val choices: List<CodeMachineChoice>,
    /** Machines the decision uses when it is more than one: "{n} machines". */
    val count: Int = 0,
)

@Immutable
data class CodeCheckoutSummary(val machine: String, val workspace: String, val mode: CodeCheckoutMode)

@Immutable
data class CodeMachineChoice(val id: String, val name: String, val selected: Boolean)

/** [COUNT]: several machines each have their pick ("{n} code workspaces"). */
enum class CodeWorkspaceChipLabel { NAME, COUNT, CHOOSE, NONE, LOADING, MISSING, UNAVAILABLE, UNSUPPORTED, GENERIC }

@Immutable
data class CodeWorkspaceChip(
    val label: CodeWorkspaceChipLabel = CodeWorkspaceChipLabel.CHOOSE,
    /** The workspace's name for [CodeWorkspaceChipLabel.NAME]. */
    val name: String? = null,
    /** The number of picks for [CodeWorkspaceChipLabel.COUNT]. */
    val count: Int = 0,
    /**
     * False on a locked chat with nothing to change. A locked chat with a transition on offer opens
     * the transition sheet.
     */
    val opensSheet: Boolean = true,
    /**
     * A tap re-reads the machine's status (a locked chat with no transition). Neither this nor
     * [opensSheet] on a sealed "No workspace": the chip is a label.
     */
    val refreshes: Boolean = false,
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
