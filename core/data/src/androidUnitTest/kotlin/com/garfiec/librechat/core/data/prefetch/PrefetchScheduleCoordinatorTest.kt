package com.garfiec.librechat.core.data.prefetch

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.data.datastore.AppPreferences
import com.garfiec.librechat.core.data.datastore.PreferenceStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Whether the OS is asked to wake us up at all.
 *
 * Every assertion here is about a *decision*, not about the resulting job: the scheduler's own
 * `KEEP` makes a redundant registration invisible in WorkManager's tables, so "did the coordinator
 * act" cannot be read back off the job afterwards. Recording the calls is the only way to tell a
 * coordinator that decided to keep the job from one that never ran at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrefetchScheduleCoordinatorTest {

    private val account = AccountId("srv:user-a")

    private val enabled = MutableStateFlow(true)
    private val onMetered = MutableStateFlow(false)

    private val preferenceStore = mockk<PreferenceStore>(relaxed = true).also {
        every { it.observe(AppPreferences.PrefetchEnabled) } returns enabled
        every { it.observe(AppPreferences.PrefetchOnMetered) } returns onMetered
    }

    private class RecordingScheduler(override val isSupported: Boolean = true) : PrefetchScheduler {
        val calls = mutableListOf<String>()
        override fun ensureScheduled(allowMetered: Boolean) {
            calls += "schedule(metered=$allowMetered)"
        }

        override fun cancel() {
            calls += "cancel"
        }
    }

    /**
     * Runs [body] against a live coordinator, then tears its scope down.
     *
     * The coordinator's whole job is an endless collector, so handing it the `TestScope` directly
     * leaves `runTest` waiting on a coroutine that by design never finishes.
     */
    private fun coordinatorTest(
        initial: AccountState,
        isSupported: Boolean = true,
        body: TestScope.(InMemoryActiveAccountProvider, RecordingScheduler) -> Unit,
    ) = runTest {
        val provider = InMemoryActiveAccountProvider(initial)
        val scheduler = RecordingScheduler(isSupported)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        PrefetchScheduleCoordinator(
            preferenceStore = preferenceStore,
            activeAccountProvider = provider,
            scheduler = scheduler,
            appScope = scope,
        )
        advanceUntilIdle()
        try {
            body(provider, scheduler)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a signed-in account with prefetching on registers the job`() =
        coordinatorTest(AccountState.Resolved(account)) { _, scheduler ->
            assertEquals(listOf("schedule(metered=false)"), scheduler.calls)
        }

    @Test
    fun `signing out cancels the job`() =
        coordinatorTest(AccountState.Resolved(account)) { provider, scheduler ->
            provider.clear()
            advanceUntilIdle()

            assertEquals(listOf("schedule(metered=false)", "cancel"), scheduler.calls)
        }

    @Test
    fun `switching prefetching off cancels the job`() =
        coordinatorTest(AccountState.Resolved(account)) { _, scheduler ->
            enabled.value = false
            advanceUntilIdle()

            assertEquals(listOf("schedule(metered=false)", "cancel"), scheduler.calls)
        }

    @Test
    fun `changing the metered override re-registers the job`() =
        coordinatorTest(AccountState.Resolved(account)) { _, scheduler ->
            onMetered.value = true
            advanceUntilIdle()

            assertEquals(
                listOf("schedule(metered=false)", "schedule(metered=true)"),
                scheduler.calls,
            )
        }

    /**
     * Why the coordinator reads identity rather than the session: a null session means both "still
     * warming" and "signed out", and treating the boot value as signed out cancels the pending job at
     * every process start — including, in a process the job itself woke, the run in progress.
     */
    @Test
    fun `a warming identity decides nothing either way`() =
        coordinatorTest(AccountState.Warming) { _, scheduler ->
            assertEquals(emptyList(), scheduler.calls)
        }

    /**
     * A cold start with no credentials resolves straight to logged-out without ever passing through
     * a signed-in state. A job registered before the session died is still on the device, so this
     * has to cancel rather than wait for a transition that never comes.
     */
    @Test
    fun `a cold start that resolves to logged out cancels the job`() =
        coordinatorTest(AccountState.Warming) { provider, scheduler ->
            provider.clear()
            advanceUntilIdle()

            assertEquals(listOf("cancel"), scheduler.calls)
        }

    @Test
    fun `a platform that schedules nothing is never asked to`() =
        coordinatorTest(AccountState.Resolved(account), isSupported = false) { provider, scheduler ->
            provider.clear()
            advanceUntilIdle()

            assertEquals(emptyList(), scheduler.calls)
        }
}
