package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.AppPreferences
import com.garfiec.librechat.core.data.datastore.ArtifactDisplayMode
import com.garfiec.librechat.core.data.datastore.ArtifactLibraryConsentStore
import com.garfiec.librechat.core.data.datastore.ArtifactLibraryConsentStore.Decision
import com.garfiec.librechat.core.data.datastore.FlaggedArtifactLibraries
import com.garfiec.librechat.core.data.datastore.LatexRenderer
import com.garfiec.librechat.core.data.datastore.PreferenceStore
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.datastore.UploadRoutingMode
import com.garfiec.librechat.core.data.datastore.WebSearchPreference
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.SpeechRepository
import com.garfiec.librechat.core.model.config.InterfaceConfig
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.speech.SpeechConfig
import com.garfiec.librechat.core.model.speech.TtsVoice
import com.garfiec.librechat.feature.settings.viewmodel.delegate.SpeechSettingsContract
import com.garfiec.librechat.feature.settings.viewmodel.delegate.SpeechSettingsFactory
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
    private val preferenceStore = mockk<PreferenceStore>(relaxed = true)
    private val uploadRoutingModeFlow = MutableStateFlow(UploadRoutingMode.AUTO)
    private val siteIconsChoiceFlow = MutableStateFlow<Boolean?>(null)
    private val preferLastUsedFlow = MutableStateFlow(false)
    private val webSearchFirstFlow = MutableStateFlow(WebSearchPreference.LIBRECHAT_FIRST)
    private val ttsSourceFlow = MutableStateFlow(SettingsDataStore.TTS_SOURCE_DEVICE)
    private val selectedVoiceIdFlow = MutableStateFlow<String?>(null)
    private var speechStateHandle: SettingsStateHandle<ChatSettingsUiState>? = null
    private val speechRepository = mockk<SpeechRepository>(relaxed = true)
    private val libraryConsent = mockk<ArtifactLibraryConsentStore>(relaxed = true)
    private val startupConfig = MutableStateFlow<StartupConfig?>(null)
    private val backendVersion = MutableStateFlow<String?>(null)
    private val configRepository = mockk<ConfigRepository>(relaxed = true) {
        every { startupConfig } returns this@ChatSettingsViewModelTest.startupConfig
        every { detectedBackendVersion } returns backendVersion
    }
    private val roleRepository = mockk<RoleRepository>(relaxed = true) {
        every { userPermissions } returns MutableStateFlow(null)
    }

    private lateinit var viewModel: ChatSettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { libraryConsent.snapshot } returns MutableStateFlow(null)

        // Setup DataStore flows
        every { settingsDataStore.thinkingOpenByDefault } returns MutableStateFlow(false)
        every { settingsDataStore.autoReadEnabled } returns MutableStateFlow(false)
        every { settingsDataStore.selectedVoiceId } returns selectedVoiceIdFlow
        every { settingsDataStore.showImageDescriptions } returns MutableStateFlow(false)
        every { settingsDataStore.dismissKeyboardOnSend } returns MutableStateFlow(false)
        every { settingsDataStore.ttsSource } returns ttsSourceFlow
        every { settingsDataStore.ttsSpeechRate } returns MutableStateFlow(1.5f)
        every { settingsDataStore.ttsPitch } returns MutableStateFlow(0.75f)
        every { settingsDataStore.ttsVoiceName } returns MutableStateFlow("en-gb-x-rjs-local")
        every { settingsDataStore.autoSendAfterStt } returns MutableStateFlow(false)
        every { settingsDataStore.sttEngine } returns MutableStateFlow("")
        every { settingsDataStore.sttLanguage } returns MutableStateFlow("")
        every { settingsDataStore.sttOnDevice } returns MutableStateFlow(true)
        every { settingsDataStore.sttEndOfSpeech } returns MutableStateFlow(false)
        // The preferences state is one combine chain, so a source that never emits stalls all of it —
        // an unstubbed flow here shows up as an unrelated setting silently keeping its default.
        every { preferenceStore.observe(AppPreferences.SiteIcons) } returns siteIconsChoiceFlow
        every { preferenceStore.observe(AppPreferences.PreferLastUsedModel) } returns preferLastUsedFlow
        coEvery { preferenceStore.set(AppPreferences.PreferLastUsedModel, any()) } answers { preferLastUsedFlow.value = secondArg() }
        every { preferenceStore.observe(AppPreferences.WebSearchFirst) } returns webSearchFirstFlow
        coEvery { preferenceStore.set(AppPreferences.WebSearchFirst, any()) } answers { webSearchFirstFlow.value = secondArg() }
        every { preferenceStore.observe(AppPreferences.CollapseLongUserMessages) } returns MutableStateFlow(true)
        every { preferenceStore.observe(AppPreferences.ClientImageResize) } returns MutableStateFlow(true)
        every { preferenceStore.observe(AppPreferences.ArtifactDisplay) } returns MutableStateFlow(ArtifactDisplayMode.AUTOMATIC)
        every { preferenceStore.initial(AppPreferences.ArtifactDisplay) } returns ArtifactDisplayMode.AUTOMATIC
        every { preferenceStore.initial(AppPreferences.SiteIcons) } returns null
        coEvery { preferenceStore.set(AppPreferences.SiteIcons, any()) } answers { siteIconsChoiceFlow.value = secondArg() }
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
        preferenceStore = preferenceStore,
        speechSettingsFactory = SpeechSettingsFactory { handle ->
            speechStateHandle = handle
            speechSettingsContract
        },
        libraryConsent = libraryConsent,
        fileRepository = io.mockk.mockk(relaxed = true),
        configRepository = configRepository,
        roleRepository = roleRepository,
    )

    @Test
    fun `library choices wait for the stored answers, then list every flagged library`() = runTest {
        val snapshots = MutableStateFlow<ArtifactLibraryConsentStore.Snapshot?>(null)
        every { libraryConsent.snapshot } returns snapshots
        viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.artifactLibraries.value).isNull()

        val answered = mockk<ArtifactLibraryConsentStore.Snapshot>()
        val first = FlaggedArtifactLibraries.SUBJECTS.first()
        every { answered.remembered(any()) } returns null
        every { answered.remembered(first) } returns false
        snapshots.value = answered
        advanceUntilIdle()

        val choices = viewModel.artifactLibraries.value!!
        assertThat(choices.map { it.subject }).containsExactlyElementsIn(FlaggedArtifactLibraries.SUBJECTS).inOrder()
        assertThat(choices.first().decision).isEqualTo(Decision.DECLINE)
        assertThat(choices.drop(1).map { it.decision }).containsNoneOf(Decision.ALLOW, Decision.DECLINE)
    }

    @Test
    fun `setting a library writes a remembered answer, and Ask forgets it`() = runTest {
        every { libraryConsent.snapshot } returns MutableStateFlow(null)
        viewModel = createViewModel()
        val subject = FlaggedArtifactLibraries.SUBJECTS.first()

        viewModel.setArtifactLibraryDecision(subject, Decision.ALLOW)
        viewModel.setArtifactLibraryDecision(subject, Decision.DECLINE)
        viewModel.setArtifactLibraryDecision(subject, Decision.ASK)
        advanceUntilIdle()

        coVerify { libraryConsent.remember(subject, true) }
        coVerify { libraryConsent.remember(subject, false) }
        coVerify { libraryConsent.forget(subject) }
    }

    @Test
    fun `site icons read as off until chosen, then follow the stored choice`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.showSiteIcons).isFalse()

        viewModel.setShowSiteIcons(true)
        advanceUntilIdle()

        coVerify { preferenceStore.set(AppPreferences.SiteIcons, true) }
        assertThat(viewModel.uiState.value.showSiteIcons).isTrue()
    }

    @Test
    fun `hide keyboard after sending reads on until the store answers`() = runTest {
        every { settingsDataStore.dismissKeyboardOnSend } returns MutableSharedFlow()
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.dismissKeyboardOnSend).isTrue()
    }

    @Test
    fun `new chats start on the server's choice until the user prefers last-used`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.preferLastUsedModel).isFalse()

        viewModel.setPreferLastUsedModel(true)
        advanceUntilIdle()

        coVerify { preferenceStore.set(AppPreferences.PreferLastUsedModel, true) }
        assertThat(viewModel.uiState.value.preferLastUsedModel).isTrue()
    }

    @Test
    fun `web search tries LibreChat search first until the user prefers the built-in one`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.webSearchFirst).isEqualTo(WebSearchPreference.LIBRECHAT_FIRST)

        viewModel.setWebSearchFirst(WebSearchPreference.BUILT_IN_FIRST)
        advanceUntilIdle()

        coVerify { preferenceStore.set(AppPreferences.WebSearchFirst, WebSearchPreference.BUILT_IN_FIRST) }
        assertThat(viewModel.uiState.value.webSearchFirst).isEqualTo(WebSearchPreference.BUILT_IN_FIRST)
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

    @Test
    fun `opening the page loads the speech config and leaves voices to it`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // The delegate fetches voices only once the config says the server has TTS.
        verify(exactly = 1) { speechSettingsContract.loadSpeechConfig() }
        verify(exactly = 0) { speechSettingsContract.loadVoices() }
    }

    @Test
    fun `opening the Voice page retries a config that failed, else voices a TTS server didn't list`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        every { speechSettingsContract.speechConfigLoaded } returns false
        viewModel.retrySpeechConfig()
        verify(exactly = 2) { speechSettingsContract.loadSpeechConfig() }

        every { speechSettingsContract.speechConfigLoaded } returns true
        viewModel.retrySpeechConfig()
        verify(exactly = 2) { speechSettingsContract.loadSpeechConfig() }
        verify(exactly = 0) { speechSettingsContract.loadVoices() }

        speechStateHandle!!.update { copy(serverTtsEnabled = true) }
        viewModel.retrySpeechConfig()
        verify(exactly = 1) { speechSettingsContract.loadVoices() }
    }

    @Test
    fun `device voices load when the Voice page asks, once`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        verify(exactly = 0) { speechSettingsContract.loadDeviceVoices() }

        viewModel.loadDeviceVoices()
        viewModel.loadDeviceVoices()

        verify(exactly = 1) { speechSettingsContract.loadDeviceVoices() }
    }

    @Test
    fun `a device preview speaks with the saved speed, pitch and voice`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.previewVoice("Hello")

        verify { speechSettingsContract.previewDeviceTts("Hello", 1.5f, 0.75f, "en-gb-x-rjs-local") }
    }

    @Test
    fun `a server preview asks for the chosen server voice`() = runTest {
        ttsSourceFlow.value = SettingsDataStore.TTS_SOURCE_SERVER
        selectedVoiceIdFlow.value = "nova"
        viewModel = createViewModel()
        val voices = listOf(TtsVoice(id = "alloy", name = "alloy"), TtsVoice(id = "nova", name = "nova"))
        speechStateHandle!!.update { copy(availableVoices = voices) }
        advanceUntilIdle()

        viewModel.previewVoice("Hello")

        verify { speechSettingsContract.previewServerTts("Hello", "nova") }
    }

    @Test
    fun `choosing a server voice saves its id, the one chat playback sends`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectServerVoice("nova")
        advanceUntilIdle()

        coVerify { settingsDataStore.setSelectedVoiceId("nova") }
    }

    @Test
    fun `context usage shows only where chat's gauge does`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        // Unknown server version: chat hides the gauge, so the row hides too.
        assertThat(viewModel.gates.value.contextUsage).isFalse()

        backendVersion.value = "0.8.7"
        advanceUntilIdle()
        assertThat(viewModel.gates.value.contextUsage).isTrue()

        startupConfig.value = StartupConfig(interfaceConfig = InterfaceConfig(contextUsage = false))
        advanceUntilIdle()
        assertThat(viewModel.gates.value.contextUsage).isFalse()

        startupConfig.value = null
        backendVersion.value = "0.8.6"
        advanceUntilIdle()
        assertThat(viewModel.gates.value.contextUsage).isFalse()
    }

    @Test
    fun `presets follow interface presets and modelSelect`() {
        val iface = InterfaceConfig()
        assertThat(chatSettingsGates(null, null, null).presets).isTrue()
        assertThat(chatSettingsGates(StartupConfig(interfaceConfig = iface), null, null).presets).isTrue()
        assertThat(chatSettingsGates(StartupConfig(interfaceConfig = iface.copy(presets = false)), null, null).presets)
            .isFalse()
        assertThat(chatSettingsGates(StartupConfig(interfaceConfig = iface.copy(modelSelect = false)), null, null).presets)
            .isFalse()
    }

    @Test
    fun `API keys follow the remote agents USE permission`() {
        fun role(use: Boolean) = UserRolePermissions(name = "USER", permissions = mapOf("REMOTE_AGENTS" to mapOf("USE" to use)))
        assertThat(chatSettingsGates(null, null, null).apiKeys).isTrue()
        assertThat(chatSettingsGates(null, null, role(use = true)).apiKeys).isTrue()
        assertThat(chatSettingsGates(null, null, role(use = false)).apiKeys).isFalse()
    }
}
