package com.garfiec.librechat.feature.schedules.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.schedule.Schedule
import com.garfiec.librechat.core.model.schedule.ScheduleLimits
import com.garfiec.librechat.core.model.schedule.ScheduleMcpConsentState
import com.garfiec.librechat.core.model.schedule.ScheduleMcpConsentView

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
