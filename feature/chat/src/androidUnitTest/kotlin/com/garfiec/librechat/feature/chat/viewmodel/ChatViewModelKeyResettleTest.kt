package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.feature.chat.util.blocksSelection
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * A key that runs out while the device sleeps must lock its endpoint when chat comes back or the
 * picker opens. The expiry timer is a `delay`, which sleep doesn't advance, so only the
 * [ChatViewModel.onResume] and [ChatViewModel.prepareModelSelector] re-settles catch it.
 *
 * Built on the real ViewModel so the wiring is covered, not just the delegate: the wall clock moves
 * past the expiry while the scheduler stands still (the timer slept), and the tests drive with
 * [runCurrent] so the pending `delay` never fires on its own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelKeyResettleTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fixture = ChatViewModelTestFixture()

    /** A wall clock the test moves by hand. */
    private class MovableClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }

    private val start = Instant.parse("2026-10-26T12:00:00Z")
    private val clock = MovableClock(start)
    private val expiresAt = start + 10.minutes
    private val storedKey = KeyState.Set(expiresAt = expiresAt, neverExpires = false, wire = expiresAt.toString())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fixture.stubDefaults()
        every { fixture.configRepository.endpointConfigs } returns
            MutableStateFlow(mapOf(ENDPOINT to EndpointConfig(userProvide = true)))
        coEvery { fixture.keyRepository.fetchKeyState(any()) } returns Result.Success(storedKey)
        coEvery { fixture.conversationRepository.getConversation(any(), any()) } returns Result.Error(message = "test")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aKeyThatRanOutWhileTheDeviceSleptLocksWhenChatResumes() = resettleTest { vm ->
        vm.onResume()
        runCurrent()

        assertThat(vm.uiState.value.endpointKeyStates[ENDPOINT])
            .isEqualTo(KeyState.Expired(expiresAt = expiresAt, wire = storedKey.wire))
        assertThat(vm.uiState.value.endpointKeyStates[ENDPOINT].blocksSelection()).isTrue()
    }

    @Test
    fun aKeyThatRanOutWhileTheDeviceSleptLocksWhenThePickerOpens() = resettleTest { vm ->
        vm.prepareModelSelector()
        runCurrent()

        assertThat(vm.uiState.value.endpointKeyStates[ENDPOINT])
            .isEqualTo(KeyState.Expired(expiresAt = expiresAt, wire = storedKey.wire))
        assertThat(vm.uiState.value.endpointKeyStates[ENDPOINT].blocksSelection()).isTrue()
    }

    @Test
    fun aKeyStillLiveOnResumeStaysSelectable() = runTest(testDispatcher) {
        val vm = fixture.build(defaultDispatcher = testDispatcher, clock = clock)
        runCurrent()
        try {
            clock.now = start + 5.minutes
            vm.onResume()
            vm.prepareModelSelector()
            runCurrent()

            assertThat(vm.uiState.value.endpointKeyStates[ENDPOINT]).isEqualTo(storedKey)
            assertThat(vm.uiState.value.endpointKeyStates[ENDPOINT].blocksSelection()).isFalse()
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    /**
     * Opens chat with the key live, then moves the wall clock past its expiry without advancing
     * the scheduler, and checks the key still reads as set: nothing but [body]'s call may lock it.
     */
    private fun resettleTest(body: suspend TestScope.(ChatViewModel) -> Unit) = runTest(testDispatcher) {
        val vm = fixture.build(defaultDispatcher = testDispatcher, clock = clock)
        runCurrent()
        try {
            assertThat(vm.uiState.value.endpointKeyStates[ENDPOINT]).isEqualTo(storedKey)

            clock.now = expiresAt + 1.minutes
            runCurrent()
            assertThat(vm.uiState.value.endpointKeyStates[ENDPOINT]).isEqualTo(storedKey)

            body(vm)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    private companion object {
        const val ENDPOINT = "openAI"
    }
}
