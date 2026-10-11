package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.ChatLayoutConstants
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
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
class ChatLayoutSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val settingsDataStore = mockk<SettingsDataStore>(relaxed = true)

    // TCHAT: start from the (Tchat) defaults, so the first state equals ChatLayoutSettingsUiState().
    private val layoutStyle = MutableStateFlow(ChatLayoutConstants.DEFAULT)
    private val showBubbles = MutableStateFlow(ChatLayoutConstants.DEFAULT_SHOW_BUBBLES)
    private val showAvatars = MutableStateFlow(true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { settingsDataStore.chatLayoutStyle } returns layoutStyle
        every { settingsDataStore.showBubbles } returns showBubbles
        every { settingsDataStore.showAvatars } returns showAvatars
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState follows the store`() = runTest {
        val viewModel = ChatLayoutSettingsViewModel(settingsDataStore)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value).isEqualTo(ChatLayoutSettingsUiState())

        layoutStyle.value = ChatLayoutConstants.THREAD
        showBubbles.value = false
        showAvatars.value = false
        advanceUntilIdle()

        assertThat(viewModel.uiState.value).isEqualTo(
            ChatLayoutSettingsUiState(layoutStyle = ChatLayoutConstants.THREAD, showBubbles = false, showAvatars = false),
        )
    }

    @Test
    fun `setters write the store`() = runTest {
        val viewModel = ChatLayoutSettingsViewModel(settingsDataStore)

        viewModel.setLayoutStyle(ChatLayoutConstants.TWO_SIDED)
        viewModel.setShowBubbles(true)
        viewModel.setShowAvatars(false)
        advanceUntilIdle()

        coVerify { settingsDataStore.setChatLayoutStyle(ChatLayoutConstants.TWO_SIDED) }
        coVerify { settingsDataStore.setShowBubbles(true) }
        coVerify { settingsDataStore.setShowAvatars(false) }
    }
}
