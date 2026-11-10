package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.AppInfo
import com.garfiec.librechat.core.common.DistributionChannel
import com.garfiec.librechat.core.common.datetime.ClockFormat
import com.garfiec.librechat.core.common.datetime.DateFormatStyle
import com.garfiec.librechat.core.common.datetime.DateTimeFormatPrefs
import com.garfiec.librechat.core.common.datetime.TimestampStyle
import com.garfiec.librechat.core.data.datastore.AppPreferences
import com.garfiec.librechat.core.data.datastore.ArtifactDisplayMode
import com.garfiec.librechat.core.data.datastore.ArtifactDisplayPrefs
import com.garfiec.librechat.core.data.datastore.DateTimePrefsStore
import com.garfiec.librechat.core.data.datastore.PreferenceStore
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.logging.PlatformInfo
import com.garfiec.librechat.core.model.config.BuildInfo
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.network.client.CertificateAuthority
import com.garfiec.librechat.core.network.client.CertificateTrustRegistry
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
class GeneralSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)
    private val dateTimePrefsStore = mockk<DateTimePrefsStore>(relaxed = true)
    private val serverDataStore = mockk<ServerDataStore>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)
    private val selectedLanguageFlow = MutableStateFlow(SettingsDataStore.DEFAULT_LANGUAGE)
    private val dateTimePrefsFlow = MutableStateFlow(DateTimeFormatPrefs())
    private val version = MutableStateFlow<String?>(null)
    private val startupConfig = MutableStateFlow<StartupConfig?>(null)
    private val thinkingOpenFlow = MutableStateFlow(false)
    private val artifactDisplayFlow = MutableStateFlow(ArtifactDisplayPrefs())
    private val autoReadFlow = MutableStateFlow(false)
    private val certificateTrust = CertificateTrustRegistry()
    private val preferenceStore = mockk<PreferenceStore>(relaxed = true) {
        every { observe(AppPreferences.AdvancedSearch) } returns MutableStateFlow(false)
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { settingsDataStore.selectedLanguage } returns selectedLanguageFlow
        coEvery { settingsDataStore.setSelectedLanguage(any()) } answers { selectedLanguageFlow.value = firstArg() }
        every { settingsDataStore.tabletSidebarGestureEnabled } returns MutableStateFlow(true)
        every { settingsDataStore.thinkingOpenByDefault } returns thinkingOpenFlow
        every { settingsDataStore.artifactDisplayPrefs } returns artifactDisplayFlow
        every { settingsDataStore.autoReadEnabled } returns autoReadFlow
        every { dateTimePrefsStore.prefs } returns dateTimePrefsFlow
        coEvery { dateTimePrefsStore.set(any()) } answers { dateTimePrefsFlow.value = firstArg() }
        every { serverDataStore.currentUrlFlow } returns MutableStateFlow("https://chat.example.com")
        every { configRepository.startupConfig } returns startupConfig
        every { configRepository.detectedBackendVersion } returns version
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = GeneralSettingsViewModel(
        settingsDataStore = settingsDataStore,
        dateTimePrefsStore = dateTimePrefsStore,
        serverDataStore = serverDataStore,
        configRepository = configRepository,
        appInfo = object : AppInfo {
            override val versionName = "0.1.0"
            override val versionCode = 1L
            override val gitSha = "testsha0"
            override val channel = DistributionChannel.OPEN_SOURCE
        },
        preferenceStore = preferenceStore,
        certificateTrust = certificateTrust,
        platformInfo = object : PlatformInfo {
            override val osName = "Android"
            override val osVersion = "17"
            override val deviceModel = "Google Pixel 9 Pro Fold"
        },
    )

    @Test
    fun `home chat summaries follow the thinking, artifact and read-aloud prefs`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.homeChats).isEqualTo(HomeChatPrefs())

        thinkingOpenFlow.value = true
        artifactDisplayFlow.value = ArtifactDisplayPrefs(mode = ArtifactDisplayMode.FULLSCREEN)
        autoReadFlow.value = true
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.homeChats).isEqualTo(
            HomeChatPrefs(
                thinkingOpenByDefault = true,
                artifactDisplayMode = ArtifactDisplayMode.FULLSCREEN,
                autoReadEnabled = true,
            ),
        )
    }

    @Test
    fun `About notes a server trusted through a user-installed CA`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.serverTrustedViaUserCa).isFalse()

        certificateTrust.record("chat.example.com", 443, CertificateAuthority.UserInstalled)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.serverTrustedViaUserCa).isTrue()

        certificateTrust.record("chat.example.com", 443, CertificateAuthority.System)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.serverTrustedViaUserCa).isFalse()
    }

    @Test
    fun `About shows the app build, server URL and detected version`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.appVersion).isEqualTo("0.1.0")
        assertThat(viewModel.uiState.value.gitSha).isEqualTo("testsha0")
        assertThat(viewModel.uiState.value.serverUrl).isEqualTo("https://chat.example.com")
        assertThat(viewModel.uiState.value.serverVersion).isNull()

        version.value = "0.8.8"
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.serverVersion).isEqualTo("0.8.8")
    }

    @Test
    fun `setLanguage updates language and dismisses dialog`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showLanguageDialog()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.showLanguageDialog).isTrue()

        viewModel.setLanguage("fr")
        advanceUntilIdle()

        coVerify { settingsDataStore.setSelectedLanguage("fr") }
        assertThat(viewModel.uiState.value.selectedLanguage).isEqualTo("fr")
        assertThat(viewModel.uiState.value.showLanguageDialog).isFalse()
    }

    @Test
    fun `saveDateTimePrefs writes all three choices and dismisses dialog`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showDateTimeDialog()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.showDateTimeDialog).isTrue()

        val chosen = DateTimeFormatPrefs(TimestampStyle.SMART, ClockFormat.H24, DateFormatStyle.YMD_DASH)
        viewModel.saveDateTimePrefs(chosen)
        advanceUntilIdle()

        coVerify(exactly = 1) { dateTimePrefsStore.set(chosen) }
        assertThat(viewModel.uiState.value.dateTimePrefs).isEqualTo(chosen)
        assertThat(viewModel.uiState.value.showDateTimeDialog).isFalse()
    }

    @Test
    fun `dismissing the date-time dialog writes nothing`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showDateTimeDialog()
        viewModel.dismissDateTimeDialog()
        advanceUntilIdle()

        coVerify(exactly = 0) { dateTimePrefsStore.set(any()) }
        assertThat(viewModel.uiState.value.showDateTimeDialog).isFalse()
    }

    @Test
    fun `setTabletSidebarGestureEnabled writes the store`() = runTest {
        val viewModel = createViewModel()

        viewModel.setTabletSidebarGestureEnabled(false)
        advanceUntilIdle()

        coVerify { settingsDataStore.setTabletSidebarGestureEnabled(false) }
    }

    @Test
    fun `the admin panel link follows the server's adminPanelURL`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.adminPanelUrl).isNull()

        startupConfig.value = StartupConfig(adminPanelURL = "https://admin.example.com")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.adminPanelUrl).isEqualTo("https://admin.example.com")

        startupConfig.value = StartupConfig(adminPanelURL = " ")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.adminPanelUrl).isNull()
    }

    @Test
    fun `diagnostics name the app, server build and device but not the server address`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.diagnostics).isEqualTo(
            listOf(
                "Switchboard version: 0.1.0",
                "Switchboard build: testsha0",
                "LibreChat version: —",
                "Commit: —",
                "Branch: —",
                "Build date: —",
                "Device: Android 17 · Google Pixel 9 Pro Fold",
            ).joinToString("\n"),
        )

        version.value = "0.8.9-rc1"
        startupConfig.value = StartupConfig(
            buildInfo = BuildInfo(commit = "abcdef1234", commitShort = "abcdef1", branch = "main", buildDate = "2026-11-01"),
        )
        advanceUntilIdle()
        val text = viewModel.uiState.value.diagnostics
        assertThat(text).contains("LibreChat version: 0.8.9-rc1")
        assertThat(text).contains("Commit: abcdef1234")
        assertThat(text).contains("Branch: main")
        assertThat(text).contains("Build date: 2026-11-01")
        assertThat(text).doesNotContain("chat.example.com")
    }
}
