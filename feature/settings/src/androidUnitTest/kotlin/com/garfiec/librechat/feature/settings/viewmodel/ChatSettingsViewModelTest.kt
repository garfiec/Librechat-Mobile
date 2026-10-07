package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.ChatLayoutConstants
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.LatexRenderer
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.datastore.UploadRoutingMode
import com.garfiec.librechat.core.data.repository.SpeechRepository
import com.garfiec.librechat.core.model.speech.SpeechConfig
import com.garfiec.librechat.feature.settings.viewmodel.delegate.SpeechSettingsContract
import com.garfiec.librechat.feature.settings.viewmodel.delegate.SpeechSettingsFactory
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val speechSettingsContract = mockk<SpeechSettingsContract>(relaxed = true)
    private val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
    private val uploadRoutingModeFlow = MutableStateFlow(UploadRoutingMode.AUTO)
    private val siteIconsChoiceFlow = MutableStateFlow<Boolean?>(null)
    private val speechRepository = mockk<SpeechRepository>(relaxed = true)

    private lateinit var viewModel: ChatSettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Setup DataStore flows
        every { settingsDataStore.chatFontSize } returns MutableStateFlow(ChatFontSize.MEDIUM)
        every { settingsDataStore.autoScrollEnabled } returns MutableStateFlow(true)
        every { settingsDataStore.showThinkingBlocks } returns MutableStateFlow(true)
        every { settingsDataStore.autoReadEnabled } returns MutableStateFlow(false)
        every { settingsDataStore.selectedVoiceId } returns MutableStateFlow(null)
        every { settingsDataStore.showImageDescriptions } returns MutableStateFlow(false)
        every { settingsDataStore.dismissKeyboardOnSend } returns MutableStateFlow(false)
        every { settingsDataStore.ttsSource } returns MutableStateFlow("device")
        every { settingsDataStore.ttsSpeechRate } returns MutableStateFlow(1.0f)
        every { settingsDataStore.ttsPitch } returns MutableStateFlow(1.0f)
        every { settingsDataStore.ttsVoiceName } returns MutableStateFlow("")
        every { settingsDataStore.ttsEngine } returns MutableStateFlow("")
        every { settingsDataStore.ttsVoice } returns MutableStateFlow("")
        every { settingsDataStore.ttsCaching } returns MutableStateFlow(true)
        every { settingsDataStore.autoSendAfterStt } returns MutableStateFlow(false)
        every { settingsDataStore.sttEngine } returns MutableStateFlow("")
        every { settingsDataStore.sttLanguage } returns MutableStateFlow("")
        every { settingsDataStore.sttOnDevice } returns MutableStateFlow(true)
        every { settingsDataStore.sttEndOfSpeech } returns MutableStateFlow(false)
        // The preferences state is one combine chain, so a source that never emits stalls all of it —
        // an unstubbed flow here shows up as an unrelated setting silently keeping its default.
        every { settingsDataStore.siteIconsChoice } returns siteIconsChoiceFlow
        coEvery { settingsDataStore.setShowSiteIcons(any()) } answers { siteIconsChoiceFlow.value = firstArg() }
        every { settingsDataStore.chatLayoutStyle } returns MutableStateFlow(ChatLayoutConstants.THREAD)
        every { settingsDataStore.showAvatars } returns MutableStateFlow(true)
        every { settingsDataStore.showBubbles } returns MutableStateFlow(false)
        every { settingsDataStore.latexRenderer } returns MutableStateFlow(LatexRenderer.KATEX)
        every { settingsDataStore.uploadRoutingMode } returns uploadRoutingModeFlow
        coEvery { settingsDataStore.setUploadRoutingMode(any()) } answers { uploadRoutingModeFlow.value = firstArg() }

        // Setup default API responses
        coEvery { speechRepository.getVoices() } returns Result.Success(emptyList())
        coEvery { speechRepository.getSpeechConfig() } returns Result.Success(SpeechConfig())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ChatSettingsViewModel(
        settingsDataStore = settingsDataStore,
        speechSettingsFactory = SpeechSettingsFactory { speechSettingsContract },
    )

    @Test
    fun `site icons read as off until chosen, then follow the stored choice`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.showSiteIcons).isFalse()

        viewModel.setShowSiteIcons(true)
        advanceUntilIdle()

        coVerify { settingsDataStore.setShowSiteIcons(true) }
        assertThat(viewModel.uiState.value.showSiteIcons).isTrue()
    }

    @Test
    fun `setForkMode updates mode and dismisses dialog`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showForkSettingsDialog()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.showForkSettingsDialog).isTrue()

        viewModel.setForkMode("allBranches")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.forkMode).isEqualTo("allBranches")
        assertThat(viewModel.uiState.value.showForkSettingsDialog).isFalse()
    }

    @Test
    fun `setUploadRoutingMode persists and surfaces the new mode`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.uploadRoutingMode).isEqualTo(UploadRoutingMode.AUTO)

        viewModel.setUploadRoutingMode(UploadRoutingMode.MANUAL)
        advanceUntilIdle()

        coVerify { settingsDataStore.setUploadRoutingMode(UploadRoutingMode.MANUAL) }
        assertThat(viewModel.uiState.value.uploadRoutingMode).isEqualTo(UploadRoutingMode.MANUAL)
    }

    /**
     * The Settings screen's own MCP dialog is the second save path (McpServerDelegate); a key
     * binding refusal has to reach its state too, or that dialog reports a failure every retry
     * repeats.
     */
}
