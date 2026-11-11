package com.garfiec.librechat.core.notifications

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.common.lifecycle.ForegroundSignal
import com.garfiec.librechat.core.common.result.getOrNull
import com.garfiec.librechat.core.data.datastore.ScheduleWatchList
import com.garfiec.librechat.core.data.datastore.WatchedSchedule
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ScheduleRepository
import com.garfiec.librechat.core.model.config.isSchedulesEnabled
import com.garfiec.librechat.core.model.schedule.Schedule
import com.garfiec.librechat.core.model.schedule.ScheduleRunStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Wakes the app to check the schedules. One pending wake-up: a newer one replaces it. */
interface ScheduleCheckScheduler {
    /**
     * Wakes the app at [at], or at no time when null. Leaves a check that is running alone: the job
     * re-arms from [ScheduledRunWatcher.check]'s result when it ends.
     */
    suspend fun arm(at: Instant?)
}

/** A check this long after a schedule's run time, so the server has started it. */
internal val AFTER_DUE: Duration = 1.minutes

/** How often a run that hasn't settled yet is checked again. */
internal val SETTLE_POLL: Duration = 2.minutes

/** A run not settled after this long is left alone: it is not one the user is waiting on. */
internal val SETTLE_WINDOW: Duration = 30.minutes

/**
 * Announces scheduled chats' runs, and the server pausing a schedule, while the app isn't open on
 * them. Reads only the server's own `nextRunAt`, never a time computed here: the server applies the
 * timezone and the cadence, and a local one drifts across DST and edits made on the web.
 *
 * A run is new only against [ScheduleWatchList], which every successful list rewrites, so a run the
 * user has already seen on the Schedules screen is never announced.
 */
class ScheduledRunWatcher(
    private val scheduleRepository: ScheduleRepository,
    private val watchList: ScheduleWatchList,
    private val configRepository: ConfigRepository,
    private val accounts: ActiveAccountProvider,
    private val replyReader: ReplyReader,
    private val notifications: ReplyNotifications,
    private val scheduler: ScheduleCheckScheduler,
    foregroundSignal: ForegroundSignal,
    scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) {
    init {
        watchList.observe()
            .onEach { list -> scheduler.arm(nextScheduleCheck(list, clock.now())) }
            .launchIn(scope)

        // Coming to the front checks: a schedule made on the web is watched from then on, and a run
        // that ended while the app was closed is shown in the app rather than only marked seen.
        combine(
            foregroundSignal.isForeground,
            configRepository.startupConfig.map { isSchedulesEnabled(it?.interfaceConfig?.schedules) },
            accounts.state.map { (it as? AccountState.Resolved)?.id },
        ) { foreground, enabled, account -> account.takeIf { foreground && enabled } }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach { check() }
            .launchIn(scope)
    }

    /** Announces what changed since the stored list, and returns when to check next (null: never). */
    suspend fun check(): Instant? {
        // A process started just for this check is still loading the account.
        accounts.state.first { it !is AccountState.Warming }
        val account = accounts.currentAccountId() ?: return null
        val config = configRepository.startupConfig.value ?: configRepository.fetchStartupConfig().getOrNull()
        if (config != null && !isSchedulesEnabled(config.interfaceConfig?.schedules)) {
            watchList.clear(account)
            return null
        }
        if (config != null) {
            val before = watchList.snapshot(account)
            val after = scheduleRepository.listSchedules().getOrNull()
            if (after != null && accounts.currentAccountId() == account) {
                scheduledRunNotices(before, after.schedules, account).forEach { announce(it) }
            }
        }
        // Offline, the stored list still says a run is due, which checks again soon.
        val current = accounts.currentAccountId() ?: return null
        return nextScheduleCheck(watchList.snapshot(current), clock.now())
    }

    private suspend fun announce(notice: ScheduledRunNotification) {
        val conversationId = notice.conversationId
        val withPreview = if (notice.outcome == ScheduledRunOutcome.Finished && conversationId != null) {
            notice.copy(preview = replyReader.read(conversationId, notice.accountId).preview)
        } else {
            notice
        }
        notifications.announce(withPreview)
    }
}

/**
 * What changed between the schedules the app last saw and [after]: each new settled or paused run,
 * and each schedule the server turned off (which outranks its run). A schedule seen for the first
 * time is only recorded, so the first list never announces its history.
 */
internal fun scheduledRunNotices(
    before: List<WatchedSchedule>,
    after: List<Schedule>,
    accountId: AccountId,
): List<ScheduledRunNotification> {
    val seen = before.associateBy { it.id }
    return after.mapNotNull { schedule ->
        val last = seen[schedule.id] ?: return@mapNotNull null
        if (last.enabled && !schedule.enabled && schedule.disabledReason != null) {
            return@mapNotNull ScheduledRunNotification(
                accountId = accountId,
                scheduleId = schedule.id,
                name = schedule.name,
                conversationId = null,
                outcome = ScheduledRunOutcome.Paused,
            )
        }
        val run = schedule.lastRun ?: return@mapNotNull null
        val firedAt = run.firedAt ?: return@mapNotNull null
        // A run paused for approval and then finished keeps its firedAt: the status is what moved.
        if (firedAt == last.lastRunFiredAt && run.status == last.lastRunStatus) return@mapNotNull null
        val outcome = when (run.status) {
            ScheduleRunStatus.SUCCESS -> ScheduledRunOutcome.Finished
            ScheduleRunStatus.ERROR, ScheduleRunStatus.INTERRUPTED -> ScheduledRunOutcome.Failed
            ScheduleRunStatus.REQUIRES_ACTION -> ScheduledRunOutcome.NeedsApproval
            ScheduleRunStatus.SKIPPED_BALANCE -> ScheduledRunOutcome.SkippedForBalance
            else -> return@mapNotNull null
        }
        ScheduledRunNotification(
            accountId = accountId,
            scheduleId = schedule.id,
            name = schedule.name,
            // A run that fails or is skipped before generating has no conversation to open; the
            // Schedules list explains it either way.
            conversationId = run.conversationId.takeIf { outcome in OPENS_CHAT },
            outcome = outcome,
        )
    }
}

private val OPENS_CHAT = setOf(ScheduledRunOutcome.Finished, ScheduledRunOutcome.NeedsApproval)

/**
 * When to check next: soon while a run is generating or due but not started (within
 * [SETTLE_WINDOW]), else [AFTER_DUE] past the earliest enabled schedule's `nextRunAt`. Null when no
 * schedule will run.
 */
internal fun nextScheduleCheck(schedules: List<WatchedSchedule>, now: Instant): Instant? =
    schedules.filter { it.enabled }.mapNotNull { schedule ->
        val runningSince = schedule.runningSinceMillis?.let(Instant::fromEpochMilliseconds)
        val due = schedule.nextRunAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        when {
            runningSince != null && now - runningSince < SETTLE_WINDOW -> now + SETTLE_POLL
            due == null -> null
            due > now -> due + AFTER_DUE
            now - due < SETTLE_WINDOW -> now + SETTLE_POLL
            else -> null
        }
    }.minOrNull()
