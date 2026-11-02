package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.code.CodeEnvironmentMode
import com.garfiec.librechat.core.model.code.CodeEnvironmentStatus
import com.garfiec.librechat.core.model.code.CodeWorkspaceCheckout
import com.garfiec.librechat.core.model.code.CodeWorkspaceDescriptor
import com.garfiec.librechat.core.model.code.CodeWorkspaceResolver
import com.garfiec.librechat.core.model.code.CodeWorkspaceResolver.State
import com.garfiec.librechat.core.model.code.CodeWorkspaceResolver.StatusResult
import com.garfiec.librechat.core.model.code.PublicCodeEnvironment
import com.garfiec.librechat.feature.chat.viewmodel.CodeCheckoutChip
import com.garfiec.librechat.feature.chat.viewmodel.CodeCheckoutChoice
import com.garfiec.librechat.feature.chat.viewmodel.CodeCheckoutMode
import com.garfiec.librechat.feature.chat.viewmodel.CodeFootnote
import com.garfiec.librechat.feature.chat.viewmodel.CodeMachineChip
import com.garfiec.librechat.feature.chat.viewmodel.CodeMachineChoice
import com.garfiec.librechat.feature.chat.viewmodel.CodeMachineSection
import com.garfiec.librechat.feature.chat.viewmodel.CodeMachineStatus
import com.garfiec.librechat.feature.chat.viewmodel.CodeRequirement
import com.garfiec.librechat.feature.chat.viewmodel.CodeRequirementState
import com.garfiec.librechat.feature.chat.viewmodel.CodeWorkspaceChip
import com.garfiec.librechat.feature.chat.viewmodel.CodeWorkspaceChipLabel
import com.garfiec.librechat.feature.chat.viewmodel.CodeWorkspaceRowView
import com.garfiec.librechat.feature.chat.viewmodel.CodeWorkspaceSheet
import com.garfiec.librechat.feature.chat.viewmodel.CodeWorkspaceUiState

/**
 * The composer row and sheet for [resolution], as web's `CodeWorkspaceMenu.tsx` lays them out
 * (slice 1: no move, attach, detach or recovery). [browsingMachineId] is a machine the user picked
 * from the machine chip that the decision does not use yet; [browsingStatus] is its poll.
 */
@Suppress("CyclomaticComplexMethod", "LongMethod", "ComplexCondition") // web's menu layout in one mapping
internal fun codeWorkspaceUiState(
    resolution: CodeWorkspaceResolver.Resolution,
    draftMode: String?,
    browsingMachineId: String?,
    browsingStatus: StatusResult?,
    sheetOpen: Boolean,
    attention: Boolean,
): CodeWorkspaceUiState {
    if (!resolution.visible) return CodeWorkspaceUiState()
    val environments = resolution.environments
    val only = environments.singleOrNull()
    val onlyDescriptor = only?.workspaces?.firstOrNull { it.id == only.selected?.workspaceId }
    val withoutWorkspace = resolution.state == State.WITHOUT_ATTACHED

    val workspaceChip = CodeWorkspaceChip(
        label = when {
            withoutWorkspace -> CodeWorkspaceChipLabel.NONE
            onlyDescriptor != null -> CodeWorkspaceChipLabel.NAME
            else -> when (resolution.state) {
                State.LOADING -> CodeWorkspaceChipLabel.LOADING
                State.MISSING -> CodeWorkspaceChipLabel.MISSING
                State.UNAVAILABLE -> CodeWorkspaceChipLabel.UNAVAILABLE
                State.UNSUPPORTED -> CodeWorkspaceChipLabel.UNSUPPORTED
                else -> CodeWorkspaceChipLabel.CHOOSE
            }
        },
        name = onlyDescriptor?.displayName,
        opensSheet = !resolution.locked,
    )

    val machineOptions = LinkedHashMap<String, PublicCodeEnvironment>()
    environments.forEach { machineOptions[it.environment.id] = it.environment }
    resolution.machineOptions.forEach { machineOptions[it.id] = it }
    val machineChip = when {
        // Web shows the picker on every undecided chat with machines; the spec narrows it to a
        // choice that exists: per-chat machine choice, more than one machine to pick from.
        !resolution.locked && resolution.machineOptions.isNotEmpty() && machineOptions.size > 1 -> CodeMachineChip(
            name = if (withoutWorkspace) null else only?.environment?.displayName,
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

    val selected = only?.selected
    val checkout = if (only != null && selected != null && !withoutWorkspace &&
        (onlyDescriptor?.offersWorktree == true || selected.checkout != null || only.allowCheckoutSelection)
    ) {
        CodeCheckoutChip(checkoutModeOf(selected.checkout), pickable = only.allowCheckoutSelection && !resolution.locked)
    } else {
        null
    }

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

    return CodeWorkspaceUiState(
        visible = true,
        locked = resolution.locked,
        holdsSend = resolution.submission == null,
        machine = machineChip,
        workspace = workspaceChip,
        branch = onlyDescriptor?.environment?.ref?.takeIf { it.isNotBlank() && !withoutWorkspace },
        checkout = checkout,
        requirements = requirements,
        graphRequirement = requirements.isNotEmpty() && codingAgents.size > 1,
        footnote = when {
            withoutWorkspace -> CodeFootnote.WITHOUT_WORKSPACE
            resolution.locked && resolution.submission == null -> CodeFootnote.LOCKED_RECOVERY
            else -> null
        },
        attention = attention,
        sheetOpen = sheetOpen && !resolution.locked,
        sheet = CodeWorkspaceSheet(
            sections = sections,
            offersNoWorkspace = resolution.supportsDecisions,
            noWorkspaceSelected = draftMode == CodeEnvironmentMode.WITHOUT_ATTACHED,
        ),
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
