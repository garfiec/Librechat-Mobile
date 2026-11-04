package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.data.datastore.ServerAccent
import com.garfiec.librechat.core.data.datastore.ThemeDataStore
import com.garfiec.librechat.core.data.datastore.ThemeMode
import com.garfiec.librechat.core.model.ui.UiStyle
import com.google.common.truth.Truth.assertThat
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
class AppearanceSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val themeDataStore = mockk<ThemeDataStore>(relaxed = true)
    private val storedStyle = MutableStateFlow<UiStyle?>(null)
    private val serverAccent = MutableStateFlow<ServerAccent?>(null)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { themeDataStore.themeMode } returns MutableStateFlow(ThemeMode.SYSTEM)
        every { themeDataStore.uiStyle } returns storedStyle
        every { themeDataStore.accentColor } returns MutableStateFlow(ThemeDataStore.DEFAULT_ACCENT_COLOR)
        every { themeDataStore.useDynamicColor } returns MutableStateFlow(false)
        every { themeDataStore.serverAccent } returns serverAccent
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setThemeMode writes the theme`() = runTest {
        val viewModel = AppearanceSettingsViewModel(themeDataStore)

        viewModel.setThemeMode(ThemeMode.DARK)
        advanceUntilIdle()

        coVerify { themeDataStore.setThemeMode(ThemeMode.DARK) }
    }

    @Test
    fun `setUiStyle to the platform default clears the stored choice`() = runTest {
        val viewModel = AppearanceSettingsViewModel(themeDataStore)

        // Android's platform default is Material: storing it would pin the user to today's
        // default instead of following the platform.
        viewModel.setUiStyle(UiStyle.MATERIAL)
        advanceUntilIdle()

        coVerify { themeDataStore.setUiStyle(null) }
    }

    @Test
    fun `setUiStyle away from the platform default stores it`() = runTest {
        val viewModel = AppearanceSettingsViewModel(themeDataStore)

        viewModel.setUiStyle(UiStyle.LIQUID_GLASS)
        advanceUntilIdle()

        coVerify { themeDataStore.setUiStyle(UiStyle.LIQUID_GLASS) }
    }

    @Test
    fun `uiState resolves an unset style to the platform default and a stored one as stored`() = runTest {
        val viewModel = AppearanceSettingsViewModel(themeDataStore)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.uiStyle).isEqualTo(UiStyle.MATERIAL)

        storedStyle.value = UiStyle.LIQUID_GLASS
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.uiStyle).isEqualTo(UiStyle.LIQUID_GLASS)
    }

    @Test
    fun `a server accent that is on is the one shown, and the user's stays theirs`() = runTest {
        val viewModel = AppearanceSettingsViewModel(themeDataStore)
        serverAccent.value = ServerAccent(serverId = "srv1", name = "Acme", seedArgb = 0xFFFAFF69.toInt(), enabled = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.accentSetByServer).isTrue()
        assertThat(state.shownAccentColor).isEqualTo(0xFFFAFF69.toInt())
        assertThat(state.accentColor).isEqualTo(ThemeDataStore.DEFAULT_ACCENT_COLOR)
    }

    @Test
    fun `turning the server accent off shows the user's again`() = runTest {
        val viewModel = AppearanceSettingsViewModel(themeDataStore)
        serverAccent.value = ServerAccent(serverId = "srv1", name = "Acme", seedArgb = 0xFFFAFF69.toInt(), enabled = false)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.accentSetByServer).isFalse()
        assertThat(viewModel.uiState.value.shownAccentColor).isEqualTo(ThemeDataStore.DEFAULT_ACCENT_COLOR)
    }

    @Test
    fun `the switch is written for the server whose accent is shown`() = runTest {
        val viewModel = AppearanceSettingsViewModel(themeDataStore)
        serverAccent.value = ServerAccent(serverId = "srv1", name = "Acme", seedArgb = 0xFFFAFF69.toInt(), enabled = true)
        advanceUntilIdle()

        viewModel.setUseServerAccent(false)
        advanceUntilIdle()

        coVerify { themeDataStore.setUseServerAccent("srv1", false) }
    }
}
