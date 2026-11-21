package com.garfiec.librechat.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.CodeEnvironmentRepository
import com.garfiec.librechat.core.data.repository.CodeMachinesRepository
import com.garfiec.librechat.core.data.repository.CodePairingOutcome
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.model.code.CodeEnvironmentPermissionField
import com.garfiec.librechat.core.model.code.CodeEnvironmentPermissionSettings
import com.garfiec.librechat.core.model.code.CodeEnvironmentStatus
import com.garfiec.librechat.core.model.code.CodeEnvironmentSummary
import com.garfiec.librechat.core.model.code.CodeEnvironmentUserSettings
import com.garfiec.librechat.core.model.code.CodeSetupAttempt
import com.garfiec.librechat.core.model.code.CodeWorkerCommand
import com.garfiec.librechat.core.model.code.CodeWorkerPairing
import com.garfiec.librechat.core.model.code.CodeWorkerShell
import com.garfiec.librechat.core.model.code.StatefulCodeScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Settings › Code execution, web's `CodeEnvironments.tsx` and `StatefulWorkspaceDefault.tsx`: the
 * default workspace scope new agents get, the user's machines with their status and permissions,
 * pairing a new machine (the one-time worker command), removing one, and opening a worker's setup
 * link. Machine status is re-read every [POLL_MS] while the page is in front, as web polls it.
 */
class CodeExecutionViewModel(
    private val machines: CodeMachinesRepository,
    private val environments: CodeEnvironmentRepository,
    private val users: UserRepository,
    configRepository: ConfigRepository,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    private val state = MutableStateFlow(CodeExecutionUiState())
    private val summaries = MutableStateFlow<List<CodeEnvironmentSummary>>(emptyList())
    private val statuses = MutableStateFlow<Map<String, CodeEnvironmentStatus?>>(emptyMap())
    private val savedScope = MutableStateFlow<String?>(null)
    private val chosenScope = MutableStateFlow<String?>(null)
    private val pairingResult = MutableStateFlow<CodeWorkerPairing?>(null)
    private val shell = MutableStateFlow(CodeWorkerShell.POSIX)

    private val _events = MutableSharedFlow<CodeExecutionEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<CodeExecutionEvent> = _events.asSharedFlow()

    private val allowedScopes = configRepository.endpointConfigs

    val uiState: StateFlow<CodeExecutionUiState> = combine(
        state,
        combine(summaries, statuses) { s, st -> s to st },
        combine(savedScope, chosenScope, allowedScopes) { saved, chosen, configs ->
            Triple(saved, chosen, configs["agents"]?.statefulCodeSessions?.allowedEnvironments)
        },
        combine(pairingResult, shell) { p, sh -> p to sh },
    ) { base, (list, st), (saved, chosen, allowed), (pairing, sh) ->
        base.copy(
            machines = list.map { machineRow(it, st[it.id], st.containsKey(it.id)) },
            scope = if (base.scopeLoaded) scopeSetting(saved, chosen, allowed, base.scopeSaving) else null,
            pairingCommand = pairing?.let { CodePairingView(sh, CodeWorkerCommand.setup(it, sh)) },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CodeExecutionUiState())

    private var pollJob: Job? = null
    private var expiryJob: Job? = null

    init {
        load()
        loadScope()
    }

    /** Never shows a fallback as the saved value: a failed read shows as failed, with Retry. */
    fun loadScope() {
        state.update { it.copy(scopeLoadFailed = false) }
        viewModelScope.launch {
            val user = users.getUser()
            if (user is Result.Success) {
                user.data.personalization?.statefulCodeEnvironment?.let { savedScope.value = it }
                state.update { it.copy(scopeLoaded = true) }
            } else {
                state.update { it.copy(scopeLoadFailed = true) }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            state.update { it.copy(loading = it.machines.isEmpty() && summaries.value.isEmpty(), loadFailed = false) }
            when (val result = machines.list()) {
                is Result.Success -> {
                    summaries.value = result.data.environments
                    val planes = result.data.controlPlanes.map { CodeControlPlaneOption(it.id, it.displayName) }
                    state.update { current ->
                        current.copy(
                            loading = false,
                            controlPlanes = planes,
                            pairPlaneId = current.pairPlaneId?.takeIf { id -> planes.any { it.id == id } } ?: planes.firstOrNull()?.id,
                        )
                    }
                    readStatuses()
                }
                else -> state.update { it.copy(loading = false, loadFailed = true) }
            }
        }
    }

    /** Starts the status poll; the page calls it on resume and [stopPolling] on pause. */
    fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(POLL_MS)
                readStatuses()
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun readStatuses() = coroutineScope {
        summaries.value.map { it.id }.map { id ->
            async { id to (environments.status(id) as? Result.Success)?.data }
        }.awaitAll().forEach { (id, status) -> statuses.update { it + (id to status) } }
    }

    private fun machineRow(summary: CodeEnvironmentSummary, status: CodeEnvironmentStatus?, read: Boolean): CodeMachineRow {
        val label = when {
            !read -> CodeMachineStatusLabel.CHECKING
            status == null -> CodeMachineStatusLabel.UNAVAILABLE
            status.status == CodeEnvironmentStatus.STATUS_READY -> CodeMachineStatusLabel.READY
            status.status == CodeEnvironmentStatus.STATUS_STARTING -> CodeMachineStatusLabel.STARTING
            else -> CodeMachineStatusLabel.OFFLINE
        }
        val fields = summary.configSchema?.permissions
        val configured = summary.settings?.permissions
        val permissions = if (summary.canEdit == true) {
            listOfNotNull(
                fields?.fileWrite?.control(CodePermissionCategory.FILE_WRITE, configured?.fileWrite),
                fields?.commandExecution?.control(CodePermissionCategory.COMMAND_EXECUTION, configured?.commandExecution),
            )
        } else {
            emptyList()
        }
        return CodeMachineRow(
            id = summary.id,
            name = summary.displayName,
            status = label,
            detail = status?.sandboxProfile?.let { (listOf(it) + status.runtimes.orEmpty()).joinToString(" · ") },
            canDelete = summary.canDelete == true,
            permissions = permissions,
        )
    }

    private fun CodeEnvironmentPermissionField.control(category: CodePermissionCategory, configured: String?) =
        CodePermissionControl(category, allowed, effective(configured)).takeIf { allowed.isNotEmpty() }

    private fun scopeSetting(saved: String?, chosen: String?, allowedConfig: List<String>?, saving: Boolean): CodeScopeSetting {
        val allowed = StatefulCodeScope.allowed(allowedConfig)
        val current = chosen ?: saved ?: StatefulCodeScope.resolve(StatefulCodeScope.USER, allowedConfig) ?: StatefulCodeScope.USER
        val disallowed = current.takeIf { it !in allowed }
        return CodeScopeSetting(
            options = allowed + listOfNotNull(disallowed),
            disallowed = disallowed,
            selected = current,
            saving = saving,
        )
    }

    /** Web's optimistic select: shown at once, put back if the server refuses it. */
    fun selectScope(scope: String) {
        val setting = uiState.value.scope ?: return
        if (scope == setting.disallowed || scope !in setting.options || setting.saving) return
        chosenScope.value = scope
        state.update { it.copy(scopeSaving = true) }
        viewModelScope.launch {
            val saved = users.setStatefulCodeEnvironment(scope) is Result.Success
            if (saved) savedScope.value = scope
            chosenScope.value = null
            state.update { it.copy(scopeSaving = false) }
            _events.emit(if (saved) CodeExecutionEvent.PreferencesUpdated else CodeExecutionEvent.PreferencesFailed)
        }
    }

    fun setPairName(name: String) = state.update { it.copy(pairName = name.take(PAIR_NAME_MAX)) }

    fun selectControlPlane(id: String) = state.update { it.copy(pairPlaneId = id) }

    fun selectShell(value: CodeWorkerShell) {
        shell.value = value
    }

    fun pair() {
        val current = state.value
        val plane = current.pairPlaneId ?: return
        val name = current.pairName.trim()
        if (name.isEmpty() || current.pairing) return
        state.update { it.copy(pairing = true) }
        viewModelScope.launch {
            when (val outcome = machines.pair(name, plane)) {
                is CodePairingOutcome.Paired -> {
                    state.update { it.copy(pairing = false, pairName = "") }
                    showPairing(outcome.response.pairing)
                    _events.emit(CodeExecutionEvent.PairingCreated)
                    load()
                }
                is CodePairingOutcome.Failed -> {
                    state.update { it.copy(pairing = false) }
                    _events.emit(CodeExecutionEvent.PairingFailed(outcome.error))
                }
            }
        }
    }

    /** The command goes away when its code expires, as web's timer drops it. */
    private fun showPairing(pairing: CodeWorkerPairing) {
        pairingResult.value = pairing
        expiryJob?.cancel()
        val expires = runCatching { Instant.parse(pairing.expiresAt) }.getOrNull()
        expiryJob = viewModelScope.launch {
            val remaining = expires?.let { it - clock.now() }?.inWholeMilliseconds ?: 0L
            delay(remaining.coerceAtLeast(0L))
            if (pairingResult.value == pairing) pairingResult.value = null
        }
    }

    fun setPermission(machineId: String, category: CodePermissionCategory, decision: String) {
        if (machineId in state.value.savingPermissionIds) return
        val settings = when (category) {
            CodePermissionCategory.FILE_WRITE -> CodeEnvironmentPermissionSettings(fileWrite = decision)
            CodePermissionCategory.COMMAND_EXECUTION -> CodeEnvironmentPermissionSettings(commandExecution = decision)
        }
        state.update { it.copy(savingPermissionIds = it.savingPermissionIds + machineId) }
        viewModelScope.launch {
            val saved = machines.updatePermissions(machineId, CodeEnvironmentUserSettings(settings))
            state.update { it.copy(savingPermissionIds = it.savingPermissionIds - machineId) }
            _events.emit(if (saved) CodeExecutionEvent.PermissionsSaved else CodeExecutionEvent.PermissionsFailed)
            if (saved) load()
        }
    }

    fun requestRemoval(machineId: String) =
        state.update { s -> s.copy(pendingRemoval = uiState.value.machines.firstOrNull { it.id == machineId }) }

    fun dismissRemoval() = state.update { it.copy(pendingRemoval = null) }

    fun confirmRemoval() {
        val machine = state.value.pendingRemoval ?: return
        state.update { it.copy(pendingRemoval = null, removingId = machine.id) }
        viewModelScope.launch {
            val error = machines.remove(machine.id)
            state.update { it.copy(removingId = null) }
            _events.emit(error?.let(CodeExecutionEvent::RemoveFailed) ?: CodeExecutionEvent.Removed)
            load()
        }
    }

    fun setSetupLink(text: String) = state.update { it.copy(setupLink = text, setupLinkInvalid = false) }

    /** Opens the worker's setup link (or its bare id) on the approval page. */
    fun openSetupLink() {
        val id = CodeSetupAttempt.idFrom(state.value.setupLink)
        if (id == null) {
            state.update { it.copy(setupLinkInvalid = true) }
            return
        }
        state.update { it.copy(setupLink = "") }
        _events.tryEmit(CodeExecutionEvent.OpenSetup(id))
    }

    private companion object {
        const val POLL_MS = 10_000L
        const val PAIR_NAME_MAX = 100
    }
}
