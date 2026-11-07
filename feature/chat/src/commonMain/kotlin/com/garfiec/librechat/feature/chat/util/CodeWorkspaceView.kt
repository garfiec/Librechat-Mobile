package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.code.CodeApproval
import com.garfiec.librechat.core.model.code.CodeApprovalMode
import com.garfiec.librechat.core.model.code.CodeEnvironmentMode
import com.garfiec.librechat.core.model.code.CodeEnvironmentStatus
import com.garfiec.librechat.core.model.code.CodeWorkspaceCheckout
import com.garfiec.librechat.core.model.code.CodeWorkspaceDescriptor
import com.garfiec.librechat.core.model.code.CodeWorkspaceResolver
import com.garfiec.librechat.core.model.code.CodeWorkspaceResolver.State
import com.garfiec.librechat.core.model.code.CodeWorkspaceResolver.StatusResult
import com.garfiec.librechat.core.model.code.CodeWorkspaceResolver.TransitionKind
import com.garfiec.librechat.core.model.code.CodeWorkspaceRouting
import com.garfiec.librechat.core.model.code.CodeWorkspaceSelection
import com.garfiec.librechat.core.model.code.PublicCodeEnvironment
import com.garfiec.librechat.feature.chat.viewmodel.CodeApprovalChip
import com.garfiec.librechat.feature.chat.viewmodel.CodeApprovalOption
import com.garfiec.librechat.feature.chat.viewmodel.CodeCheckoutChip
import com.garfiec.librechat.feature.chat.viewmodel.CodeCheckoutChoice
import com.garfiec.librechat.feature.chat.viewmodel.CodeCheckoutMode
import com.garfiec.librechat.feature.chat.viewmodel.CodeCheckoutSummary
import com.garfiec.librechat.feature.chat.viewmodel.CodeConfirming
import com.garfiec.librechat.feature.chat.viewmodel.CodeFootnote
import com.garfiec.librechat.feature.chat.viewmodel.CodeMachineChip
import com.garfiec.librechat.feature.chat.viewmodel.CodeMachineChoice
import com.garfiec.librechat.feature.chat.viewmodel.CodeMachineSection
import com.garfiec.librechat.feature.chat.viewmodel.CodeMachineStatus
import com.garfiec.librechat.feature.chat.viewmodel.CodeRequirement
import com.garfiec.librechat.feature.chat.viewmodel.CodeRequirementState
import com.garfiec.librechat.feature.chat.viewmodel.CodeTransitionAction
import com.garfiec.librechat.feature.chat.viewmodel.CodeTransitionInfo
import com.garfiec.librechat.feature.chat.viewmodel.CodeTransitionView
import com.garfiec.librechat.feature.chat.viewmodel.CodeWorkspaceChip
import com.garfiec.librechat.feature.chat.viewmodel.CodeWorkspaceChipLabel
import com.garfiec.librechat.feature.chat.viewmodel.CodeWorkspaceRowView
import com.garfiec.librechat.feature.chat.viewmodel.CodeWorkspaceSheet
import com.garfiec.librechat.feature.chat.viewmodel.CodeWorkspaceUiState

/**
 * The composer row and sheet for [resolution], as web's `CodeWorkspaceMenu.tsx` lays them out.
 * [browsingMachineId] is a machine the user picked from the machine chip that the decision does
 * not use yet; [browsingStatus] is its poll. [transitionChoices] are the picks drafted for the
 * resolution's transition, [moving] says one is being written, and [confirming] replaces the
 * whole row with the decision check web shows after a failed turn.
 */
@Suppress("CyclomaticComplexMethod", "LongMethod", "ComplexCondition") // web's menu layout in one mapping
internal fun codeWorkspaceUiState(
    resolution: CodeWorkspaceResolver.Resolution,
    draftMode: String?,
    browsingMachineId: String?,
    browsingStatus: StatusResult?,
    sheetOpen: Boolean,
    attention: Boolean,
    transitionChoices: Map<String, CodeWorkspaceSelection> = emptyMap(),
    moving: Boolean = false,
    confirming: CodeConfirming? = null,
): CodeWorkspaceUiState {
    if (confirming != null) return CodeWorkspaceUiState(visible = true, holdsSend = true, confirming = confirming)
    if (!resolution.visible && !mayFold(resolution)) return CodeWorkspaceUiState()
    val transition = resolution.transition?.let { transitionView(it, transitionChoices, moving) }
    val environments = resolution.environments
    val only = environments.singleOrNull()
    val onlyDescriptor = only?.workspaces?.firstOrNull { it.id == only.selected?.workspaceId }
    val withoutWorkspace = resolution.state == State.WITHOUT_ATTACHED

    val workspaceChip = CodeWorkspaceChip(
        label = when {
            withoutWorkspace -> CodeWorkspaceChipLabel.NONE
            onlyDescriptor != null -> CodeWorkspaceChipLabel.NAME
            resolution.state == State.READY && environments.size > 1 -> CodeWorkspaceChipLabel.COUNT
            else -> when (resolution.state) {
                State.NOT_REQUIRED -> CodeWorkspaceChipLabel.GENERIC
                State.LOADING -> CodeWorkspaceChipLabel.LOADING
                State.MISSING -> CodeWorkspaceChipLabel.MISSING
                State.UNAVAILABLE -> CodeWorkspaceChipLabel.UNAVAILABLE
                State.UNSUPPORTED -> CodeWorkspaceChipLabel.UNSUPPORTED
                else -> CodeWorkspaceChipLabel.CHOOSE
            }
        },
        name = onlyDescriptor?.displayName,
        count = resolution.selections?.size ?: 0,
        opensSheet = !resolution.locked || transition != null,
        // A sealed "No workspace" has no machine status to read again.
        refreshes = resolution.locked && transition == null && !withoutWorkspace,
    )

    val machineOptions = LinkedHashMap<String, PublicCodeEnvironment>()
    environments.forEach { machineOptions[it.environment.id] = it.environment }
    resolution.machineOptions.forEach { machineOptions[it.id] = it }
    val machineChip = when {
        // Web shows the picker on every undecided chat with machines; the spec narrows it to a
        // choice that exists: per-chat machine choice, more than one machine to pick from.
        !resolution.locked && resolution.machineOptions.isNotEmpty() && machineOptions.size > 1 -> CodeMachineChip(
            name = if (withoutWorkspace) null else only?.environment?.displayName,
            count = if (withoutWorkspace) 0 else environments.size.takeIf { it > 1 } ?: 0,
            choices = machineOptions.values.map { env ->
                CodeMachineChoice(
                    id = env.id,
                    name = env.displayName,
                    selected = resolution.mode == CodeEnvironmentMode.ATTACHED && environments.any { it.environment.id == env.id },
                )
            },
        )
        // A locked chat names the machine it runs on, read only.
        resolution.locked && only != null && !withoutWorkspace -> CodeMachineChip(only.environment.displayName, emptyList())
        else -> null
    }

    // A locked chat keeps its recorded checkout on screen while the machine can't confirm the pick.
    val selected = only?.selected ?: only?.recorded
    val checkout = if (only != null && selected != null && !withoutWorkspace &&
        (onlyDescriptor?.offersWorktree == true || selected.checkout != null || only.allowCheckoutSelection)
    ) {
        CodeCheckoutChip(checkoutModeOf(selected.checkout), pickable = only.allowCheckoutSelection && !resolution.locked)
    } else {
        null
    }

    // Web's GitContext: Auto on a workspace that offers worktrees isolates the chat, so only the
    // registered checkout (or a workspace without worktrees) shares the linked worktrees.
    val linkedWorktrees = only?.selected?.let { pick ->
        onlyDescriptor != null && !withoutWorkspace &&
            !(pick.checkout != CodeWorkspaceCheckout.SOURCE && onlyDescriptor.offersWorktree) &&
            onlyDescriptor.workspaceScopes?.contains(CodeWorkspaceCheckout.SCOPE_GIT_LINKED_WORKTREE) == true &&
            only.environment.configSchema?.workspaces?.linkedWorktrees != false &&
            CodeWorkspaceRouting.isCheckoutAvailable(pick, onlyDescriptor, only.allowCheckoutSelection)
    } == true

    val requirements = if (withoutWorkspace) {
        emptyList()
    } else {
        environments.mapNotNull { result ->
            if (result.state == State.READY || result.requiredBy.isEmpty()) return@mapNotNull null
            CodeRequirement(
                agents = result.requiredBy.joinToString(", ") { it.name?.takeIf(String::isNotBlank) ?: it.id },
                machine = result.environment.displayName,
                state = when (result.state) {
                    State.CHOOSE -> CodeRequirementState.CHOOSE
                    State.LOADING -> CodeRequirementState.LOADING
                    State.MISSING -> CodeRequirementState.MISSING
                    State.UNSUPPORTED -> CodeRequirementState.UNSUPPORTED
                    else -> CodeRequirementState.UNAVAILABLE
                },
            )
        }
    }
    val codingAgents = environments.flatMap { result -> result.requiredBy.map { it.id } }.toSet()
    // Every machine has its pick, yet the decision doesn't resolve: one agent's machine choice
    // left another without the machine it needs.
    val selectionConflict = !withoutWorkspace && requirements.isEmpty() &&
        resolution.state == State.CHOOSE && !resolution.canSubmit
    val checkoutSummaries = if (environments.size > 1 && !withoutWorkspace) {
        environments.mapNotNull { result ->
            val pick = result.selected ?: return@mapNotNull null
            if (pick.checkout == null) return@mapNotNull null
            CodeCheckoutSummary(
                machine = result.environment.displayName,
                workspace = result.workspaces.firstOrNull { it.id == pick.workspaceId }?.displayName ?: pick.workspaceId,
                mode = checkoutModeOf(pick.checkout),
            )
        }
    } else {
        emptyList()
    }

    val sections = if (browsingMachineId != null && environments.none { it.environment.id == browsingMachineId }) {
        val env = resolution.machineOptions.firstOrNull { it.id == browsingMachineId }
        listOfNotNull(env?.let { browsingSection(it, browsingStatus) })
    } else {
        environments
            .filter { browsingMachineId == null || it.environment.id == browsingMachineId }
            .map { result ->
                CodeMachineSection(
                    id = result.environment.id,
                    name = result.environment.displayName,
                    usedBy = result.requiredBy.joinToString(", ") { it.name?.takeIf(String::isNotBlank) ?: it.id }
                        .takeIf { it.isNotEmpty() },
                    status = when (result.state) {
                        State.LOADING -> CodeMachineStatus.LOADING
                        State.UNAVAILABLE -> if (result.workspaces.isEmpty()) CodeMachineStatus.UNAVAILABLE else CodeMachineStatus.READY
                        State.UNSUPPORTED -> if (result.workspaces.isEmpty()) CodeMachineStatus.UNSUPPORTED else CodeMachineStatus.READY
                        else -> CodeMachineStatus.READY
                    },
                    rows = rowsFor(
                        result.workspaces,
                        selectedId = result.selected?.workspaceId.takeUnless { withoutWorkspace },
                        selectedCheckout = result.selected?.checkout,
                        allowCheckoutSelection = result.allowCheckoutSelection,
                    ),
                )
            }
    }

    val footnote = when {
        withoutWorkspace -> CodeFootnote.WITHOUT_WORKSPACE
        resolution.locked && resolution.submission == null -> CodeFootnote.LOCKED_RECOVERY
        else -> null
    }
    // Branch, checkout and linked worktrees never unfold it. An undecided chat with a machine to
    // choose stays unfolded: the pick isn't made until the send seals it.
    val settled = mayFold(resolution) ||
        (mayFoldUndecided(resolution) && machineChip?.choices.isNullOrEmpty())
    val folded = settled && !attention && !moving && footnote == null &&
        requirements.isEmpty() && !selectionConflict &&
        (transition == null || transition.action == CodeTransitionAction.DETACH) &&
        (workspaceChip.label == CodeWorkspaceChipLabel.NAME || workspaceChip.label == CodeWorkspaceChipLabel.COUNT)

    return CodeWorkspaceUiState(
        visible = !folded,
        folded = folded,
        locked = resolution.locked,
        holdsSend = resolution.submission == null,
        // The chip names its machine before the first send as after it ("workspace · machine"), so
        // sealing the pick doesn't rename it.
        machine = machineChip ?: only?.takeIf { !withoutWorkspace }?.let { CodeMachineChip(it.environment.displayName, emptyList()) },
        workspace = workspaceChip,
        branch = onlyDescriptor?.environment?.ref?.takeIf { it.isNotBlank() && !withoutWorkspace },
        checkout = checkout,
        linkedWorktrees = linkedWorktrees,
        requirements = requirements,
        graphRequirement = (requirements.isNotEmpty() || selectionConflict) && codingAgents.size > 1,
        selectionConflict = selectionConflict,
        checkoutSummaries = checkoutSummaries,
        footnote = footnote,
        attention = attention,
        sheetOpen = sheetOpen && (!resolution.locked || transition != null),
        transition = transition,
        sheet = CodeWorkspaceSheet(
            sections = sections,
            offersNoWorkspace = resolution.supportsDecisions,
            noWorkspaceSelected = draftMode == CodeEnvironmentMode.WITHOUT_ATTACHED,
        ),
    )
}

/** A saved decision the next send doesn't wait on, with every machine ready. */
private fun mayFold(resolution: CodeWorkspaceResolver.Resolution): Boolean =
    resolution.required && resolution.locked && resolution.submission != null && resolution.state == State.READY

/** An undecided chat whose next send would seal a ready pick. */
private fun mayFoldUndecided(resolution: CodeWorkspaceResolver.Resolution): Boolean =
    resolution.required && !resolution.locked && resolution.submission != null && resolution.state == State.READY

/** Web's `describeTransition` inputs, and the target sections with the drafted picks. */
private fun transitionView(
    transition: CodeWorkspaceResolver.Transition,
    choices: Map<String, CodeWorkspaceSelection>,
    moving: Boolean,
): CodeTransitionView {
    val proposal = CodeWorkspaceResolver.proposeTransition(transition, choices)
    val targetNames = transition.targets.joinToString(", ") { it.environment.displayName }
    val previousNames = transition.previous.joinToString(", ") { it.displayName }
    val action = when {
        transition.kind == TransitionKind.DETACH -> CodeTransitionAction.DETACH
        transition.isRecovery -> CodeTransitionAction.RECOVER
        transition.kind == TransitionKind.ATTACH -> CodeTransitionAction.ATTACH
        else -> CodeTransitionAction.MOVE
    }
    val info = when (action) {
        CodeTransitionAction.DETACH -> CodeTransitionInfo.DETACH
        CodeTransitionAction.RECOVER -> CodeTransitionInfo.RECOVER
        CodeTransitionAction.ATTACH -> CodeTransitionInfo.ATTACH
        CodeTransitionAction.MOVE -> when {
            transition.targets.isEmpty() && previousNames.isEmpty() -> CodeTransitionInfo.DETACH_PROMPT
            transition.targets.isEmpty() -> CodeTransitionInfo.MOVE_REMOVED
            previousNames.isEmpty() -> CodeTransitionInfo.MOVE_ADDED
            else -> CodeTransitionInfo.MOVE
        }
    }
    val sections = transition.targets.map { target ->
        val chosen = proposal.chosen.firstOrNull { it.environmentId == target.environment.id }
        CodeMachineSection(
            id = target.environment.id,
            name = target.environment.displayName,
            usedBy = target.requiredBy.joinToString(", ") { it.name?.takeIf(String::isNotBlank) ?: it.id }
                .takeIf { it.isNotEmpty() },
            status = if (target.workspaces.isEmpty()) CodeMachineStatus.UNAVAILABLE else CodeMachineStatus.READY,
            rows = rowsFor(
                target.workspaces,
                selectedId = chosen?.workspaceId,
                selectedCheckout = chosen?.checkout,
                allowCheckoutSelection = target.allowCheckoutSelection,
            ),
        )
    }
    return CodeTransitionView(
        action = action,
        targetNames = targetNames,
        singleTarget = transition.targets.size == 1,
        previousNames = previousNames,
        info = info,
        sections = sections,
        offersConfirm = transition.offersMove,
        canConfirm = transition.offersMove && proposal.ready && !moving,
        detachable = transition.detachable,
        busy = moving,
        renamesChip = transition.kind == TransitionKind.MOVE && transition.offersMove,
    )
}

private fun browsingSection(env: PublicCodeEnvironment, status: StatusResult?): CodeMachineSection {
    val loaded = (status as? StatusResult.Loaded)?.status?.takeIf { it.environmentId == env.id }
    return CodeMachineSection(
        id = env.id,
        name = env.displayName,
        usedBy = null,
        status = machineStatusOf(status, loaded),
        rows = rowsFor(
            loaded?.takeIf { it.isReady }?.workspaces.orEmpty(),
            selectedId = null,
            selectedCheckout = null,
            allowCheckoutSelection = env.configSchema?.workspaces?.allowCheckoutSelection == true,
        ),
    )
}

private fun machineStatusOf(status: StatusResult?, loaded: CodeEnvironmentStatus?): CodeMachineStatus = when {
    status == null || status is StatusResult.Loading -> CodeMachineStatus.LOADING
    loaded == null || !loaded.isReady -> CodeMachineStatus.UNAVAILABLE
    loaded.workspaces == null -> CodeMachineStatus.UNSUPPORTED
    loaded.workspaces.isNullOrEmpty() -> CodeMachineStatus.UNAVAILABLE
    else -> CodeMachineStatus.READY
}

private fun rowsFor(
    workspaces: List<CodeWorkspaceDescriptor>,
    selectedId: String?,
    selectedCheckout: String?,
    allowCheckoutSelection: Boolean,
): List<CodeWorkspaceRowView> {
    val nameCounts = workspaces.groupingBy { it.displayName }.eachCount()
    return workspaces.map { descriptor ->
        val git = listOfNotNull(
            descriptor.environment?.repo?.takeIf { it.isNotBlank() },
            descriptor.environment?.ref?.takeIf { it.isNotBlank() },
        )
        val parts = git + listOfNotNull(descriptor.id.takeIf { (nameCounts[descriptor.displayName] ?: 0) > 1 })
        val selected = descriptor.id == selectedId
        CodeWorkspaceRowView(
            id = descriptor.id,
            name = descriptor.displayName,
            subtitle = parts.joinToString(" · ").takeIf { it.isNotEmpty() },
            selected = selected,
            checkout = if (selected && allowCheckoutSelection) {
                CodeCheckoutChoice(checkoutModeOf(selectedCheckout), descriptor.offersWorktree)
            } else {
                null
            },
        )
    }
}

internal fun checkoutModeOf(checkout: String?): CodeCheckoutMode = when (checkout) {
    CodeWorkspaceCheckout.ISOLATED -> CodeCheckoutMode.WORKTREE
    CodeWorkspaceCheckout.SOURCE -> CodeCheckoutMode.REGISTERED
    else -> CodeCheckoutMode.AUTO
}

internal fun CodeCheckoutMode.wire(): String? = when (this) {
    CodeCheckoutMode.AUTO -> null
    CodeCheckoutMode.WORKTREE -> CodeWorkspaceCheckout.ISOLATED
    CodeCheckoutMode.REGISTERED -> CodeWorkspaceCheckout.SOURCE
}

/** The approval-mode chip for [picker]: shown only while the picker is available. */
internal fun approvalChip(picker: CodeApproval.Picker): CodeApprovalChip? {
    val selected = picker.selected?.takeIf { picker.available }?.let(::approvalOption) ?: return null
    return CodeApprovalChip(selected, picker.modes.mapNotNull(::approvalOption))
}

private fun approvalOption(mode: String): CodeApprovalOption? = when (mode) {
    CodeApprovalMode.ASK -> CodeApprovalOption.ASK
    CodeApprovalMode.ACCEPT_EDITS -> CodeApprovalOption.ACCEPT_EDITS
    CodeApprovalMode.FULL_ACCESS -> CodeApprovalOption.FULL_ACCESS
    else -> null
}

internal fun CodeApprovalOption.wire(): String = when (this) {
    CodeApprovalOption.ASK -> CodeApprovalMode.ASK
    CodeApprovalOption.ACCEPT_EDITS -> CodeApprovalMode.ACCEPT_EDITS
    CodeApprovalOption.FULL_ACCESS -> CodeApprovalMode.FULL_ACCESS
}
