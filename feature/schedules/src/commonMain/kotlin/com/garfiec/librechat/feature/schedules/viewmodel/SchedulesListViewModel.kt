package com.garfiec.librechat.feature.schedules.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.ScheduleRepository
import com.garfiec.librechat.core.model.config.isSchedulesEnabled
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.hasAccessStrictOrDenied
import com.garfiec.librechat.core.model.schedule.Schedule
import com.garfiec.librechat.core.model.schedule.ScheduleLimits
import com.garfiec.librechat.core.model.schedule.UpdateScheduleRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Immutable
data class SchedulesListUiState(
    val schedules: List<Schedule> = emptyList(),
    val limits: ScheduleLimits = ScheduleLimits(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    /**
     * Fail-CLOSED: only true when SCHEDULES.CREATE is explicitly granted. Create, edit, delete and
     * run-now are all gated on it, matching the routes — every write requires USE **and** CREATE.
     */
    val canCreate: Boolean = false,
    /** The schedule a write is in flight for, so one row can show progress without locking the list. */
    val busyScheduleId: String? = null,
    /** True once the server has said this deployment does not have the feature on at all. */
    val isDisabledOnServer: Boolean = false,
) {
    val isAtLimit: Boolean
        get() = limits.maxPerUser > 0 && schedules.size >= limits.maxPerUser
}

class SchedulesListViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val roleRepository: RoleRepository,
    private val configRepository: ConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SchedulesListUiState())
    val uiState: StateFlow<SchedulesListUiState> = _uiState.asStateFlow()

    init {
        observeGates()
    }

    /**
     * Both halves of the gate, together.
     *
     * The permission half is permissive on USE and fail-closed on CREATE, matching this codebase's
     * convention and the route split. The config half is fail-CLOSED and is the real feature
     * switch, which is what makes the permissive USE safe: a server that has not opted in cannot
     * be opened up by a missing permission key.
     */
    private fun observeGates() {
        viewModelScope.launch {
            combine(
                roleRepository.userPermissions,
                configRepository.startupConfig.map { isSchedulesEnabled(it?.interfaceConfig?.schedules) },
            ) { role, enabled ->
                enabled to role.hasAccessStrictOrDenied(PermissionType.SCHEDULES, Permission.CREATE)
            }.collect { (enabled, canCreate) ->
                _uiState.value = _uiState.value.copy(
                    canCreate = canCreate,
                    isDisabledOnServer = !enabled,
                )
            }
        }
    }

    /**
     * Reloads the list.
     *
     * Driven by the screen's resume effect rather than from `init`: Nav3 retains this ViewModel, so
     * a schedule created in the editor would not show on return otherwise. Everything on a card —
     * `nextRunAt`, `lastRun`, `inFlight` — moves without this client acting, which is also why
     * nothing here is cached.
     */
    fun refresh(isUserInitiated: Boolean = false) {
        if (_uiState.value.isLoading || _uiState.value.isRefreshing) return
        _uiState.value = _uiState.value.copy(
            isLoading = !isUserInitiated && _uiState.value.schedules.isEmpty(),
            isRefreshing = isUserInitiated,
            error = null,
        )
        viewModelScope.launch {
            when (val result = scheduleRepository.listSchedules()) {
                is Result.Success -> _uiState.value = _uiState.value.copy(
                    schedules = result.data.schedules,
                    limits = result.data.limits,
                    isLoading = false,
                    isRefreshing = false,
                )
                is Result.Error -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = result.message,
                )
                is Result.Loading -> Unit
            }
        }
    }

    /**
     * Pauses or resumes a schedule.
     *
     * Builds the body directly rather than round-tripping through [ScheduleDraft]: the draft is
     * the EDITOR's model, and its `cadence` substitutes `hour ?: 0` / `minute ?: 0` for a time it
     * cannot represent. A cadence arm this build does not know — one with no hour/minute, or with
     * a field `ScheduleCadence` does not model — therefore comes back out of the form differing
     * from the original, and [diffAgainst] would include it, silently rewriting when the schedule
     * runs. Only the cron arm survives that round trip intact, so only cron was ever safe.
     *
     * `enabled` alone, with the revision fence, has nothing to round-trip and is safe for every
     * arm — present and future.
     */
    fun setEnabled(schedule: Schedule, enabled: Boolean) {
        if (!_uiState.value.canCreate) return
        write(schedule.id) {
            scheduleRepository.updateSchedule(
                schedule.id,
                UpdateScheduleRequest(
                    enabled = enabled,
                    expectedConfigRevision = schedule.configRevision,
                ),
            ).map { }
        }
    }

    fun runNow(schedule: Schedule, onStarted: (conversationId: String) -> Unit) {
        if (!_uiState.value.canCreate) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busyScheduleId = schedule.id, error = null)
            val result = scheduleRepository.runScheduleNow(schedule.id)
            when (result) {
                is Result.Success -> onStarted(result.data.conversationId)
                is Result.Error -> _uiState.value = _uiState.value.copy(error = result.message)
                is Result.Loading -> Unit
            }
            refreshAndSettle()
        }
    }

    fun delete(schedule: Schedule) {
        if (!_uiState.value.canCreate) return
        write(schedule.id) { scheduleRepository.deleteSchedule(schedule.id) }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun write(scheduleId: String, block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busyScheduleId = scheduleId, error = null)
            val result = block()
            if (result is Result.Error) {
                _uiState.value = _uiState.value.copy(error = result.message)
            }
            // Refreshed either way: a refused write leaves the server's copy authoritative, and a
            // successful one moves fields (nextRunAt, disabledReason) this client cannot derive.
            // The row stays busy ACROSS the refresh — clearing first makes it flash settled and
            // then busy again while the list reloads underneath it.
            refreshAndSettle()
        }
    }

    /** Re-reads the list and clears the busy row in ONE emission, so nothing flashes between. */
    private suspend fun refreshAndSettle() {
        val result = scheduleRepository.listSchedules()
        _uiState.value = when (result) {
            is Result.Success -> _uiState.value.copy(
                schedules = result.data.schedules,
                limits = result.data.limits,
                busyScheduleId = null,
            )
            // A failed reload is not worth reporting over the write's own outcome; the list is
            // simply left as it was and the next resume re-reads it.
            else -> _uiState.value.copy(busyScheduleId = null)
        }
    }

    private fun <T> Result<T>.map(transform: (T) -> Unit): Result<Unit> = when (this) {
        is Result.Success -> Result.Success(transform(data))
        is Result.Error -> this
        is Result.Loading -> Result.Loading
    }
}
