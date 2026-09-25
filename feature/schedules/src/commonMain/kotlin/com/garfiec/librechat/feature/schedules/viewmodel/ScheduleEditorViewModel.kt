package com.garfiec.librechat.feature.schedules.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.ProjectRepository
import com.garfiec.librechat.core.data.repository.ScheduleRepository
import com.garfiec.librechat.core.model.ChatProject
import com.garfiec.librechat.core.model.schedule.Schedule
import com.garfiec.librechat.core.model.schedule.ScheduleLimits
import com.garfiec.librechat.core.model.schedule.cadenceToCron
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Immutable
data class AgentChoice(val id: String, val name: String)

@Immutable
data class ScheduleEditorUiState(
    val draft: ScheduleDraft = ScheduleDraft(),
    val limits: ScheduleLimits = ScheduleLimits(),
    val agents: List<AgentChoice> = emptyList(),
    val projects: List<ChatProject> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedScheduleId: String? = null,
    /** Set when the server reports a concurrent edit; the form must be reloaded, never retried. */
    val hasConflict: Boolean = false,
    val isEditing: Boolean = false,
) {
    /** The cron line the engine would fire from, for the structured picker's preview. */
    val cadencePreview: String get() = cadenceToCron(draft.cadence)

    val problem: ScheduleDraftProblem?
        get() = draft.firstProblem(limits, hasNoAgents = !isLoading && agents.isEmpty())

    /** A pinned deployment supplies the destination itself, so no picker is offered. */
    val isProjectPinned: Boolean get() = limits.projectId != null

    /** `requireProject` with nothing to choose: the save can never succeed until one exists. */
    val needsAProjectToExist: Boolean
        get() = limits.requireProject && !isProjectPinned && projects.isEmpty()

    val canSave: Boolean get() = !isSaving && !hasConflict && problem == null
}

class ScheduleEditorViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val agentRepository: AgentRepository,
    private val projectRepository: ProjectRepository,
    private val scheduleId: String?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleEditorUiState(isEditing = scheduleId != null))
    val uiState: StateFlow<ScheduleEditorUiState> = _uiState.asStateFlow()

    /** The row the draft was computed from. The diff and the 409 fence are both taken from it. */
    private var original: Schedule? = null

    /**
     * Stable for the life of this editor, not per attempt.
     *
     * Creation commits the row and arms it in two writes, so a failure between them leaves this
     * client unable to tell whether anything persisted — a retry with a fresh key would then make
     * a second recurring schedule. Minted once here so a retried Save resolves to the same row.
     */
    @OptIn(ExperimentalUuidApi::class)
    private val clientRequestId: String = Uuid.random().toString()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            // Three independent reads, so they go out together: the editor is blocked on a spinner
            // until all of them land, and serialising them made that wait the sum rather than the
            // slowest. The limits ride on the LIST, not on the single-schedule route, so an editor
            // opened straight from a deep link still has to read the list to know its own policy.
            val listingAsync = async { scheduleRepository.listSchedules() }
            val existingAsync = async { scheduleId?.let { scheduleRepository.getSchedule(it) } }
            val agentsAsync = async { loadAgents() }

            val listing = listingAsync.await()
            val limits = (listing as? Result.Success)?.data?.limits ?: ScheduleLimits()
            val existing = existingAsync.await()
            val schedule = (existing as? Result.Success)?.data
            original = schedule

            val agents = agentsAsync.await()
            // Sequenced deliberately: a pinned deployment picks its own destination, so there is
            // nothing to choose and the request is not worth making.
            val projects = if (limits.projectId == null) loadProjects() else emptyList()

            _uiState.value = _uiState.value.copy(
                draft = schedule?.let(ScheduleDraft::from)
                    ?: ScheduleDraft(
                        timezone = TimeZone.currentSystemDefault().id,
                        agentId = agents.firstOrNull()?.id.orEmpty(),
                    ),
                limits = limits,
                agents = agents,
                projects = projects,
                isLoading = false,
                error = (existing as? Result.Error)?.message
                    ?: (listing as? Result.Error)?.message,
            )
        }
    }

    private suspend fun loadAgents(): List<AgentChoice> =
        when (val result = agentRepository.getAgents()) {
            is Result.Success -> result.data.map { AgentChoice(it.id, it.name ?: it.id) }
            else -> emptyList()
        }

    private suspend fun loadProjects(): List<ChatProject> =
        when (val result = projectRepository.listProjects()) {
            is Result.Success -> result.data.projects
            else -> emptyList()
        }

    fun update(transform: (ScheduleDraft) -> ScheduleDraft) {
        _uiState.value = _uiState.value.copy(draft = transform(_uiState.value.draft), error = null)
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.value = state.copy(isSaving = true, error = null)
        viewModelScope.launch {
            val existing = original
            val result = if (existing == null) {
                scheduleRepository.createSchedule(state.draft.toCreateRequest(clientRequestId))
            } else {
                scheduleRepository.updateSchedule(existing.id, state.draft.diffAgainst(existing))
            }
            when (result) {
                is Result.Success -> _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    savedScheduleId = result.data.id,
                )
                is Result.Error -> _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = result.message,
                    // A concurrent edit elsewhere. Retrying would send the same stale revision
                    // again, and the payload carries a whole cadence — so the form is frozen
                    // until it is reloaded rather than offering a Save that cannot succeed.
                    hasConflict = result.isConcurrentEdit(),
                )
                is Result.Loading -> Unit
            }
        }
    }

    /** Discards the local edit and re-reads the server's copy after a 409. */
    fun reloadAfterConflict() {
        _uiState.value = _uiState.value.copy(hasConflict = false, isLoading = true, error = null)
        load()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun Result.Error.isConcurrentEdit(): Boolean =
        (exception as? ApiException)?.statusCode == HTTP_CONFLICT

    private companion object {
        const val HTTP_CONFLICT = 409
    }
}
