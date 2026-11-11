package com.garfiec.librechat.core.notifications

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.dateWithTimeIntervalSince1970
import kotlin.concurrent.Volatile
import kotlin.time.Instant

/** The schedule check's background task, as `ScheduleCheckTask.swift` registers it. */
object IosScheduleCheckTask {
    /** Must be listed in Info.plist's `BGTaskSchedulerPermittedIdentifiers`. */
    const val ID: String = "com.garfiec.librechat.schedules.check"

    /** Under the ~30 seconds iOS gives a refresh task, leaving room to report completion. */
    const val BUDGET_SECONDS: Double = 22.0

    /**
     * Set once the Swift launch handler is installed. Submitting an identifier with no handler is an
     * uncatchable exception, so nothing is submitted before this.
     */
    @Volatile
    var launchHandlerRegistered: Boolean = false
        private set

    fun markLaunchHandlerRegistered() {
        launchHandlerRegistered = true
    }
}

/**
 * Best effort: a background app refresh task that iOS may start any time after [arm]'s time,
 * or never (Background App Refresh off, Low Power Mode, an app the user rarely opens). Reliable
 * delivery would need a push from the server.
 */
class IosScheduleCheckScheduler : ScheduleCheckScheduler {

    private val scheduler get() = BGTaskScheduler.sharedScheduler

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun arm(at: Instant?) {
        if (at == null) {
            scheduler.cancelTaskRequestWithIdentifier(IosScheduleCheckTask.ID)
            return
        }
        if (!IosScheduleCheckTask.launchHandlerRegistered) return
        // A new request replaces the pending one; a task that is running carries on.
        val request = BGAppRefreshTaskRequest(IosScheduleCheckTask.ID).apply {
            earliestBeginDate = NSDate.dateWithTimeIntervalSince1970(at.toEpochMilliseconds() / 1000.0)
        }
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            if (!scheduler.submitTaskRequest(request, error.ptr)) {
                Logger.w { "Schedule check not scheduled: ${error.value?.localizedDescription}" }
            }
        }
    }
}
