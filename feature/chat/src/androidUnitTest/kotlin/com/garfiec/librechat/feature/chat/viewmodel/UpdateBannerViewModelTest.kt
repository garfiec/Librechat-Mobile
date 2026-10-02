package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.data.update.AppUpdateRepository
import com.garfiec.librechat.core.data.update.PendingUpdate
import com.garfiec.librechat.feature.chat.util.MessageNode
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateBannerViewModelTest {

    private val pending = MutableStateFlow<PendingUpdate?>(null)
    private val repository = mockk<AppUpdateRepository>(relaxed = true) {
        every { pendingUpdate } returns pending
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun exposesThePendingUpdateWhileCollected() = runTest {
        val viewModel = UpdateBannerViewModel(repository)
        val collected = mutableListOf<PendingUpdate?>()
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.pendingUpdate.collect { collected += it } }

        val update = PendingUpdate("v2026.10.1", "2026.10.1")
        pending.value = update
        assertEquals(update, collected.last())
    }

    @Test
    fun acknowledgeMarksTheTagNotified() = runTest {
        val viewModel = UpdateBannerViewModel(repository)
        viewModel.acknowledge(PendingUpdate("v2026.10.1", "2026.10.1"))
        coVerify { repository.markNotified("v2026.10.1") }
    }

    @Test
    fun emptyLandingShowsTheBanner() {
        assertTrue(ChatUiState().isEmptyLanding())
    }

    @Test
    fun activeConversationHidesTheBanner() {
        assertFalse(ChatUiState(content = MessagesState(screenState = ChatScreenState.ACTIVE)).isEmptyLanding())
    }

    @Test
    fun firstSendOfANewChatHidesTheBanner() {
        // Still LANDING until the server's `created` event, but no longer empty.
        assertFalse(ChatUiState(content = MessagesState(isStreaming = true)).isEmptyLanding())
        assertFalse(ChatUiState(content = MessagesState(displayMessages = listOf(mockk<MessageNode>()))).isEmptyLanding())
    }
}
