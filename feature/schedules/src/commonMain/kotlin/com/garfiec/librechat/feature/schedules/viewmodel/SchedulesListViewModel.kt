package com.garfiec.librechat.feature.schedules.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.BackendVersion
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
import com.garfiec.librechat.core.model.schedule.ScheduleMcpConsentState
import com.garfiec.librechat.core.model.schedule.ScheduleMcpConsentView
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
    /** The server has the MCP consent routes (v0.8.9-rc1). Unknown version: false. */
    val consentRoutesAvailable: Boolean = false,
    val consent: ScheduleConsentSheetState? = null,
) {
    val isAtLimit: Boolean
        get() = limits.maxPerUser > 0 && schedules.size >= limits.maxPerUser

    /**
     * Whether [schedule] offers the MCP consent sheet: an rc1+ server that asks for consent on this
     * deployment, or a schedule that already carries one (web `canInspectConsent`). Anyone who can
     * see the schedule may inspect it; only Confirm needs CREATE.
     */
    fun offersConsent(schedule: Schedule): Boolean =
        consentRoutesAvailable && (limits.mcpConsent == true || schedule.hasMcpConsent == true)
}

/** What the last consent write did, shown in the sheet under the controls. */
enum class ConsentMessage { CONFIRMED, REVOKED, FAILED }

/** The open MCP consent sheet for one schedule. */
@Immutable
data class ScheduleConsentSheetState(
    val scheduleId: String,
    val scheduleName: String,
    /** USE + CREATE, as the confirm route requires; inspecting and revoking need neither. */
    val canConfirm: Boolean,
    val view: ScheduleMcpConsentView? = null,
    val isLoading: Boolean = true,
    /** The view could not be read: the sheet offers Retry. */
    val loadFailed: Boolean = false,
    /** A confirm or revoke is in flight. */
    val isWriting: Boolean = false,
    /** As typed; web's default is 1 hour. */
    val hoursText: String = DEFAULT_CONSENT_HOURS.toString(),
    val message: ConsentMessage? = null,
) {
    val maxLifetimeHours: Int? get() = view?.offer?.maxLifetimeHours

    /** The typed hours, when they are a whole number the offer allows. */
    val validHours: Int?
        get() {
            val max = maxLifetimeHours ?: return null
            return hoursText.trim().toIntOrNull()?.takeIf { it in 1..max }
        }

    val showsConfirm: Boolean get() = canConfirm && view?.offer != null

    val canSubmitConfirm: Boolean get() = showsConfirm && validHours != null && !isWriting && !isLoading

    /** Web parity: any viewer may revoke a recorded authorization that isn't already revoked. */
    val showsRevoke: Boolean
        get() = view?.revision != null && view.state != ScheduleMcpConsentState.REVOKED

    companion object {
        const val DEFAULT_CONSENT_HOURS = 1
    }
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
                // The consent routes are v0.8.9-rc1; schedules themselves are not version-gated.
                configRepository.detectedBackendVersion.map { version ->
                    version != null && BackendVersion.isCompatibleOrNewer(version, CONSENT_MIN_VERSION)
                },
            ) { role, enabled, consentRoutes ->
                Triple(enabled, role.hasAccessStrictOrDenied(PermissionType.SCHEDULES, Permission.CREATE), consentRoutes)
            }.collect { (enabled, canCreate, consentRoutes) ->
                _uiState.value = _uiState.value.copy(
                    canCreate = canCreate,
                    isDisabledOnServer = !enabled,
                    consentRoutesAvailable = consentRoutes,
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

    /** Opens the MCP consent sheet for [schedule] and reads its current state. */
    fun openConsent(schedule: Schedule) {
        if (!_uiState.value.offersConsent(schedule)) return
        _uiState.value = _uiState.value.copy(
            consent = ScheduleConsentSheetState(
                scheduleId = schedule.id,
                scheduleName = schedule.name,
                canConfirm = _uiState.value.canCreate,
            ),
        )
        loadConsent(schedule.id, keepMessage = false)
    }

    fun closeConsent() {
        _uiState.value = _uiState.value.copy(consent = null)
    }

    /** Retry after a failed read, and the reload after every write. */
    fun reloadConsent() {
        val consent = _uiState.value.consent ?: return
        loadConsent(consent.scheduleId, keepMessage = false)
    }

    fun onConsentHoursChanged(text: String) {
        updateConsent(_uiState.value.consent?.scheduleId ?: return) { it.copy(hoursText = text) }
    }

    fun confirmConsent() {
        val consent = _uiState.value.consent ?: return
        val offer = consent.view?.offer ?: return
        val hours = consent.validHours ?: return
        if (!consent.canSubmitConfirm) return
        writeConsent(consent.scheduleId, ConsentMessage.CONFIRMED) {
            scheduleRepository.confirmMcpConsent(
                id = consent.scheduleId,
                offerDigest = offer.digest,
                expectedRevision = consent.view.revision,
                lifetimeHours = hours,
            ).map { }
        }
    }

    fun revokeConsent() {
        val consent = _uiState.value.consent ?: return
        val revision = consent.view?.revision ?: return
        if (!consent.showsRevoke || consent.isWriting) return
        writeConsent(consent.scheduleId, ConsentMessage.REVOKED) {
            scheduleRepository.revokeMcpConsent(consent.scheduleId, revision)
        }
    }

    private fun writeConsent(scheduleId: String, success: ConsentMessage, block: suspend () -> Result<Unit>) {
        updateConsent(scheduleId) { it.copy(isWriting = true, message = null) }
        viewModelScope.launch {
            val result = block()
            val message = if (result is Result.Success) success else ConsentMessage.FAILED
            updateConsent(scheduleId) { it.copy(isWriting = false, message = message) }
            // Re-read either way: a refused write (a stale revision, a changed binding) leaves the
            // server's view authoritative, and a successful one moves its state and deadline.
            loadConsent(scheduleId, keepMessage = true)
            // The list's `hasMcpConsent` follows the enrollment.
            refreshAndSettle()
        }
    }

    private fun loadConsent(scheduleId: String, keepMessage: Boolean) {
        updateConsent(scheduleId) {
            it.copy(isLoading = true, loadFailed = false, message = if (keepMessage) it.message else null)
        }
        viewModelScope.launch {
            val result = scheduleRepository.getMcpConsent(scheduleId)
            updateConsent(scheduleId) {
                when (result) {
                    is Result.Success -> it.copy(view = result.data, isLoading = false)
                    is Result.Error -> it.copy(isLoading = false, loadFailed = it.view == null)
                    is Result.Loading -> it
                }
            }
        }
    }

    /** Applies [transform] only while the sheet for [scheduleId] is the one open, so a late answer can't land in another. */
    private fun updateConsent(scheduleId: String, transform: (ScheduleConsentSheetState) -> ScheduleConsentSheetState) {
        val current = _uiState.value.consent ?: return
        if (current.scheduleId != scheduleId) return
        _uiState.value = _uiState.value.copy(consent = transform(current))
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

    private companion object {
        const val CONSENT_MIN_VERSION = "0.8.9-rc1"
    }

    private fun <T> Result<T>.map(transform: (T) -> Unit): Result<Unit> = when (this) {
        is Result.Success -> Result.Success(transform(data))
        is Result.Error -> this
        is Result.Loading -> Result.Loading
    }
}
