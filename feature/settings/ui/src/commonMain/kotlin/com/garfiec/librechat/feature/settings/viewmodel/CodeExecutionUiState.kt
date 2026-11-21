package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.code.CodePairingError
import com.garfiec.librechat.core.model.code.CodeRemovalError
import com.garfiec.librechat.core.model.code.CodeWorkerShell

enum class CodeMachineStatusLabel { CHECKING, UNAVAILABLE, OFFLINE, STARTING, READY }

/** `fileWrite` or `commandExecution`. */
enum class CodePermissionCategory { FILE_WRITE, COMMAND_EXECUTION }

@Immutable
data class CodePermissionControl(
    val category: CodePermissionCategory,
    /** `allow`, `ask`, `deny`: what the machine offers. */
    val options: List<String>,
    val selected: String,
)

@Immutable
data class CodeMachineRow(
    val id: String,
    val name: String,
    val status: CodeMachineStatusLabel,
    /** `sandboxProfile · runtimes`, when the worker reports a profile. */
    val detail: String?,
    val canDelete: Boolean,
    /** Empty when the machine offers none, or this user may not edit it. */
    val permissions: List<CodePermissionControl>,
)

@Immutable
data class CodeControlPlaneOption(val id: String, val name: String)

/** The one-time worker command, shown until the pairing code expires. */
@Immutable
data class CodePairingView(val shell: CodeWorkerShell, val command: String)

@Immutable
data class CodeScopeSetting(
    /** In upstream's order; a saved scope this deployment no longer allows is listed disabled. */
    val options: List<String>,
    val disallowed: String?,
    val selected: String,
    val saving: Boolean,
)

@Immutable
data class CodeExecutionUiState(
    /** Null until the user's saved scope has been read. */
    val scope: CodeScopeSetting? = null,
    val scopeLoaded: Boolean = false,
    val scopeLoadFailed: Boolean = false,
    val scopeSaving: Boolean = false,
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val machines: List<CodeMachineRow> = emptyList(),
    /** Empty: this server doesn't offer self-service workers, so pairing is not offered. */
    val controlPlanes: List<CodeControlPlaneOption> = emptyList(),
    val pairName: String = "",
    val pairPlaneId: String? = null,
    val pairing: Boolean = false,
    val pairingCommand: CodePairingView? = null,
    val pendingRemoval: CodeMachineRow? = null,
    val removingId: String? = null,
    val savingPermissionIds: Set<String> = emptySet(),
    val setupLink: String = "",
    val setupLinkInvalid: Boolean = false,
) {
    val canPair: Boolean get() = pairName.isNotBlank() && pairPlaneId != null && !pairing
}

/** One-shot outcomes, rendered as snackbars. */
sealed interface CodeExecutionEvent {
    data object PairingCreated : CodeExecutionEvent
    data class PairingFailed(val error: CodePairingError) : CodeExecutionEvent
    data object PermissionsSaved : CodeExecutionEvent
    data object PermissionsFailed : CodeExecutionEvent
    data object Removed : CodeExecutionEvent
    data class RemoveFailed(val error: CodeRemovalError) : CodeExecutionEvent
    data object PreferencesUpdated : CodeExecutionEvent
    data object PreferencesFailed : CodeExecutionEvent
    data class OpenSetup(val attemptId: String) : CodeExecutionEvent
}
