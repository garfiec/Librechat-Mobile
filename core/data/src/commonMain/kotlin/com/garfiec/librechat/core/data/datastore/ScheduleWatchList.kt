package com.garfiec.librechat.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.model.schedule.Schedule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * One schedule as the app last saw it, for notifying about runs while it isn't open. Never shown:
 * the Schedules screen always reads the server.
 */
@Serializable
data class WatchedSchedule(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val disabledReason: String? = null,
    /** The server's own next fire time (ISO-8601), never one computed here. */
    val nextRunAt: String? = null,
    val lastRunFiredAt: String? = null,
    val lastRunStatus: String? = null,
    /** Epoch millis when a run was first seen generating; null while none is. */
    val runningSinceMillis: Long? = null,
)

/**
 * The active account's schedules as of the last successful list. Every list is the app having seen
 * those runs, so a later run counts as new only against what is stored here.
 *
 * Keyed by account (`acct:<id>:…`), so sign-out's purge clears it and each account keeps its own.
 */
class ScheduleWatchList(
    private val dataStore: DataStore<Preferences>,
    private val accounts: ActiveAccountProvider,
    private val clock: Clock = Clock.System,
) {

    /** The active account's list; nothing while the account is still loading. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<List<WatchedSchedule>> = accounts.state
        .filter { it !is AccountState.Warming }
        .flatMapLatest { state ->
            val id = (state as? AccountState.Resolved)?.id ?: return@flatMapLatest flowOf(emptyList())
            dataStore.data.map { decode(it[accountScopedKey(id.value, BASE)]) }
        }
        .distinctUntilChanged()

    suspend fun snapshot(accountId: AccountId): List<WatchedSchedule> = bestEffortCache({
        Logger.w(it) { "Failed to read the schedule watch list" }
        emptyList()
    }) {
        decode(dataStore.data.first()[accountScopedKey(accountId.value, BASE)])
    }

    suspend fun replace(accountId: AccountId, schedules: List<Schedule>) {
        val key = accountScopedKey(accountId.value, BASE)
        val now = clock.now().toEpochMilliseconds()
        withContext(NonCancellable) {
            bestEffortCache({ Logger.w(it) { "Failed to store the schedule watch list" } }) {
                dataStore.edit { prefs ->
                    val before = decode(prefs[key]).associateBy { it.id }
                    prefs[key] = json.encodeToString(schedules.map { it.watched(before[it.id], now) })
                }
            }
        }
    }

    suspend fun clear(accountId: AccountId) {
        withContext(NonCancellable) {
            bestEffortCache({ Logger.w(it) { "Failed to clear the schedule watch list" } }) {
                dataStore.edit { it.remove(accountScopedKey(accountId.value, BASE)) }
            }
        }
    }

    private fun decode(raw: String?): List<WatchedSchedule> =
        raw?.let { runCatching { json.decodeFromString<List<WatchedSchedule>>(it) }.getOrNull() } ?: emptyList()

    private companion object {
        const val BASE = "schedule_watch_list"
        val json = Json { ignoreUnknownKeys = true }
    }
}

private fun Schedule.watched(before: WatchedSchedule?, nowMillis: Long) = WatchedSchedule(
    id = id,
    name = name,
    enabled = enabled,
    disabledReason = disabledReason,
    nextRunAt = nextRunAt,
    lastRunFiredAt = lastRun?.firedAt,
    lastRunStatus = lastRun?.status,
    runningSinceMillis = if (inFlight.isNullOrEmpty()) null else before?.runningSinceMillis ?: nowMillis,
)
