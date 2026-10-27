package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.endpoint.KeyInvalidation
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.model.endpoint.fromWire
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.EndpointKeyHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Behavior tests for the per-endpoint user-provided-key status fan-out.
 *
 * Each test wires a fake [KeyRepository] return shape, calls
 * [EndpointKeyStatusDelegate.recomputeFor], drains the test scheduler, and
 * asserts the resulting [ChatUiState.endpointKeyStates] map.
 *
 * The delegate's contract: only endpoints with `userProvide=true` (or
 * `userProvideURL=true`) appear in the result map; built-ins are absent.
 * Network errors preserve any previously-resolved [KeyState] for that endpoint;
 * brand-new endpoints with no prior state fall through to [KeyState.Unset]
 * (fail-closed → CTA shown).
 *
 * The delegate consumes [KeyRepository.keyInvalidations] in its init block, so
 * every fixture provides a real [MutableSharedFlow] instead of relying on
 * MockK's relaxed default (the relaxed default returns an unsubscribable stub).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EndpointKeyStatusDelegateTest {

    private val invalidations = MutableSharedFlow<KeyInvalidation>(extraBufferCapacity = 8)

    private val keyRepository = mockk<KeyRepository>(relaxed = true).also {
        every { it.keyInvalidations } returns invalidations
    }

    private fun newHandle(scope: CoroutineScope) = ChatStateHandle(
        stateFlow = MutableStateFlow(ChatUiState()),
        scope = scope,
    )

    /** Wall-clock start plus the scheduler's virtual time. */
    private class VirtualClock(private val scheduler: TestCoroutineScheduler, private val start: Instant) : Clock {
        override fun now(): Instant = start + scheduler.currentTime.milliseconds
    }

    private val clockStart = Clock.System.now()

    private fun TestScope.newDelegate(handle: ChatStateHandle) =
        EndpointKeyStatusDelegate(EndpointKeyHandle(handle), keyRepository, VirtualClock(testScheduler, clockStart))

    private fun setAt(expiresAt: Instant) =
        KeyState.Set(expiresAt = expiresAt, neverExpires = false, wire = expiresAt.toString())

    private fun expiredAt(expiresAt: Instant) = KeyState.Expired(expiresAt = expiresAt, wire = expiresAt.toString())

    private val neverExpires = KeyState.Set(expiresAt = null, neverExpires = true, wire = "never")

    @Test
    fun unsetWhenGetKeyExpiryReturnsNull() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val delegate = newDelegate(handle)
        coEvery { keyRepository.fetchKeyState(any()) } returns Result.Success(KeyState.Unset)

        delegate.recomputeFor(mapOf("openAI" to EndpointConfig(userProvide = true)))
        advanceUntilIdle()

        assertThat(handle.state.endpointKeyStates).containsExactly("openAI", KeyState.Unset)
    }

    @Test
    fun setWithFutureIsoTimestamp() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val delegate = newDelegate(handle)
        val future = (Clock.System.now() + 1.days).toString()
        coEvery { keyRepository.fetchKeyState("openAI") } returns
            Result.Success(KeyState.fromWire(future, Clock.System.now()).state)

        delegate.recomputeFor(mapOf("openAI" to EndpointConfig(userProvide = true)))
        runCurrent()

        val state = handle.state.endpointKeyStates["openAI"]
        assertThat(state).isInstanceOf(KeyState.Set::class.java)
        assertThat((state as KeyState.Set).neverExpires).isFalse()
        assertThat(state.expiresAt).isNotNull()
        assertThat(state.wire).isEqualTo(future)
    }

    @Test
    fun expiredWhenIsoTimestampInPast() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val delegate = newDelegate(handle)
        val past = (Clock.System.now() - 1.days).toString()
        coEvery { keyRepository.fetchKeyState("openAI") } returns
            Result.Success(KeyState.fromWire(past, Clock.System.now()).state)

        delegate.recomputeFor(mapOf("openAI" to EndpointConfig(userProvide = true)))
        advanceUntilIdle()

        val expired = handle.state.endpointKeyStates["openAI"]
        assertThat(expired).isEqualTo(KeyState.Expired(expiresAt = Instant.parse(past), wire = past))
    }

    @Test
    fun setNeverExpiresWhenWireValueIsNeverLiteral() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val delegate = newDelegate(handle)
        coEvery { keyRepository.fetchKeyState("openAI") } returns
            Result.Success(KeyState.Set(expiresAt = null, neverExpires = true, wire = "never"))

        delegate.recomputeFor(mapOf("openAI" to EndpointConfig(userProvide = true)))
        advanceUntilIdle()

        val state = handle.state.endpointKeyStates["openAI"]
        assertThat(state).isInstanceOf(KeyState.Set::class.java)
        assertThat((state as KeyState.Set).neverExpires).isTrue()
        assertThat(state.expiresAt).isNull()
        assertThat(state.wire).isEqualTo("never")
    }

    @Test
    fun userProvideUrlOnlyEndpointStillTrackedAsNeedsKey() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val delegate = newDelegate(handle)
        coEvery { keyRepository.fetchKeyState(any()) } returns Result.Success(KeyState.Unset)

        delegate.recomputeFor(
            mapOf("custom" to EndpointConfig(userProvide = null, userProvideURL = true)),
        )
        advanceUntilIdle()

        assertThat(handle.state.endpointKeyStates).containsExactly("custom", KeyState.Unset)
    }

    @Test
    fun builtInEndpointsAbsentFromResultMap() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val delegate = newDelegate(handle)
        coEvery { keyRepository.fetchKeyState(any()) } returns Result.Success(KeyState.Unset)

        delegate.recomputeFor(
            mapOf(
                "anthropic" to EndpointConfig(userProvide = null),
                "openAI" to EndpointConfig(userProvide = false),
            ),
        )
        advanceUntilIdle()

        assertThat(handle.state.endpointKeyStates).isEmpty()
    }

    @Test
    fun azureEndpointResolvesToAzureOpenAiKeyName() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val delegate = newDelegate(handle)
        coEvery { keyRepository.fetchKeyState("azureOpenAI") } returns Result.Success(KeyState.Unset)

        delegate.recomputeFor(
            mapOf("AzureOpenAI" to EndpointConfig(userProvide = true, azure = true)),
        )
        advanceUntilIdle()

        coVerify { keyRepository.fetchKeyState("azureOpenAI") }
        assertThat(handle.state.endpointKeyStates).containsKey("AzureOpenAI")
    }

    @Test
    fun networkErrorOnFirstLoadFallsBackToUnset() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val delegate = newDelegate(handle)
        coEvery { keyRepository.fetchKeyState(any()) } returns
            Result.Error(RuntimeException("server unavailable"))

        delegate.recomputeFor(mapOf("openAI" to EndpointConfig(userProvide = true)))
        advanceUntilIdle()

        // No prior state on first load → surface the configurable affordance
        // instead of silently letting the user pick a model that will fail at
        // chat-send time.
        assertThat(handle.state.endpointKeyStates["openAI"]).isEqualTo(KeyState.Unset)
    }

    @Test
    fun networkErrorPreservesPriorResolvedStateAcrossRefetch() = runTest {
        // Guards against the "transient 401 mid-auth-refresh greys every row"
        // failure mode: once an endpoint has been resolved to a real state, a
        // later failed fetch (during invalidation-driven refresh) must keep the
        // last-known value rather than demoting it to Unset/CTA.
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val delegate = newDelegate(handle)

        val future = (Clock.System.now() + 1.days).toString()
        val resolvedSet = KeyState.Set(
            expiresAt = Clock.System.now() + 1.days,
            neverExpires = false,
            wire = future,
        )

        // First fan-out resolves to a real Set.
        coEvery { keyRepository.fetchKeyState("openAI") } returns Result.Success(resolvedSet)
        delegate.recomputeFor(mapOf("openAI" to EndpointConfig(userProvide = true)))
        runCurrent()
        assertThat(handle.state.endpointKeyStates["openAI"]).isEqualTo(resolvedSet)

        // Invalidation triggers a refetch, but the next call errors.
        coEvery { keyRepository.fetchKeyState("openAI") } returns
            Result.Error(RuntimeException("transient 401"))
        invalidations.emit(KeyInvalidation.ByName("openAI"))
        runCurrent()

        // Prior Set value preserved — the CTA does NOT appear mid-blip.
        assertThat(handle.state.endpointKeyStates["openAI"]).isEqualTo(resolvedSet)
    }

    @Test
    fun loadingStatePushedOptimisticallyBeforeFanOut() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val delegate = newDelegate(handle)

        // Stub never returns until we explicitly advance the scheduler so we can
        // observe the optimistic Loading state mid-fan-out.
        val deferredAnswer = CompletableDeferred<Result<KeyState>>()
        coEvery { keyRepository.fetchKeyState(any()) } coAnswers { deferredAnswer.await() }

        delegate.recomputeFor(mapOf("openAI" to EndpointConfig(userProvide = true)))
        // Yield once so the optimistic update lands, but the fan-out is still suspended.
        testScheduler.runCurrent()

        assertThat(handle.state.endpointKeyStates["openAI"]).isEqualTo(KeyState.Loading)

        // Now resolve the in-flight call so the test can complete cleanly.
        deferredAnswer.complete(Result.Success(KeyState.Unset))
        advanceUntilIdle()
        assertThat(handle.state.endpointKeyStates["openAI"]).isEqualTo(KeyState.Unset)
    }

    @Test
    fun emptyConfigsClearsExistingStates() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val delegate = newDelegate(handle)
        coEvery { keyRepository.fetchKeyState(any()) } returns Result.Success(KeyState.Unset)

        delegate.recomputeFor(mapOf("openAI" to EndpointConfig(userProvide = true)))
        advanceUntilIdle()
        delegate.recomputeFor(emptyMap())
        advanceUntilIdle()

        assertThat(handle.state.endpointKeyStates).isEmpty()
    }

    @Test
    fun keyInvalidationByNameTriggersRecompute() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val delegate = newDelegate(handle)
        coEvery { keyRepository.fetchKeyState(any()) } returns Result.Success(KeyState.Unset)

        delegate.recomputeFor(mapOf("openAI" to EndpointConfig(userProvide = true)))
        advanceUntilIdle()

        invalidations.emit(KeyInvalidation.ByName("openAI"))
        advanceUntilIdle()

        coVerify(exactly = 2) { keyRepository.fetchKeyState("openAI") }
    }

    @Test
    fun keyInvalidationForUnrelatedEndpointDoesNotTriggerRecompute() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val delegate = newDelegate(handle)
        coEvery { keyRepository.fetchKeyState(any()) } returns Result.Success(KeyState.Unset)

        delegate.recomputeFor(mapOf("openAI" to EndpointConfig(userProvide = true)))
        advanceUntilIdle()

        invalidations.emit(KeyInvalidation.ByName("anthropic"))
        advanceUntilIdle()

        // Unchanged: only the openAI fan-out from the initial recomputeFor.
        coVerify(exactly = 1) { keyRepository.fetchKeyState("openAI") }
    }

    @Test
    fun keyInvalidationAllAlwaysRefreshes() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val delegate = newDelegate(handle)
        coEvery { keyRepository.fetchKeyState(any()) } returns Result.Success(KeyState.Unset)

        delegate.recomputeFor(mapOf("openAI" to EndpointConfig(userProvide = true)))
        advanceUntilIdle()

        invalidations.emit(KeyInvalidation.All)
        advanceUntilIdle()

        coVerify(exactly = 2) { keyRepository.fetchKeyState("openAI") }
    }

    @Test
    fun keyInvalidationByNameFetchesOnlyThatKeyAndKeepsTheOthers() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val delegate = newDelegate(handle)
        coEvery { keyRepository.fetchKeyState(any()) } returns Result.Success(KeyState.Unset)

        delegate.recomputeFor(
            mapOf(
                "openAI" to EndpointConfig(userProvide = true),
                "anthropic" to EndpointConfig(userProvide = true),
            ),
        )
        advanceUntilIdle()

        coEvery { keyRepository.fetchKeyState("openAI") } returns Result.Success(neverExpires)
        invalidations.emit(KeyInvalidation.ByName("openAI"))
        advanceUntilIdle()

        coVerify(exactly = 2) { keyRepository.fetchKeyState("openAI") }
        coVerify(exactly = 1) { keyRepository.fetchKeyState("anthropic") }
        assertThat(handle.state.endpointKeyStates).containsExactly(
            "openAI", neverExpires,
            "anthropic", KeyState.Unset,
        )
    }

    @Test
    fun keyInvalidationByNameDuringAFullFanOutStillFetchesEveryEndpoint() = runTest {
        // The cancelled fan-out had already pushed Loading for new rows; a fetch of the named key
        // alone would leave the others loading.
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val delegate = newDelegate(handle)
        val firstCall = CompletableDeferred<Result<KeyState>>()
        var calls = 0
        coEvery { keyRepository.fetchKeyState(any()) } coAnswers {
            if (++calls == 1) firstCall.await() else Result.Success(KeyState.Unset)
        }

        delegate.recomputeFor(
            mapOf(
                "openAI" to EndpointConfig(userProvide = true),
                "anthropic" to EndpointConfig(userProvide = true),
            ),
        )
        testScheduler.runCurrent()
        invalidations.emit(KeyInvalidation.ByName("openAI"))
        advanceUntilIdle()

        assertThat(handle.state.endpointKeyStates).containsExactly(
            "openAI", KeyState.Unset,
            "anthropic", KeyState.Unset,
        )
    }

    @Test
    fun emptyConfigsClearsExistingStatesWithoutRedundantWrites() = runTest {
        // recomputeFor with an empty gated set must clear endpointKeyStates if
        // it had entries, and must skip the state write entirely if already empty.
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val delegate = newDelegate(handle)
        coEvery { keyRepository.fetchKeyState(any()) } returns Result.Success(KeyState.Unset)

        // Seed with a non-empty map.
        delegate.recomputeFor(mapOf("openAI" to EndpointConfig(userProvide = true)))
        advanceUntilIdle()
        assertThat(handle.state.endpointKeyStates).hasSize(1)

        // Clear with an empty map — entries vanish.
        delegate.recomputeFor(emptyMap())
        advanceUntilIdle()
        assertThat(handle.state.endpointKeyStates).isEmpty()
    }

    @Test
    fun keyInvalidationDuringInFlightRecomputeCancelsPriorFanOut() = runTest {
        // Round-6 contract: an invalidation arriving while a `recomputeFor` fan-out is
        // suspended on a slow `getKeyExpiry` GET MUST cancel that prior fan-out and
        // launch a fresh one — it does not serialize behind the prior fan-out's mutex.
        // Otherwise the user's just-saved key would not be reflected snappily.
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val delegate = newDelegate(handle)

        // First call: suspend indefinitely so the fan-out is in-flight when the
        // invalidation arrives.
        val firstCall = CompletableDeferred<Result<KeyState>>()
        val secondCall = CompletableDeferred<Result<KeyState>>()
        var callCount = 0
        coEvery { keyRepository.fetchKeyState("openAI") } coAnswers {
            callCount++
            if (callCount == 1) firstCall.await() else secondCall.await()
        }

        delegate.recomputeFor(mapOf("openAI" to EndpointConfig(userProvide = true)))
        testScheduler.runCurrent()
        // First fan-out is in flight.
        assertThat(callCount).isEqualTo(1)

        // Invalidation arrives mid-recompute.
        invalidations.emit(KeyInvalidation.ByName("openAI"))
        testScheduler.runCurrent()

        // Resolve the SECOND call — the first call should never complete since the
        // job that owned it was cancelled.
        secondCall.complete(Result.Success(KeyState.Unset))
        advanceUntilIdle()

        assertThat(handle.state.endpointKeyStates["openAI"]).isEqualTo(KeyState.Unset)
        // The first call's deferred is left dangling; if we hadn't cancelled, the
        // second runFanOut would have been queued behind it on the mutex.
        assertThat(firstCall.isCompleted).isFalse()
    }

    @Test
    fun reEmittingSameGatedSetDoesNotFlickerResolvedRowsBackToLoading() = runTest {
        // An unrelated config tweak that re-emits the same gated endpoints must
        // NOT push every already-resolved row through Loading on each emission.
        // Only net-new (or previously-Loading) endpoints get the optimistic
        // Loading; resolved Set/Expired/Unset values are preserved.
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val delegate = newDelegate(handle)

        val future = (Clock.System.now() + 1.days).toString()
        coEvery { keyRepository.fetchKeyState("openAI") } returns
            Result.Success(KeyState.Set(expiresAt = Clock.System.now() + 1.days, neverExpires = false, wire = future))

        // First emission: row resolves to Set.
        delegate.recomputeFor(mapOf("openAI" to EndpointConfig(userProvide = true)))
        runCurrent()
        val resolved = handle.state.endpointKeyStates["openAI"]
        assertThat(resolved).isInstanceOf(KeyState.Set::class.java)

        // Now suspend the next fetchKeyState so we can observe the optimistic push.
        val deferred = CompletableDeferred<Result<KeyState>>()
        coEvery { keyRepository.fetchKeyState("openAI") } coAnswers { deferred.await() }

        // Re-emit the same gated set (e.g. an unrelated config tweak).
        delegate.recomputeFor(mapOf("openAI" to EndpointConfig(userProvide = true)))
        testScheduler.runCurrent()

        // Resolved value preserved during the in-flight fan-out — no flicker.
        assertThat(handle.state.endpointKeyStates["openAI"]).isInstanceOf(KeyState.Set::class.java)

        // Let the in-flight call complete and confirm it still ends in Set.
        deferred.complete(Result.Success(resolved!!))
        runCurrent()
        assertThat(handle.state.endpointKeyStates["openAI"]).isEqualTo(resolved)
    }
    // ── Expiry scheduling ────────────────────────────────────────────────

    @Test
    fun keyRunningOutWhileOpenLocksAtItsExpiryThenReadsUnsetAfterTheTtlWindow() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val delegate = newDelegate(handle)
        val expiresAt = clockStart + 10.minutes
        var server: KeyState = setAt(expiresAt)
        coEvery { keyRepository.fetchKeyState("openAI") } coAnswers { Result.Success(server) }

        delegate.recomputeFor(mapOf("openAI" to EndpointConfig(userProvide = true)))
        runCurrent()
        assertThat(handle.state.endpointKeyStates["openAI"]).isEqualTo(setAt(expiresAt))

        // Nothing before the deadline.
        server = expiredAt(expiresAt)
        advanceTimeBy(10.minutes + KeyExpiryMargin - 1.milliseconds)
        runCurrent()
        coVerify(exactly = 1) { keyRepository.fetchKeyState("openAI") }

        // At it: expired locally, without asking the server.
        advanceTimeBy(1.milliseconds)
        runCurrent()
        coVerify(exactly = 1) { keyRepository.fetchKeyState("openAI") }
        assertThat(handle.state.endpointKeyStates["openAI"]).isEqualTo(expiredAt(expiresAt))

        // The server's TTL pass deletes it; one refetch reads Not set.
        server = KeyState.Unset
        advanceTimeBy(ExpiredKeyTtlGrace - KeyExpiryMargin - 1.milliseconds)
        runCurrent()
        coVerify(exactly = 1) { keyRepository.fetchKeyState("openAI") }
        advanceTimeBy(1.milliseconds)
        runCurrent()
        coVerify(exactly = 2) { keyRepository.fetchKeyState("openAI") }
        assertThat(handle.state.endpointKeyStates["openAI"]).isEqualTo(KeyState.Unset)

        // Nothing left to wait for.
        advanceUntilIdle()
        coVerify(exactly = 2) { keyRepository.fetchKeyState("openAI") }
    }

    @Test
    fun theEarliestExpiryLocksOnlyItsKeyAndOnlyThatKeyIsFetchedAfterTheTtlWindow() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val delegate = newDelegate(handle)
        val server = mutableMapOf<String, KeyState>(
            "openAI" to setAt(clockStart + 30.minutes),
            "anthropic" to setAt(clockStart + 10.minutes),
            "google" to neverExpires,
        )
        coEvery { keyRepository.fetchKeyState(any()) } coAnswers { Result.Success(server.getValue(firstArg())) }

        delegate.recomputeFor(
            mapOf(
                "openAI" to EndpointConfig(userProvide = true),
                "anthropic" to EndpointConfig(userProvide = true),
                "google" to EndpointConfig(userProvide = true),
            ),
        )
        runCurrent()

        server["anthropic"] = KeyState.Unset
        advanceTimeBy(10.minutes + KeyExpiryMargin)
        runCurrent()
        coVerify(exactly = 1) { keyRepository.fetchKeyState("anthropic") }
        assertThat(handle.state.endpointKeyStates["anthropic"]).isEqualTo(expiredAt(clockStart + 10.minutes))
        assertThat(handle.state.endpointKeyStates["openAI"]).isEqualTo(setAt(clockStart + 30.minutes))

        advanceTimeBy(ExpiredKeyTtlGrace - KeyExpiryMargin)
        runCurrent()
        coVerify(exactly = 2) { keyRepository.fetchKeyState("anthropic") }
        coVerify(exactly = 1) { keyRepository.fetchKeyState("openAI") }
        coVerify(exactly = 1) { keyRepository.fetchKeyState("google") }
        assertThat(handle.state.endpointKeyStates).containsExactly(
            "openAI", setAt(clockStart + 30.minutes),
            "anthropic", KeyState.Unset,
            "google", neverExpires,
        )
    }

    @Test
    fun keysWithoutAnExpiryScheduleNothing() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val delegate = newDelegate(handle)
        coEvery { keyRepository.fetchKeyState("openAI") } returns Result.Success(neverExpires)
        coEvery { keyRepository.fetchKeyState("anthropic") } returns Result.Success(KeyState.Unset)

        delegate.recomputeFor(
            mapOf(
                "openAI" to EndpointConfig(userProvide = true),
                "anthropic" to EndpointConfig(userProvide = true),
            ),
        )
        advanceUntilIdle()

        coVerify(exactly = 1) { keyRepository.fetchKeyState("openAI") }
        coVerify(exactly = 1) { keyRepository.fetchKeyState("anthropic") }
    }

    @Test
    fun anEarlierExpiryFromARefreshReplacesTheScheduledRecompute() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val delegate = newDelegate(handle)
        var server: KeyState = setAt(clockStart + 30.minutes)
        coEvery { keyRepository.fetchKeyState("openAI") } coAnswers { Result.Success(server) }

        delegate.recomputeFor(mapOf("openAI" to EndpointConfig(userProvide = true)))
        runCurrent()

        // A new key saved elsewhere, expiring sooner.
        server = setAt(clockStart + 5.minutes)
        invalidations.emit(KeyInvalidation.ByName("openAI"))
        runCurrent()
        coVerify(exactly = 2) { keyRepository.fetchKeyState("openAI") }

        server = neverExpires
        advanceTimeBy(5.minutes + KeyExpiryMargin)
        runCurrent()
        coVerify(exactly = 2) { keyRepository.fetchKeyState("openAI") }
        advanceTimeBy(ExpiredKeyTtlGrace - KeyExpiryMargin)
        runCurrent()
        coVerify(exactly = 3) { keyRepository.fetchKeyState("openAI") }

        // The old 30-minute deadline was dropped, and a never-expiring key sets none.
        advanceUntilIdle()
        coVerify(exactly = 3) { keyRepository.fetchKeyState("openAI") }
        assertThat(handle.state.endpointKeyStates["openAI"]).isEqualTo(neverExpires)
    }

    @Test
    fun clearingTheGatedEndpointsCancelsTheScheduledRecompute() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val delegate = newDelegate(handle)
        coEvery { keyRepository.fetchKeyState("openAI") } returns Result.Success(setAt(clockStart + 5.minutes))

        delegate.recomputeFor(mapOf("openAI" to EndpointConfig(userProvide = true)))
        runCurrent()
        delegate.recomputeFor(emptyMap())
        advanceUntilIdle()

        coVerify(exactly = 1) { keyRepository.fetchKeyState("openAI") }
        assertThat(handle.state.endpointKeyStates).isEmpty()
    }

    @Test
    fun keyRunningOutWhileOfflineStillLocksAndStopsRetrying() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val delegate = newDelegate(handle)
        val expiresAt = clockStart + 10.minutes
        coEvery { keyRepository.fetchKeyState("openAI") } returns Result.Success(setAt(expiresAt))

        delegate.recomputeFor(mapOf("openAI" to EndpointConfig(userProvide = true)))
        runCurrent()

        // Every later fetch fails. The key locks at its expiry anyway, with no fetch.
        coEvery { keyRepository.fetchKeyState("openAI") } returns Result.Error(RuntimeException("offline"))
        advanceTimeBy(10.minutes + KeyExpiryMargin)
        runCurrent()
        coVerify(exactly = 1) { keyRepository.fetchKeyState("openAI") }
        assertThat(handle.state.endpointKeyStates["openAI"]).isEqualTo(expiredAt(expiresAt))

        // One check after the TTL window, then nothing: no deadline is left ahead.
        advanceUntilIdle()
        coVerify(exactly = 2) { keyRepository.fetchKeyState("openAI") }
        assertThat(handle.state.endpointKeyStates["openAI"]).isEqualTo(expiredAt(expiresAt))
        assertThat(handle.state.endpointKeyStates["openAI"]).isEqualTo(expiredAt(expiresAt))
    }

    @Test
    fun aServerThatStillReportsAPastKeyAsSetDoesNotLoop() = runTest {
        // A Set past its expiry can only come back from a stale source; the delegate reads it
        // as expired rather than scheduling a recompute that is already due.
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val delegate = newDelegate(handle)
        val expiresAt = clockStart - 5.minutes
        coEvery { keyRepository.fetchKeyState("openAI") } returns Result.Success(setAt(expiresAt))

        delegate.recomputeFor(mapOf("openAI" to EndpointConfig(userProvide = true)))
        advanceUntilIdle()

        coVerify(exactly = 1) { keyRepository.fetchKeyState("openAI") }
        assertThat(handle.state.endpointKeyStates["openAI"]).isEqualTo(expiredAt(expiresAt))
    }

    /** Virtual time plus [slept]: wall-clock time that passes while `delay` doesn't count it. */
    private class SleepingClock(private val scheduler: TestCoroutineScheduler, private val start: Instant) : Clock {
        var slept: Duration = Duration.ZERO
        override fun now(): Instant = start + scheduler.currentTime.milliseconds + slept
    }

    @Test
    fun aKeyThatRanOutWhileTheDeviceSleptLocksOnResettle() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val clock = SleepingClock(testScheduler, clockStart)
        val delegate = EndpointKeyStatusDelegate(EndpointKeyHandle(handle), keyRepository, clock)
        val expiresAt = clockStart + 30.minutes
        var server: KeyState = setAt(expiresAt)
        coEvery { keyRepository.fetchKeyState("openAI") } coAnswers { Result.Success(server) }

        delegate.recomputeFor(mapOf("openAI" to EndpointConfig(userProvide = true)))
        runCurrent()

        // Two hours asleep: the wall clock moves, the scheduled recompute doesn't.
        clock.slept = 2.hours
        server = KeyState.Unset
        runCurrent()
        coVerify(exactly = 1) { keyRepository.fetchKeyState("openAI") }
        assertThat(handle.state.endpointKeyStates["openAI"]).isEqualTo(setAt(expiresAt))

        // Back in front: locked at once, then confirmed with the server.
        delegate.resettle()
        assertThat(handle.state.endpointKeyStates["openAI"]).isEqualTo(expiredAt(expiresAt))
        runCurrent()
        coVerify(exactly = 2) { keyRepository.fetchKeyState("openAI") }
        assertThat(handle.state.endpointKeyStates["openAI"]).isEqualTo(KeyState.Unset)

        // The stale 30-minute timer is gone with nothing left to wait for.
        advanceUntilIdle()
        coVerify(exactly = 2) { keyRepository.fetchKeyState("openAI") }
    }

    @Test
    fun resettleReArmsTheTimerFromTheRealTimeLeft() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val clock = SleepingClock(testScheduler, clockStart)
        val delegate = EndpointKeyStatusDelegate(EndpointKeyHandle(handle), keyRepository, clock)
        val expiresAt = clockStart + 30.minutes
        coEvery { keyRepository.fetchKeyState("openAI") } returns Result.Success(setAt(expiresAt))

        delegate.recomputeFor(mapOf("openAI" to EndpointConfig(userProvide = true)))
        runCurrent()

        // Twenty minutes asleep: ten are left, not thirty, and no fetch is due yet.
        clock.slept = 20.minutes
        delegate.resettle()
        runCurrent()
        coVerify(exactly = 1) { keyRepository.fetchKeyState("openAI") }

        advanceTimeBy(10.minutes)
        runCurrent()
        assertThat(handle.state.endpointKeyStates["openAI"]).isEqualTo(setAt(expiresAt))
        advanceTimeBy(KeyExpiryMargin)
        runCurrent()
        assertThat(handle.state.endpointKeyStates["openAI"]).isEqualTo(expiredAt(expiresAt))
    }

    @Test
    fun resettleConfirmsAKeyPastItsTtlWindow() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val clock = SleepingClock(testScheduler, clockStart)
        val delegate = EndpointKeyStatusDelegate(EndpointKeyHandle(handle), keyRepository, clock)
        val expiresAt = clockStart - 10.seconds
        coEvery { keyRepository.fetchKeyState("openAI") } returns Result.Success(expiredAt(expiresAt))

        delegate.recomputeFor(mapOf("openAI" to EndpointConfig(userProvide = true)))
        runCurrent()

        // Asleep through the server's TTL pass: the key is gone, so ask rather than wait.
        coEvery { keyRepository.fetchKeyState("openAI") } returns Result.Success(KeyState.Unset)
        clock.slept = 5.minutes
        delegate.resettle()
        runCurrent()
        coVerify(exactly = 2) { keyRepository.fetchKeyState("openAI") }
        assertThat(handle.state.endpointKeyStates["openAI"]).isEqualTo(KeyState.Unset)
    }

    @Test
    fun resettleFetchesOnlyKeysPastTheirTtlWindowAndNotTwiceInAMinute() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val handle = newHandle(scope)
        val clock = SleepingClock(testScheduler, clockStart)
        val delegate = EndpointKeyStatusDelegate(EndpointKeyHandle(handle), keyRepository, clock)
        // A server whose TTL pass lags: the key still reads as expired well past the window.
        val lagging = expiredAt(clockStart - 10.minutes)
        coEvery { keyRepository.fetchKeyState("openAI") } returns Result.Success(lagging)
        coEvery { keyRepository.fetchKeyState("anthropic") } returns Result.Success(neverExpires)

        delegate.recomputeFor(
            mapOf(
                "openAI" to EndpointConfig(userProvide = true),
                "anthropic" to EndpointConfig(userProvide = true),
            ),
        )
        runCurrent()

        // Just fetched: nothing to ask again.
        clock.slept = 30.seconds
        delegate.resettle()
        runCurrent()
        coVerify(exactly = 1) { keyRepository.fetchKeyState("openAI") }

        // A minute on, the lagging key alone is asked again.
        clock.slept = ResettleRefetchInterval
        delegate.resettle()
        runCurrent()
        coVerify(exactly = 2) { keyRepository.fetchKeyState("openAI") }
        coVerify(exactly = 1) { keyRepository.fetchKeyState("anthropic") }
        assertThat(handle.state.endpointKeyStates).containsExactly("openAI", lagging, "anthropic", neverExpires)
    }

    @Test
    fun nextKeyDeadlineIgnoresDeadlinesAlreadyPassed() {
        val now = Instant.parse("2026-10-25T12:00:00Z")
        val states = mapOf(
            "a" to setAt(now + 3.minutes),
            "b" to expiredAt(now - 30.minutes),
            "c" to expiredAt(now - ExpiredKeyTtlGrace + 20.seconds),
            "d" to neverExpires,
            "e" to KeyState.Unset,
            "f" to KeyState.Loading,
        )

        // c: deleted 20 s from now, sooner than a's expiry.
        assertThat(nextKeyDeadline(states, now)).isEqualTo(20.seconds)
        assertThat(nextKeyDeadline(states - "c", now)).isEqualTo(3.minutes + KeyExpiryMargin)
        assertThat(nextKeyDeadline(mapOf("b" to states.getValue("b"), "d" to neverExpires), now)).isNull()
    }
}
