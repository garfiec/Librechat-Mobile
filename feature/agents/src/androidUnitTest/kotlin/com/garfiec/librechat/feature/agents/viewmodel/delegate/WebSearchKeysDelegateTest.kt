package com.garfiec.librechat.feature.agents.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.WebSearchReadinessRepository
import com.garfiec.librechat.core.model.AuthType
import com.garfiec.librechat.core.model.ToolAuthResult
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorStateHandle
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorUiState
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WebSearchKeysDelegateTest {

    private val readiness = mockk<WebSearchReadinessRepository>()

    private suspend fun TestScope.editor(
        answer: ToolAuthResult?,
        block: suspend (WebSearchKeysDelegate, MutableStateFlow<AgentEditorUiState>) -> Unit,
    ) {
        every { readiness.account } returns MutableStateFlow("acct")
        every { readiness.changes } returns MutableStateFlow(0)
        coEvery { readiness.cached(any()) } returns null
        coEvery { readiness.check(any()) } returns answer
        val config = mockk<ConfigRepository>(relaxed = true)
        every { config.startupConfig } returns MutableStateFlow(StartupConfig())
        val flow = MutableStateFlow(AgentEditorUiState())
        val observerScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val delegate = WebSearchKeysDelegate(AgentEditorStateHandle(flow, observerScope), readiness, config)
        delegate.observe()
        try {
            block(delegate, flow)
        } finally {
            observerScope.cancel()
        }
    }

    @Test
    fun `turning web search on without the user's keys opens the key sheet instead`() = runTest {
        editor(missingKeys) { delegate, state ->
            delegate.onWebSearchToggled(true)

            assertThat(state.value.webSearchEnabled).isFalse()
            assertThat(state.value.showWebSearchKeysSheet).isTrue()
            assertThat(state.value.webSearchNeedsKeys).isTrue()
        }
    }

    @Test
    fun `turning it on with keys set, keys the server holds, or no answer enables it`() = runTest {
        listOf(keysSet, ToolAuthResult(authenticated = true, message = AuthType.SYSTEM_DEFINED), null).forEach { answer ->
            editor(answer) { delegate, state ->
                delegate.onWebSearchToggled(true)

                assertThat(state.value.webSearchEnabled).isTrue()
                assertThat(state.value.showWebSearchKeysSheet).isFalse()
            }
        }
    }

    @Test
    fun `turning it off never asks for keys`() = runTest {
        editor(missingKeys) { delegate, state ->
            state.value = state.value.copy(webSearchEnabled = true)

            delegate.onWebSearchToggled(false)

            assertThat(state.value.webSearchEnabled).isFalse()
            assertThat(state.value.showWebSearchKeysSheet).isFalse()
        }
    }

    @Test
    fun `saving keys turns web search on, a failed save leaves it off`() = runTest {
        editor(missingKeys) { delegate, state ->
            coEvery { readiness.saveKeys(any()) } returns Result.Error(RuntimeException("500"))
            assertThat(delegate.saveKeys(mapOf("serperApiKey" to "k"))).isFalse()
            assertThat(state.value.webSearchEnabled).isFalse()

            coEvery { readiness.saveKeys(any()) } returns Result.Success(Unit)
            assertThat(delegate.saveKeys(mapOf("serperApiKey" to "k"))).isTrue()
            assertThat(state.value.webSearchEnabled).isTrue()
        }
    }

    @Test
    fun `removing keys turns web search off`() = runTest {
        editor(keysSet) { delegate, state ->
            state.value = state.value.copy(webSearchEnabled = true)
            coEvery { readiness.removeKeys() } returns Result.Success(Unit)

            assertThat(delegate.removeKeys()).isTrue()

            assertThat(state.value.webSearchEnabled).isFalse()
        }
    }

    private companion object {
        val userProvided =
            listOf(listOf("providers", AuthType.USER_PROVIDED), listOf("scrapers", AuthType.USER_PROVIDED))
        val missingKeys = ToolAuthResult(authenticated = false, authTypes = userProvided)
        val keysSet = ToolAuthResult(authenticated = true, message = AuthType.USER_PROVIDED, authTypes = userProvided)
    }
}
