package com.garfiec.librechat.core.data.prefetch

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.garfiec.librechat.core.model.prefetch.PrefetchRunOutcome
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration

/**
 * The worker resolves its dependency from Koin rather than through a `WorkerFactory`, which is a
 * runtime lookup no compiler checks. It is also the one component here that only ever runs when the
 * OS decides to run it, so a wiring mistake would surface as a job that quietly does nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrefetchWorkerTest {

    private val runner = mockk<PrefetchBackgroundRunner>()

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun startKoinWithRunner() {
        startKoin { modules(module { single { runner } }) }
    }

    @Test
    fun `the worker resolves the runner from Koin and runs a pass`() = runTest {
        startKoinWithRunner()
        coEvery { runner.runOnce(any<Duration>()) } returns PrefetchRunOutcome.COMPLETED

        val worker = TestListenableWorkerBuilder<PrefetchWorker>(
            ApplicationProvider.getApplicationContext(),
        ).build()

        assertThat(worker.doWork()).isEqualTo(ListenableWorker.Result.success())
    }

    /**
     * Every outcome reports success. With `setRequiresDeviceIdle` there is no backoff ladder, so a
     * retry would just wait for the next period — which success already does, without the job
     * showing up in diagnostics as a failure.
     */
    @Test
    fun `an unmet constraint is still reported as success`() = runTest {
        startKoinWithRunner()
        coEvery { runner.runOnce(any<Duration>()) } returns PrefetchRunOutcome.CONSTRAINTS_UNMET

        val worker = TestListenableWorkerBuilder<PrefetchWorker>(
            ApplicationProvider.getApplicationContext(),
        ).build()

        assertThat(worker.doWork()).isEqualTo(ListenableWorker.Result.success())
    }
}
