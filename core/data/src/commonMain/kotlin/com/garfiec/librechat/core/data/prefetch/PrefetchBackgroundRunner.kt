package com.garfiec.librechat.core.data.prefetch

import com.garfiec.librechat.core.common.identity.SessionManager
import com.garfiec.librechat.core.common.lifecycle.DeferredWorkWindow
import com.garfiec.librechat.core.data.datastore.AppPreferences
import com.garfiec.librechat.core.data.datastore.PreferenceStore
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.logging.Diag
import com.garfiec.librechat.core.model.prefetch.PrefetchRunOutcome
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** The last scheduled run, as the readout presents it. Account-scoped — see [SettingsDataStore.lastScheduledRun]. */
data class ScheduledRunRecord(val atMillis: Long, val outcome: PrefetchRunOutcome)

/**
 * Runs one prefetch pass on behalf of a platform scheduler, within a budget.
 *
 * It does **not** drive the engine directly. [PrefetchController]'s single collector is the only
 * thing serializing passes — the engine has no locking of its own — so this waits on that collector
 * instead of starting a second pass beside it.
 *
 * The subtlety is that opening the window is *itself* a trigger. In a process the scheduler spawned,
 * nothing has marked the UI started, so [DeferredWorkWindow.beginBackgroundRun] is what opens the
 * gate, and the gate's rising edge starts a pass with no request from anyone. Asking for one as well
 * would run two full passes back to back. So this waits for a pass to appear first, and only asks
 * when none does — which is the already-running-and-idle process, where there is no edge to ride.
 */
class PrefetchBackgroundRunner(
    private val sessionManager: SessionManager,
    private val controller: PrefetchController,
    private val engine: PrefetchEngine,
    private val deferredWorkWindow: DeferredWorkWindow,
    private val settingsDataStore: SettingsDataStore,
    private val preferenceStore: PreferenceStore,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {

    suspend fun runOnce(budget: Duration): PrefetchRunOutcome {
        // Seeded so a run the platform cancels still records something: WorkManager drops the worker
        // the moment a constraint is lost, and a cancelled call never produces a return value.
        var outcome = PrefetchRunOutcome.INTERRUPTED
        try {
            outcome = runPass(budget)
            return outcome
        } finally {
            // Record every outcome, including the ones that warmed nothing — those are invisible
            // everywhere else, since no watermark moves and the process is gone by the time anyone looks.
            withContext(NonCancellable) {
                settingsDataStore.recordScheduledRun(recordedAccountId, ScheduledRunRecord(nowMillis(), outcome))
            }
        }
    }

    private var recordedAccountId: String? = null

    private suspend fun runPass(budget: Duration): PrefetchRunOutcome {
        if (!preferenceStore.observe(AppPreferences.PrefetchEnabled).first()) return PrefetchRunOutcome.DISABLED

        // One deadline for the whole call, armed before any waiting: not one per attempt (the retry
        // below would re-arm the full budget), and covering the handshake, which can spend
        // SESSION_WAIT plus two PASS_START_GRACEs — the whole allowance of an iOS refresh task.
        val deadline = timeSource.markNow() + budget
        deferredWorkWindow.beginBackgroundRun()
        try {
            val session = withTimeoutOrNull(deadline.remainingCappedTo(SESSION_WAIT)) {
                sessionManager.current.filterNotNull().first()
            } ?: return PrefetchRunOutcome.NO_SESSION

            val accountId = session.accountId.value
            recordedAccountId = accountId
            // The window opening is itself a trigger, so only ask when nothing started on its own.
            if (!awaitPassStart(deadline) && !requestAndAwaitStart(deadline)) {
                return PrefetchRunOutcome.CONSTRAINTS_UNMET
            }

            var outcome = awaitPassEnd(deadline, accountId)
            if (outcome == PrefetchRunOutcome.STOPPED && requestAndAwaitStart(deadline)) {
                // One retry with the breaker cleared. Beyond that the server really is not answering,
                // and the next scheduled run is hours away — a better time to try than now.
                outcome = awaitPassEnd(deadline, accountId)
            }
            Diag.d("Prefetch", attrs = mapOf("outcome" to outcome.name)) { "scheduled run finished" }
            return outcome
        } finally {
            deferredWorkWindow.endBackgroundRun()
        }
    }

    /**
     * Waits briefly for a pass to be under way, or for one to have completed while we were not
     * looking — [PrefetchController.passInProgress] conflates, so a short pass can begin and end
     * between two reads of it and would otherwise be reported as never having started.
     */
    private suspend fun awaitPassStart(deadline: TimeMark): Boolean {
        val before = controller.completedPasses.value
        return withTimeoutOrNull(deadline.remainingCappedTo(PASS_START_GRACE)) {
            combine(controller.passInProgress, controller.completedPasses) { running, completed ->
                running || completed != before
            }.first { it }
        } != null
    }

    private suspend fun requestAndAwaitStart(deadline: TimeMark): Boolean {
        controller.requestScheduledRun()
        return awaitPassStart(deadline)
    }

    /**
     * How long is left, never more than [cap] and never negative — a negative timeout makes
     * `withTimeoutOrNull` fail instantly, indistinguishably from the condition genuinely not being met.
     */
    private fun TimeMark.remainingCappedTo(cap: Duration): Duration =
        minOf(cap, -elapsedNow()).coerceAtLeast(Duration.ZERO)

    private suspend fun awaitPassEnd(deadline: TimeMark, accountId: String): PrefetchRunOutcome {
        val remaining = -deadline.elapsedNow()
        if (remaining <= Duration.ZERO) return PrefetchRunOutcome.BUDGET_EXPIRED
        val finished = withTimeoutOrNull(remaining) { controller.passInProgress.first { !it } } != null
        if (!finished) return PrefetchRunOutcome.BUDGET_EXPIRED
        return if (engine.runState.value.stateFor(accountId) == PrefetchRunState.Stopped) {
            PrefetchRunOutcome.STOPPED
        } else {
            PrefetchRunOutcome.COMPLETED
        }
    }

    private companion object {
        /** Long enough for a cold process to read the account registry and form a session. */
        val SESSION_WAIT = 15.seconds
        val PASS_START_GRACE = 5.seconds
    }
}
