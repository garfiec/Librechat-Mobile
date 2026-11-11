package com.garfiec.librechat.core.notifications

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.lifecycle.ForegroundSignal
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ScheduleWatchList
import com.garfiec.librechat.core.data.datastore.WatchedSchedule
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ScheduleRepository
import com.garfiec.librechat.core.model.config.InterfaceConfig
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.schedule.Schedule
import com.garfiec.librechat.core.model.schedule.ScheduleCadence
import com.garfiec.librechat.core.model.schedule.ScheduleDisabledReason
import com.garfiec.librechat.core.model.schedule.ScheduleLastRun
import com.garfiec.librechat.core.model.schedule.ScheduleListResponse
import com.garfiec.librechat.core.model.schedule.ScheduleRunStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ScheduledRunWatcherTest {

    private val alice = AccountId("server:alice")
    private val now = Instant.parse("2026-11-10T09:00:00Z")
    private val daily = ScheduleCadence.structured("daily", hour = 9, minute = 0)

    private fun seen(
        firedAt: String? = "2026-11-09T09:00:00Z",
        status: String? = ScheduleRunStatus.SUCCESS,
        enabled: Boolean = true,
        nextRunAt: String? = null,
        runningSince: Instant? = null,
    ) = WatchedSchedule(
        id = "s1",
        name = "Morning brief",
        enabled = enabled,
        nextRunAt = nextRunAt,
        lastRunFiredAt = firedAt,
        lastRunStatus = status,
        runningSinceMillis = runningSince?.toEpochMilliseconds(),
    )

    private fun server(
        firedAt: String = "2026-11-10T09:00:00Z",
        status: String = ScheduleRunStatus.SUCCESS,
        enabled: Boolean = true,
        disabledReason: String? = null,
    ) = Schedule(
        id = "s1",
        name = "Morning brief",
        cadence = daily,
        enabled = enabled,
        disabledReason = disabledReason,
        lastRun = ScheduleLastRun(conversationId = "c2", status = status, firedAt = firedAt),
    )

    private fun noticesFor(before: WatchedSchedule, after: Schedule) =
        scheduledRunNotices(listOf(before), listOf(after), alice)

    @Test
    fun aNewRun_isAnnouncedByItsOutcome_withItsChat() {
        assertEquals(
            listOf(ScheduledRunNotification(alice, "s1", "Morning brief", "c2", ScheduledRunOutcome.Finished)),
            noticesFor(seen(), server()),
        )
        val failed = noticesFor(seen(), server(status = ScheduleRunStatus.ERROR)).single()
        assertEquals(ScheduledRunOutcome.Failed, failed.outcome)
        // A failed run may have no chat: it opens the Schedules list instead.
        assertNull(failed.conversationId)
        assertEquals(ScheduledRunOutcome.Failed, noticesFor(seen(), server(status = ScheduleRunStatus.INTERRUPTED)).single().outcome)
        assertEquals(
            ScheduledRunOutcome.SkippedForBalance,
            noticesFor(seen(), server(status = ScheduleRunStatus.SKIPPED_BALANCE)).single().outcome,
        )
    }

    @Test
    fun aRunAlreadySeen_orStillStarting_orSkippedForOverlap_isNot() {
        assertEquals(emptyList(), noticesFor(seen(firedAt = "2026-11-10T09:00:00Z"), server()))
        assertEquals(emptyList(), noticesFor(seen(), server(status = ScheduleRunStatus.STARTED)))
        assertEquals(emptyList(), noticesFor(seen(), server(status = ScheduleRunStatus.SKIPPED_OVERLAP)))
    }

    @Test
    fun aRunPausedForApproval_isAnnounced_andAgainWhenItFinishes() {
        val paused = noticesFor(seen(), server(status = ScheduleRunStatus.REQUIRES_ACTION))
        assertEquals(ScheduledRunOutcome.NeedsApproval, paused.single().outcome)

        val sameRunFinished = noticesFor(
            seen(firedAt = "2026-11-10T09:00:00Z", status = ScheduleRunStatus.REQUIRES_ACTION),
            server(status = ScheduleRunStatus.SUCCESS),
        )
        assertEquals(ScheduledRunOutcome.Finished, sameRunFinished.single().outcome)
    }

    @Test
    fun theServerPausingASchedule_isAnnouncedInsteadOfItsRun_andOpensTheList() {
        val notices = noticesFor(
            seen(),
            server(status = ScheduleRunStatus.ERROR, enabled = false, disabledReason = ScheduleDisabledReason.TOO_MANY_FAILURES),
        )
        assertEquals(ScheduledRunOutcome.Paused, notices.single().outcome)
        assertNull(notices.single().conversationId)
    }

    @Test
    fun theUserTurningAScheduleOff_isNotAnnounced() {
        assertEquals(emptyList(), noticesFor(seen(firedAt = "2026-11-10T09:00:00Z"), server(enabled = false)))
    }

    @Test
    fun aScheduleSeenForTheFirstTime_isOnlyRecorded() {
        assertEquals(emptyList(), scheduledRunNotices(emptyList(), listOf(server()), alice))
    }

    @Test
    fun theNextCheck_isAMinuteAfterTheEarliestServerRunTime() {
        val schedules = listOf(
            seen(nextRunAt = "2026-11-10T12:00:00Z"),
            seen(nextRunAt = "2026-11-10T10:00:00Z"),
            seen(nextRunAt = "2026-11-10T09:30:00Z", enabled = false),
        )
        assertEquals(Instant.parse("2026-11-10T10:01:00Z"), nextScheduleCheck(schedules, now))
    }

    @Test
    fun aRunGeneratingOrDueButNotStarted_isCheckedAgainSoon_untilItIsTooOld() {
        val generating = seen(nextRunAt = "2026-11-11T09:00:00Z", runningSince = now - 5.minutes)
        assertEquals(now + SETTLE_POLL, nextScheduleCheck(listOf(generating), now))
        assertEquals(now + SETTLE_POLL, nextScheduleCheck(listOf(seen(nextRunAt = "2026-11-10T08:58:00Z")), now))

        val stale = seen(nextRunAt = "2026-11-11T09:00:00Z", runningSince = now - 1.hours)
        assertEquals(Instant.parse("2026-11-11T09:01:00Z"), nextScheduleCheck(listOf(stale), now))
        assertNull(nextScheduleCheck(listOf(seen(nextRunAt = "2026-11-10T07:00:00Z")), now))
    }

    @Test
    fun noScheduleThatWillRun_meansNoCheck() {
        assertNull(nextScheduleCheck(emptyList(), now))
        assertNull(nextScheduleCheck(listOf(seen(nextRunAt = null)), now))
    }

    private val scheduleRepository = mockk<ScheduleRepository>()
    private val watchList = mockk<ScheduleWatchList>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)
    private val replyReader = mockk<ReplyReader>()
    private val notifications = mockk<ReplyNotifications>(relaxed = true)
    private val accounts = InMemoryActiveAccountProvider().apply { set(alice) }

    private val foreground = ForegroundSignal()
    private val scheduler = mockk<ScheduleCheckScheduler>(relaxed = true)

    private fun TestScope.watcher(schedulesFlag: Boolean, stored: List<WatchedSchedule>? = null) = run {
        val config = StartupConfig(interfaceConfig = InterfaceConfig(schedules = JsonPrimitive(schedulesFlag)))
        every { configRepository.startupConfig } returns MutableStateFlow(config)
        every { watchList.observe() } returns (stored?.let { flowOf(it) } ?: emptyFlow())
        ScheduledRunWatcher(
            scheduleRepository = scheduleRepository,
            watchList = watchList,
            configRepository = configRepository,
            accounts = accounts,
            replyReader = replyReader,
            notifications = notifications,
            scheduler = scheduler,
            foregroundSignal = foreground,
            scope = backgroundScope,
            clock = object : Clock {
                override fun now() = this@ScheduledRunWatcherTest.now
            },
        )
    }

    @Test
    fun aCheck_announcesWhatChangedSinceTheStoredList_withThePreview() = runTest {
        // Read before listing: the list itself rewrites what is stored.
        coEvery { watchList.snapshot(alice) } returns listOf(seen())
        coEvery { scheduleRepository.listSchedules() } returns Result.Success(ScheduleListResponse(listOf(server())))
        coEvery { replyReader.read("c2", alice) } returns FinishedReply("t", "Rain at noon", failed = false, isTemporary = false)
        val announced = slot<AppNotification>()
        coEvery { notifications.announce(capture(announced)) } returns Unit

        watcher(schedulesFlag = true).check()

        coVerifyOrder {
            watchList.snapshot(alice)
            scheduleRepository.listSchedules()
        }
        assertEquals(
            ScheduledRunNotification(alice, "s1", "Morning brief", "c2", ScheduledRunOutcome.Finished, preview = "Rain at noon"),
            announced.captured,
        )
    }

    @Test
    fun comingToTheFront_announcesARunThatEndedWhileAway() = runTest {
        coEvery { watchList.snapshot(alice) } returns listOf(seen())
        coEvery { scheduleRepository.listSchedules() } returns
            Result.Success(ScheduleListResponse(listOf(server(status = ScheduleRunStatus.ERROR))))
        watcher(schedulesFlag = true)
        runCurrent()

        foreground.set(true)
        runCurrent()

        coVerify { notifications.announce(match { it is ScheduledRunNotification && it.outcome == ScheduledRunOutcome.Failed }) }
    }

    @Test
    fun aCheck_onAServerWithSchedulesOff_forgetsTheList() = runTest {
        watcher(schedulesFlag = false).check()

        coVerify { watchList.clear(alice) }
        coVerify(exactly = 0) { scheduleRepository.listSchedules() }
        coVerify(exactly = 0) { notifications.announce(any()) }
    }

    @Test
    fun aFailedCheck_longPastTheRunTime_triesAgainLater() = runTest {
        coEvery { watchList.snapshot(alice) } returns listOf(seen(nextRunAt = "2026-11-10T07:00:00Z"))
        coEvery { scheduleRepository.listSchedules() } returns Result.Error(RuntimeException("502"))

        assertEquals(now + RETRY_AFTER_FAILURE, watcher(schedulesFlag = true).check())
    }

    @Test
    fun twoChecksAtOnce_announceARunOnce() = runTest {
        var stored = listOf(seen())
        coEvery { watchList.snapshot(alice) } answers { stored }
        coEvery { scheduleRepository.listSchedules() } coAnswers {
            delay(100)
            // As the repository does: a successful list rewrites what is stored.
            stored = listOf(seen(firedAt = "2026-11-10T09:00:00Z", status = ScheduleRunStatus.ERROR))
            Result.Success(ScheduleListResponse(listOf(server(status = ScheduleRunStatus.ERROR))))
        }
        val watcher = watcher(schedulesFlag = true)

        launch { watcher.check() }
        launch { watcher.check() }
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { notifications.announce(any()) }
    }

    @Test
    fun comingToTheFront_whileTheServerIsDown_armsTheRetry() = runTest {
        coEvery { watchList.snapshot(alice) } returns listOf(seen(nextRunAt = "2026-11-10T07:00:00Z"))
        coEvery { scheduleRepository.listSchedules() } returns Result.Error(RuntimeException("502"))
        watcher(schedulesFlag = true)
        runCurrent()

        foreground.set(true)
        runCurrent()

        coVerify { scheduler.arm(now + RETRY_AFTER_FAILURE) }
    }

    @Test
    fun aStartThatFindsNothingDue_leavesAQueuedCheckAlone() = runTest {
        watcher(schedulesFlag = true, stored = listOf(seen(nextRunAt = "2026-11-10T07:00:00Z")))
        runCurrent()

        coVerify(exactly = 0) { scheduler.arm(any()) }
    }

    @Test
    fun aStartWithARunComing_armsItsCheck() = runTest {
        watcher(schedulesFlag = true, stored = listOf(seen(nextRunAt = "2026-11-10T10:00:00Z")))
        runCurrent()

        coVerify { scheduler.arm(Instant.parse("2026-11-10T10:01:00Z")) }
    }

    @Test
    fun aFailedCheck_moreThan12HoursAfterTheRun_stopsRetrying() = runTest {
        coEvery { watchList.snapshot(alice) } returns listOf(seen(nextRunAt = "2026-11-09T20:00:00Z"))
        coEvery { scheduleRepository.listSchedules() } returns Result.Error(RuntimeException("401"))

        assertNull(watcher(schedulesFlag = true).check())
    }

    @Test
    fun aFailedCheck_retriesAMissedRun_beforeAnotherSchedulesLaterRun() = runTest {
        coEvery { watchList.snapshot(alice) } returns listOf(
            seen(nextRunAt = "2026-11-10T07:00:00Z"),
            seen(nextRunAt = "2026-11-13T09:00:00Z"),
        )
        coEvery { scheduleRepository.listSchedules() } returns Result.Error(RuntimeException("502"))

        assertEquals(now + RETRY_AFTER_FAILURE, watcher(schedulesFlag = true).check())
    }
}
