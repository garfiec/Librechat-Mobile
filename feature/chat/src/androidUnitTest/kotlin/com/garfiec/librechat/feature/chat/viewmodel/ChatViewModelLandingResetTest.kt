package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.ArtifactsMode
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.endpoint.KeyState
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** What the landing keeps when it resets after handing a new chat off (`onPendingNavigationHandled`). */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelLandingResetTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fixture = ChatViewModelTestFixture()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fixture.stubDefaults()
        with(fixture) {
            every { configRepository.endpointConfigs } returns MutableStateFlow(
                mapOf(KEYED to EndpointConfig(userProvide = true)),
            )
            coEvery { keyRepository.fetchKeyState(any()) } returns Result.Success(KeyState.Unset)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the next new chat still knows which endpoints need the user's key`() = runTest(testDispatcher) {
        val vm = fixture.build(defaultDispatcher = testDispatcher)
        try {
            advanceUntilIdle()
            assertEquals(mapOf(KEYED to KeyState.Unset), vm.uiState.value.endpointKeyStates)

            // The configs are a StateFlow and don't re-emit, so nothing would fetch the states again.
            vm.onPendingNavigationHandled()
            advanceUntilIdle()

            assertEquals(mapOf(KEYED to KeyState.Unset), vm.uiState.value.endpointKeyStates)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun `the next new chat keeps the Artifacts mode the user chose`() = runTest(testDispatcher) {
        every { fixture.settingsDataStore.artifactsMode } returns MutableStateFlow(ArtifactsMode.SHADCN_UI)
        val vm = fixture.build(defaultDispatcher = testDispatcher)
        try {
            advanceUntilIdle()
            assertEquals(ArtifactsMode.SHADCN_UI, vm.uiState.value.selection.artifactsMode)

            vm.onPendingNavigationHandled()
            advanceUntilIdle()

            assertEquals(ArtifactsMode.SHADCN_UI, vm.uiState.value.selection.artifactsMode)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    private companion object {
        const val KEYED = "Groq Proxy"
    }
}
