package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.AppInfo
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.McpAppFailure
import com.garfiec.librechat.core.data.repository.McpAppResult
import com.garfiec.librechat.core.data.repository.McpAppsRepository
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.mcp.McpAppsPolicy
import com.garfiec.librechat.core.model.mcp.selectToolCallUiResources
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class McpAppsViewModelTest {

    private val config = MutableStateFlow<StartupConfig?>(null)
    private val refreshes = MutableSharedFlow<StartupConfig>(extraBufferCapacity = 1)
    private val repository = mockk<McpAppsRepository>()

    private val occurrence = selectToolCallUiResources(
        listOf(
            Attachment(
                type = "ui_resources",
                toolCallId = "call_1",
                uiResources = Json.parseToJsonElement(
                    """[{"resourceId":"r1","uri":"ui://w","mimeType":"text/html;profile=mcp-app","text":"<p>x</p>",
                    "serverName":"weather","toolName":"show_forecast","serverBinding":"v1.abc"}]""",
                ),
            ),
        ),
    ).single()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { repository.validate(any(), any()) } returns McpAppResult.Success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): McpAppsViewModel {
        val configRepository = mockk<ConfigRepository>(relaxed = true)
        every { configRepository.startupConfig } returns config
        every { configRepository.startupConfigRefreshes } returns refreshes
        val appInfo = mockk<AppInfo>()
        every { appInfo.versionName } returns "1.0"
        return McpAppsViewModel(
            repository = repository,
            appInfo = appInfo,
            configRepository = configRepository,
        )
    }

    private fun enabledConfig() = StartupConfig(
        mcpApps = Json.parseToJsonElement("""{"enabled":true,"legacyHtmlEnabled":true}"""),
    )

    @Test
    fun `nothing opens while the server's policy is off`() {
        val vm = viewModel()
        vm.open(occurrence)
        assertThat(vm.session.value).isNull()
    }

    @Test
    fun `opening starts the app straight away, and close tears it down`() {
        config.value = enabledConfig()
        val vm = viewModel()

        vm.open(occurrence)
        assertThat(vm.session.value?.occurrence).isEqualTo(occurrence)

        vm.close()
        assertThat(vm.session.value).isNull()
    }

    @Test
    fun `retry starts a fresh attempt`() {
        config.value = enabledConfig()
        val vm = viewModel()
        vm.open(occurrence)
        val first = vm.session.value!!
        vm.retry()
        assertThat(vm.session.value!!.attempt).isEqualTo(first.attempt + 1)
        assertThat(vm.session.value!!.host).isNotSameInstanceAs(first.host)
    }

    @Test
    fun `apps reported off stay off until the server answers a config again, even an unchanged one`() {
        config.value = enabledConfig()
        coEvery { repository.validate(any(), any()) } returns McpAppResult.Failure(McpAppFailure.APPS_DISABLED)
        val vm = viewModel()

        vm.open(occurrence)
        assertThat(vm.policy.value).isEqualTo(McpAppsPolicy.DISABLED)

        // An equal config is deduped by the StateFlow: on its own it lifts nothing.
        config.value = enabledConfig()
        assertThat(vm.policy.value).isEqualTo(McpAppsPolicy.DISABLED)

        refreshes.tryEmit(enabledConfig())
        assertThat(vm.policy.value.enabled).isTrue()
    }
}
