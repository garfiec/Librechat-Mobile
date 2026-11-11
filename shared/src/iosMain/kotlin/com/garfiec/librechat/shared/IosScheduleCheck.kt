package com.garfiec.librechat.shared

import com.garfiec.librechat.core.notifications.IosScheduleCheckTask
import com.garfiec.librechat.core.notifications.ScheduleCheckScheduler
import com.garfiec.librechat.core.notifications.ScheduledRunWatcher
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/** Swift's handle on the schedule check's background task (`ScheduleCheckTask.swift`). */
object IosScheduleCheck {
    const val ID: String = IosScheduleCheckTask.ID
    const val BUDGET_SECONDS: Double = IosScheduleCheckTask.BUDGET_SECONDS

    fun markLaunchHandlerRegistered() = IosScheduleCheckTask.markLaunchHandlerRegistered()

    /**
     * Runs one check and submits the next, returning whether it finished inside [budgetSeconds],
     * which the task reports to iOS. Out of time, it submits the check the stored list calls for.
     */
    @Throws(Exception::class)
    suspend fun run(budgetSeconds: Double): Boolean {
        val koin = IosKoinAccessor.koin
        val scheduler = koin.get<ScheduleCheckScheduler>()
        val watcher = koin.get<ScheduledRunWatcher>()
        val finished = withTimeoutOrNull(budgetSeconds.seconds) {
            scheduler.arm(watcher.check())
            true
        } ?: false
        if (!finished) watcher.fallbackCheck()?.let { scheduler.arm(it) }
        return finished
    }
}
