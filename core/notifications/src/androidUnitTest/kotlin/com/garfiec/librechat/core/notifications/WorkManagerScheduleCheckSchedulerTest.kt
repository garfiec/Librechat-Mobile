package com.garfiec.librechat.core.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkManagerScheduleCheckSchedulerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var scheduler: WorkManagerScheduleCheckScheduler

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        scheduler = WorkManagerScheduleCheckScheduler(context)
    }

    private fun checks(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork("schedule-check").get()

    @Test
    fun anOverdueCheck_survivesAStartThatFindsNothingDue() = runTest {
        // Due now and waiting on its network constraint: queued, about to run.
        scheduler.arm(Clock.System.now())
        Thread.sleep(5)

        scheduler.arm(null)

        assertEquals(WorkInfo.State.ENQUEUED, checks().single().state)
    }

    @Test
    fun aLaterCheck_isCancelledWhenNothingIsDue() = runTest {
        scheduler.arm(Clock.System.now() + 1.hours)

        scheduler.arm(null)

        assertTrue(checks().all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun anEarlierTime_replacesALaterCheck_butALaterOneKeepsIt() = runTest {
        scheduler.arm(Clock.System.now() + 2.hours)
        val first = checks().single().id

        scheduler.arm(Clock.System.now() + 3.hours)
        assertEquals(first, checks().single { it.state == WorkInfo.State.ENQUEUED }.id)

        scheduler.arm(Clock.System.now() + 1.hours)
        assertTrue(checks().single { it.state == WorkInfo.State.ENQUEUED }.id != first)
    }
}
