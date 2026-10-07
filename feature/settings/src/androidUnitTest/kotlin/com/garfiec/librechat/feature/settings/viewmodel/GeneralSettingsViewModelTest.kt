package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.AppInfo
import com.garfiec.librechat.core.common.datetime.ClockFormat
import com.garfiec.librechat.core.common.datetime.DateFormatStyle
import com.garfiec.librechat.core.common.datetime.DateTimeFormatPrefs
import com.garfiec.librechat.core.common.datetime.TimestampStyle
import com.garfiec.librechat.core.data.datastore.DateTimePrefsStore
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.repository.ConfigRepository
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

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { settingsDataStore.selectedLanguage } returns selectedLanguageFlow
        coEvery { settingsDataStore.setSelectedLanguage(any()) } answers { selectedLanguageFlow.value = firstArg() }
        every { settingsDataStore.tabletSidebarGestureEnabled } returns MutableStateFlow(true)
        every { dateTimePrefsStore.prefs } returns dateTimePrefsFlow
        coEvery { dateTimePrefsStore.set(any()) } answers { dateTimePrefsFlow.value = firstArg() }
        every { serverDataStore.currentUrlFlow } returns MutableStateFlow("https://chat.example.com")
        every { configRepository.startupConfig } returns MutableStateFlow(null)
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
        },
    )

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
}
