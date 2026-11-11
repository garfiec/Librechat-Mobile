package com.garfiec.librechat.core.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

private const val WORK_NAME = "schedule-check"

/**
 * One WorkManager job at the next check time, no exact alarm: exact alarms need a permission
 * Android 14 denies by default and Play grants only to alarm apps, and a run takes minutes anyway.
 * In Doze the check can come late. WorkManager jobs survive a reboot without a boot receiver.
 */
class WorkManagerScheduleCheckScheduler(context: Context) : ScheduleCheckScheduler {
    private val workManager = WorkManager.getInstance(context)

    override suspend fun arm(at: Instant?) {
        val pending = workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME).first()
        // Replacing a running job cancels it; it re-arms itself when it ends.
        if (pending.any { it.state == WorkInfo.State.RUNNING }) return
        // A queued job that fires no later is kept: a process started for it would otherwise push
        // it back on every start. One that fires early only checks once more. An overdue one is kept
        // even when nothing is due: WorkManager may have started this process to run it, before it
        // reads as running, and it re-arms itself.
        val keepBy = maxOf(at?.toEpochMilliseconds() ?: 0L, nowMillis())
        val keep = pending.any { it.state == WorkInfo.State.ENQUEUED && it.nextScheduleTimeMillis <= keepBy }
        if (!keep) workManager.armScheduleCheck(at)
    }
}

private fun nowMillis() = Clock.System.now().toEpochMilliseconds()

/** Resolves from Koin like `ReplyWatchWorker`, and arms the next check as it ends. */
@Suppress("NoKoinComponentInterface", "NoInjectDelegate") // see KDoc
class ScheduleCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val watcher: ScheduledRunWatcher by inject()

    override suspend fun doWork(): Result {
        val next = watcher.check()
        // Replaces this job, which is done: nothing after this line runs on its behalf.
        WorkManager.getInstance(applicationContext).armScheduleCheck(next)
        return Result.success()
    }
}

private fun WorkManager.armScheduleCheck(at: Instant?) {
    if (at == null) {
        cancelUniqueWork(WORK_NAME)
        return
    }
    val delay = (at - Clock.System.now()).coerceAtLeast(Duration.ZERO)
    val request = OneTimeWorkRequestBuilder<ScheduleCheckWorker>()
        .setInitialDelay(delay.inWholeSeconds, TimeUnit.SECONDS)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
}
