package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.ChatLayoutConstants
import com.garfiec.librechat.core.data.datastore.AppPreferences
import com.garfiec.librechat.core.data.datastore.InlineArtifactPrefs
import com.garfiec.librechat.core.data.datastore.LatexRenderer
import com.google.common.truth.Truth.assertThat
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
 * The "Open thinking by default" setting reaches the chat: it was saved for a long time and read
 * by nothing but its own toggle (#586). The reasoning card takes its initial fold from this value.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelThinkingOpenByDefaultTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fixture = ChatViewModelTestFixture()
    private val settingsDataStore get() = fixture.settingsDataStore
    private val thinkingOpen = MutableStateFlow(false)

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
        every { settingsDataStore.thinkingOpenByDefault } returns thinkingOpen
        every { fixture.preferenceStore.observe(AppPreferences.SiteIcons) } returns MutableStateFlow(false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `closed until the setting says otherwise`() {
        assertThat(ChatPreferences().thinkingOpenByDefault).isFalse()
    }

    @Test
    fun `the setting's value reaches the chat preferences, and follows changes`() = runTest(testDispatcher) {
        val viewModel = fixture.build(testDispatcher)
        advanceUntilIdle()
        assertThat(viewModel.chatPreferences.value.thinkingOpenByDefault).isFalse()

        thinkingOpen.value = true
        advanceUntilIdle()
        assertThat(viewModel.chatPreferences.value.thinkingOpenByDefault).isTrue()

        thinkingOpen.value = false
        advanceUntilIdle()
        assertThat(viewModel.chatPreferences.value.thinkingOpenByDefault).isFalse()
    }

    @Test
    fun `collapse long user messages is on until the setting turns it off`() = runTest(testDispatcher) {
        val collapse = MutableStateFlow(true)
        every { fixture.preferenceStore.observe(AppPreferences.CollapseLongUserMessages) } returns collapse
        val viewModel = fixture.build(testDispatcher)
        advanceUntilIdle()
        assertThat(viewModel.chatPreferences.value.collapseLongUserMessages).isTrue()

        collapse.value = false
        advanceUntilIdle()
        assertThat(viewModel.chatPreferences.value.collapseLongUserMessages).isFalse()
    }
}
