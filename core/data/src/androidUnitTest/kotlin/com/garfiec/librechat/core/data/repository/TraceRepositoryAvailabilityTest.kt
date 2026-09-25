package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.BackendBuildClass
import com.garfiec.librechat.core.common.DetectedBackend
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.trace.TraceAvailability
import com.garfiec.librechat.core.network.api.TracesApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The availability precondition, which is the whole gate on the trace entry point.
 *
 * Everything worth testing here is about NOT asking: the route is the one trace route with no rate
 * limiter in front of it, so a loop that re-reads on its own is a loop nothing stops.
 */
class TraceRepositoryAvailabilityTest {

    private val api = mockk<TracesApi>()
    private val configRepository = mockk<ConfigRepository>()

    private fun repository(detected: DetectedBackend? = rc3()): TraceRepository {
        every { configRepository.detectedBackend } returns MutableStateFlow(detected)
        return TraceRepositoryImpl(api, configRepository)
    }

    private fun rc3() = DetectedBackend("0.8.8-rc3", BackendBuildClass.RC, "2026-09-17")
    private fun v087() = DetectedBackend("0.8.7", BackendBuildClass.OFFICIAL, "2026-06-26")

    @Test
    fun `a decided answer is returned without asking twice`() = runTest {
        coEvery { api.getAvailability("c1") } returns TraceAvailability(available = true)

        val result = repository().resolveAvailability("c1")

        assertThat((result as Result.Success).data.available).isTrue()
        coVerify(exactly = 1) { api.getAvailability("c1") }
    }

    @Test
    fun `an undecided answer is waited out and re-read`() = runTest {
        // The backend sets retryAfterMs while it is still ingesting the run. Returning the first
        // `false` would hide the control on exactly the conversation that just produced a trace.
        coEvery { api.getAvailability("c1") } returnsMany listOf(
            TraceAvailability(available = false, retryAfterMs = 500),
            TraceAvailability(available = true),
        )

        val result = repository().resolveAvailability("c1")

        assertThat((result as Result.Success).data.available).isTrue()
        coVerify(exactly = 2) { api.getAvailability("c1") }
    }

    @Test
    fun `the waiting is bounded`() = runTest {
        // A deployment that never settles would otherwise re-read for as long as the conversation
        // stays open, against the one trace route with no limiter in front of it.
        coEvery { api.getAvailability("c1") } returns
            TraceAvailability(available = false, retryAfterMs = 500)

        val result = repository().resolveAvailability("c1")

        assertThat((result as Result.Success).data.available).isFalse()
        coVerify(exactly = 11) { api.getAvailability("c1") }
    }

    @Test
    fun `a non-positive wait is not waited on`() = runTest {
        // Honouring it literally is a spin: no delay, and the same answer next time round.
        coEvery { api.getAvailability("c1") } returns
            TraceAvailability(available = false, retryAfterMs = 0)

        repository().resolveAvailability("c1")

        coVerify(exactly = 1) { api.getAvailability("c1") }
    }

    @Test
    fun `a refusal the server decided is taken at its word`() = runTest {
        coEvery { api.getAvailability("c1") } throws ApiException(404, "Trace not found")

        val result = repository().resolveAvailability("c1")

        assertThat(result).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 1) { api.getAvailability("c1") }
    }

    /**
     * A 5xx GET is already retried by the transport — `configureRetryPolicy` replays retry-safe
     * methods twice with exponential backoff — so a second ladder here multiplied rather than
     * added: four repository attempts over three transport attempts is twelve requests to a route
     * with no rate limiter, plus seconds of sleeping the caller cannot see. The fault is reported
     * once; the retrying already happened underneath.
     */
    @Test
    fun `a server fault is reported rather than retried a second time`() = runTest {
        coEvery { api.getAvailability("c1") } throws ApiException(500, "Server error")

        val result = repository().resolveAvailability("c1")

        assertThat(result).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 1) { api.getAvailability("c1") }
    }

    @Test
    fun `a proven-old server is ruled out and an unplaceable one is not`() {
        // The interface flag already fails closed on a pre-rc3 server — its config schema strips
        // `traceViewer` before serving it — so this exists for the build that reports a tag it
        // predates, not as the live gate.
        assertThat(repository(v087()).isRuledOutForServer()).isTrue()
        assertThat(repository(rc3()).isRuledOutForServer()).isFalse()
        assertThat(repository(detected = null).isRuledOutForServer()).isFalse()
    }
}
