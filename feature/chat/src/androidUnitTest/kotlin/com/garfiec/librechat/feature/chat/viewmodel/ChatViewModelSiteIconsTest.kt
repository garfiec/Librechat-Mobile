package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.ChatLayoutConstants
import com.garfiec.librechat.core.data.datastore.InlineArtifactPrefs
import com.garfiec.librechat.core.data.datastore.LatexRenderer
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
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
 * The website-icons preference as the chat screen sees it: off and silent until preferences load,
 * then a stored choice wins, and with none the prompt shows unless it was closed this process.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelSiteIconsTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fixture = ChatViewModelTestFixture()
    private val settingsDataStore get() = fixture.settingsDataStore
    private val siteIconsChoice = MutableStateFlow<Boolean?>(null)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fixture.stubDefaults()
        // chatPreferences is one combine over every source; a relaxed (empty) flow would stall it.
        every { settingsDataStore.showImageDescriptions } returns MutableStateFlow(false)
        every { settingsDataStore.dismissKeyboardOnSend } returns MutableStateFlow(false)
        every { settingsDataStore.chatLayoutStyle } returns MutableStateFlow(ChatLayoutConstants.THREAD)
        every { settingsDataStore.showAvatars } returns MutableStateFlow(true)
        every { settingsDataStore.showBubbles } returns MutableStateFlow(false)
        every { settingsDataStore.latexRenderer } returns MutableStateFlow(LatexRenderer.KATEX)
        every { settingsDataStore.autoSendAfterStt } returns MutableStateFlow(false)
        every { settingsDataStore.sttEngine } returns MutableStateFlow("")
        every { settingsDataStore.sttLanguage } returns MutableStateFlow("")
        every { settingsDataStore.inlineArtifactPrefs } returns MutableStateFlow(InlineArtifactPrefs())
        every { settingsDataStore.siteIconsChoice } returns siteIconsChoice
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `default before preferences load never asks`() {
        // The stateIn seed is what a card sees before DataStore's first emission.
        assertThat(ChatPreferences().siteIcons).isEqualTo(SiteIconsState.LOADING)
        assertThat(SiteIconsState.LOADING.showIcons).isFalse()
    }

    @Test
    fun `no stored choice asks and keeps icons off`() = runTest(testDispatcher) {
        val viewModel = fixture.build(testDispatcher)
        advanceUntilIdle()

        val state = viewModel.chatPreferences.value.siteIcons
        assertThat(state).isEqualTo(SiteIconsState.ASK)
        assertThat(state.showIcons).isFalse()
    }

    @Test
    fun `stored choice wins`() = runTest(testDispatcher) {
        val viewModel = fixture.build(testDispatcher)
        siteIconsChoice.value = true
        advanceUntilIdle()
        assertThat(viewModel.chatPreferences.value.siteIcons).isEqualTo(SiteIconsState.ON)

        siteIconsChoice.value = false
        advanceUntilIdle()
        assertThat(viewModel.chatPreferences.value.siteIcons).isEqualTo(SiteIconsState.OFF)
    }

    @Test
    fun `closing the prompt without a choice stops asking for the process, in every chat`() =
        runTest(testDispatcher) {
            val first = fixture.build(testDispatcher)
            advanceUntilIdle()

            first.dismissSiteIconPrompt()
            advanceUntilIdle()
            assertThat(first.chatPreferences.value.siteIcons).isEqualTo(SiteIconsState.OFF)

            // Opening another conversation builds a new ViewModel over the same process-wide session.
            val second = fixture.build(testDispatcher)
            advanceUntilIdle()
            assertThat(second.chatPreferences.value.siteIcons).isEqualTo(SiteIconsState.OFF)
            coVerify(exactly = 0) { settingsDataStore.setShowSiteIcons(any()) }
        }

    @Test
    fun `a choice is stored`() = runTest(testDispatcher) {
        val viewModel = fixture.build(testDispatcher)
        advanceUntilIdle()

        viewModel.setShowSiteIcons(true)
        advanceUntilIdle()

        coVerify { settingsDataStore.setShowSiteIcons(true) }
    }
}
