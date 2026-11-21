package com.garfiec.librechat.feature.settings.screen.providerkeys

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * A status line reads out the answer to a Retry, and goes quiet again after it, so a key's
 * countdown isn't read out every minute.
 *
 * Each test drives the retry's progress the way the line's composable does: one long-lived
 * [RetryAnnouncement.follow] over the `retrying` value, which only ever changes. Nothing here
 * cancels or restarts the follower by hand.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RetryAnnouncementTest {

    private val retrying = MutableStateFlow(false)
    private val announcement = RetryAnnouncement(window = 1.seconds)

    private fun TestScope.following() {
        backgroundScope.launch { announcement.follow(retrying) }
        runCurrent()
    }

    /** Sets the retry's progress and lets the follower see it. */
    private fun TestScope.retrying(value: Boolean) {
        retrying.value = value
        runCurrent()
    }

    private fun TestScope.tapRetry() {
        announcement.onRetry()
        runCurrent()
    }

    @Test
    fun quiet_until_a_retry() = runTest {
        following()
        assertThat(announcement.active).isFalse()

        // The first load's check (or any check the user didn't ask for) is not read out.
        retrying(true)
        retrying(false)
        advanceTimeBy(5.seconds)
        assertThat(announcement.active).isFalse()
    }

    @Test
    fun live_while_the_retry_runs_and_for_a_moment_after_its_answer_then_quiet() = runTest {
        following()
        // The tap and the retry starting land together.
        tapRetry()
        retrying(true)

        // However long the check takes, the line stays live.
        advanceTimeBy(1.minutes)
        assertThat(announcement.active).isTrue()

        // The answer is shown: still live, so the change is read out.
        retrying(false)
        advanceTimeBy(900.milliseconds)
        assertThat(announcement.active).isTrue()

        advanceTimeBy(200.milliseconds)
        // The next minute's tick changes the line without being read out.
        assertThat(announcement.active).isFalse()

        // And it stays quiet for as long as the user stays.
        advanceTimeBy(10.minutes)
        assertThat(announcement.active).isFalse()
    }

    @Test
    fun the_tap_counts_even_before_the_retry_shows_as_running() = runTest {
        following()
        // The ViewModel's retrying flag can lag the tap by a frame; the window waits for it.
        tapRetry()
        advanceTimeBy(500.milliseconds)
        retrying(true)
        advanceTimeBy(2.seconds)
        assertThat(announcement.active).isTrue()

        retrying(false)
        advanceTimeBy(1100.milliseconds)
        assertThat(announcement.active).isFalse()
    }

    @Test
    fun an_answer_too_fast_to_show_as_running_still_ends_the_window() = runTest {
        following()
        // The retry started and answered within one frame: `retrying` never read true.
        tapRetry()
        advanceTimeBy(900.milliseconds)
        assertThat(announcement.active).isTrue()

        advanceTimeBy(200.milliseconds)
        assertThat(announcement.active).isFalse()
    }

    @Test
    fun a_second_retry_during_the_window_keeps_the_line_live_for_its_own_answer() = runTest {
        following()
        tapRetry()
        retrying(true)
        retrying(false)
        // The first answer is out; its window is half run.
        advanceTimeBy(500.milliseconds)
        assertThat(announcement.active).isTrue()

        // A new retry starts. The first window's end (t = 1 s) passes while it runs.
        tapRetry()
        retrying(true)
        advanceTimeBy(2.seconds)
        assertThat(announcement.active).isTrue()

        // Its answer gets a full window of its own...
        retrying(false)
        advanceTimeBy(900.milliseconds)
        assertThat(announcement.active).isTrue()

        // ...and then the line goes quiet.
        advanceTimeBy(200.milliseconds)
        assertThat(announcement.active).isFalse()
    }
}
