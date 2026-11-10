package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.data.datastore.ServerTheme
import com.garfiec.librechat.core.data.datastore.ThemeDataStore
import com.garfiec.librechat.core.data.datastore.ThemeMode
import com.garfiec.librechat.core.model.config.theme.ServerThemeDefinition
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
    private val serverTheme = MutableStateFlow<ServerTheme?>(null)
    private val acme = ServerThemeDefinition("Acme", light = emptyMap(), dark = emptyMap())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { themeDataStore.themeMode } returns MutableStateFlow(ThemeMode.SYSTEM)
        every { themeDataStore.uiStyle } returns storedStyle
        every { themeDataStore.accentColor } returns MutableStateFlow(ThemeDataStore.DEFAULT_ACCENT_COLOR)
        every { themeDataStore.useDynamicColor } returns MutableStateFlow(false)
        every { themeDataStore.serverTheme } returns serverTheme
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
    fun `a server theme that is on puts its colours on screen, and the user's accent stays theirs`() = runTest {
        val viewModel = AppearanceSettingsViewModel(themeDataStore)
        serverTheme.value = ServerTheme(serverId = "srv1", definition = acme, enabled = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.serverColorsOn).isTrue()
        assertThat(state.accentColor).isEqualTo(ThemeDataStore.DEFAULT_ACCENT_COLOR)
    }

    @Test
    fun `picking Switchboard's colours takes the server's off screen`() = runTest {
        val viewModel = AppearanceSettingsViewModel(themeDataStore)
        serverTheme.value = ServerTheme(serverId = "srv1", definition = acme, enabled = false)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.serverColorsOn).isFalse()
    }

    @Test
    fun `the choice is written for the server whose theme is shown`() = runTest {
        val viewModel = AppearanceSettingsViewModel(themeDataStore)
        serverTheme.value = ServerTheme(serverId = "srv1", definition = acme, enabled = true)
        advanceUntilIdle()

        viewModel.setUseServerTheme(false)
        advanceUntilIdle()

        coVerify { themeDataStore.setUseServerTheme("srv1", false) }
    }
}
