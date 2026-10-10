package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.BackendBuildClass
import com.garfiec.librechat.core.common.DetectedBackend
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.ChatHeaderAlignment
import com.garfiec.librechat.core.data.datastore.ChatHeaderContent
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.response.ChatStatusResponse
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The manual-compaction send, asserted through the ViewModel rather than through
 * `ChatPayloadBuilder`.
 *
 * The builder cannot catch what matters here: it echoes whatever it is handed, so a payload-level
 * test passes no matter what the delegate actually sends. Every value below is decided at the
 * delegate and is wrong in a way nothing else would notice — most of all `optimisticUserMessageId`,
 * which is what the early-abort un-send may delete. Compaction is the one path where the wire
 * `messageId` names an EXISTING persisted row, so conflating the two would make a Stop before
 * `created` delete the very message being summarized.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelCompactionTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fixture = ChatViewModelTestFixture()

    private val leaf = Message(
        messageId = LEAF_ID,
        conversationId = CONVERSATION_ID,
        parentMessageId = "user-1",
        isCreatedByUser = false,
        text = "the reply being summarized",
    )
    private val userTurn = Message(
        messageId = "user-1",
        conversationId = CONVERSATION_ID,
        isCreatedByUser = true,
        text = "hello",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fixture.stubDefaults()

        every { fixture.configRepository.detectedBackend } returns
            MutableStateFlow(DetectedBackend("0.8.8-rc3", BackendBuildClass.RC))
        every { fixture.configRepository.detectedBackendVersion } returns MutableStateFlow("0.8.8-rc3")
        // The gate is the announced capability, nothing else.
        every { fixture.configRepository.startupConfig } returns
            MutableStateFlow(StartupConfig(compactionEnabled = true))
        every { fixture.configRepository.endpointConfigs } returns
            MutableStateFlow(mapOf(ENDPOINT to EndpointConfig()))
        every { fixture.configRepository.availableModels } returns
            MutableStateFlow(mapOf(ENDPOINT to listOf(MODEL)))
        every { fixture.settingsDataStore.selectedMcpServers } returns flowOf(emptySet())
        every { fixture.settingsDataStore.enabledTools } returns flowOf(emptySet())
        every { fixture.settingsDataStore.duringRunAction } returns flowOf(DuringRunAction.QUEUE)
        every { fixture.serverDataStore.currentUrlFlow } returns flowOf("https://example.test")
        every { fixture.settingsDataStore.chatFontSize } returns flowOf(ChatFontSize.MEDIUM)
        every { fixture.settingsDataStore.starredModelsDisplay } returns flowOf(StarredModelsDisplay.OFF)
        every { fixture.settingsDataStore.chatHeaderContent } returns flowOf(ChatHeaderContent.TITLE)
        every { fixture.settingsDataStore.chatHeaderAlignment } returns flowOf(ChatHeaderAlignment.LEFT)
        every { fixture.settingsDataStore.contextBarPlacement } returns flowOf(ContextBarPlacement.OPTIONS_SHEET)
        every { fixture.settingsDataStore.contextGaugeExpanded } returns flowOf(false)
        every { fixture.platformDelegateFactory.createFileHandler(any()) } returns
            mockk(relaxed = true) { every { attachedFiles } returns MutableStateFlow(emptyList()) }

        // A settled conversation whose tail is an ordinary assistant reply: the compactable leaf.
        coEvery { fixture.messageRepository.getMessages(any()) } returns
            Result.Success(listOf(userTurn, leaf))
        every { fixture.messageRepository.observeMessages(any()) } returns
            flowOf(listOf(userTurn, leaf))
        coEvery { fixture.chatRepository.checkStreamStatus(any(), any()) } returns
            ChatStatusResponse(active = false)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Captures every positional argument of the `startChat` the compaction fires. */
    private class StartChatArgs {
        var text: String? = null
        var userMessageId: String? = null
        var parentMessageId: String? = null
        var isRegenerate: Boolean? = null
        var compact: Boolean? = null
    }

    private fun captureStartChat(stream: MutableSharedFlow<StreamEvent>): StartChatArgs {
        val args = StartChatArgs()
        every {
            fixture.chatRepository.startChat(
                text = any(),
                conversationId = any(),
                endpoint = any(),
                endpointType = any(),
                key = any(),
                modelDisplayLabel = any(),
                model = any(),
                userMessageId = any(),
                parentMessageId = any(),
                agentId = any(),
                overrideParentMessageId = any(),
                responseMessageId = any(),
                isEdited = any(),
                isRegenerate = any(),
                compact = any(),
                isContinued = any(),
                webSearch = any(),
                files = any(),
                addedConvo = any(),
                ephemeralAgent = any(),
                isTemporary = any(),
                modelParams = any(),
                quotes = any(),
            )
        } answers {
            args.text = arg(0)
            args.userMessageId = arg(7)
            args.parentMessageId = arg(8)
            args.isRegenerate = arg(13)
            args.compact = arg(14)
            stream
        }
        return args
    }

    /** The compaction turn's SSE stream; hot, so a test can end the run by emitting into it. */
    private val compactionStream = MutableSharedFlow<StreamEvent>(extraBufferCapacity = 8)

    private fun compactTest(body: suspend TestScope.(ChatViewModel, StartChatArgs) -> Unit) = runTest(testDispatcher) {
        fixture.selectionHandoff.put(conversationId = CONVERSATION_ID, endpoint = ENDPOINT, model = MODEL)
        val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = CONVERSATION_ID)
        runCurrent()
        val args = captureStartChat(compactionStream)
        try {
            body(vm, args)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun `a compaction anchors both ids to the leaf and is regenerate-shaped`() = compactTest { vm, args ->
        assertThat(vm.uiState.value.canCompactNow).isTrue()

        vm.compactConversation()
        runCurrent()

        assertThat(args.compact).isTrue()
        assertThat(args.text).isEmpty()
        // Both the summary's parent and the server-side anchor.
        assertThat(args.userMessageId).isEqualTo(LEAF_ID)
        assertThat(args.parentMessageId).isEqualTo(LEAF_ID)
        // Upstream derives isRegenerate as `isRegenerate || compact`; the server reads it to shape
        // the job as one with no user message of its own.
        assertThat(args.isRegenerate).isTrue()
    }

    @Test
    fun `an early abort does not delete the leaf the compaction was summarizing`() = compactTest { vm, _ ->
        vm.compactConversation()
        runCurrent()
        assertThat(vm.uiState.value.isCompacting).isTrue()

        // earlyAbort un-sends the turn's OWN optimistic message. A compaction mints none — its
        // wire `messageId` is a persisted row — so nothing may be removed here.
        compactionStream.emit(
            StreamEvent.Final(requestMessage = null, responseMessage = null, aborted = true, earlyAbort = true),
        )
        runCurrent()

        assertThat(vm.uiState.value.messages.map { it.messageId }).contains(LEAF_ID)
        assertThat(vm.uiState.value.isCompacting).isFalse()
    }

    private companion object {
        const val CONVERSATION_ID = "conv-1"
        const val ENDPOINT = "anthropic"
        const val MODEL = "claude-haiku-4-5"
        const val LEAF_ID = "assistant-1"
    }
}
