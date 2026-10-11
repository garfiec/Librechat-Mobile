package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.AgentTool
import com.garfiec.librechat.core.model.config.ImageSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.tchat.TchatModelCatalog
import com.garfiec.librechat.feature.chat.tchat.IMAGE_GEN_TOOL_ID
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The landing ViewModel resets itself after a new chat's first send hands off to Chat(id). The
 * Tchat slice is loaded once from flows that don't re-emit, so the reset must carry it over or
 * the picker loses its Image section and Free tags for the rest of the process.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TchatLandingResetTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fixture = ChatViewModelTestFixture()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fixture.stubDefaults()
        val spec = ImageSpec(name = "gpt-image", label = "GPT Image", model = "gpt-image-2")
        every { fixture.configRepository.startupConfig } returns
            MutableStateFlow(StartupConfig(modelSpecs = ModelSpecs(imageList = listOf(spec))))
        every { fixture.tchatModelCatalogRepository.catalog } returns
            MutableStateFlow(TchatModelCatalog(free = listOf("gpt-5-mini")))
        coEvery { fixture.agentRepository.getAvailableTools() } returns
            Result.Success(listOf(AgentTool(pluginKey = IMAGE_GEN_TOOL_ID)))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `navigation reset keeps the catalog and image entries`() = runTest(testDispatcher) {
        val viewModel = fixture.build(testDispatcher)
        advanceUntilIdle()
        val before = viewModel.uiState.value.tchat
        assertThat(before.catalogView.ready).isTrue()
        assertThat(before.imageGenAvailable).isTrue()

        viewModel.onPendingNavigationHandled()
        advanceUntilIdle()

        val after = viewModel.uiState.value.tchat
        assertThat(after.catalogView.ready).isTrue()
        assertThat(after.catalogView.isFree("gpt-5-mini")).isTrue()
        assertThat(after.imageGenAvailable).isTrue()
        assertThat(after.specsLoaded).isTrue()
    }
}
