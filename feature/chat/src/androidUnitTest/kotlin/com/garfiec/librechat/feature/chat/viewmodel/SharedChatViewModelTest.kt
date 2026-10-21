package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.BackendBuildClass
import com.garfiec.librechat.core.common.DetectedBackend
import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.FailureKind
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.ShareRepository
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.FILE_SOURCE_TEXT
import com.garfiec.librechat.core.model.FileReference
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.core.model.request.NO_PARENT
import com.garfiec.librechat.core.model.response.ForkConversationResponse
import com.garfiec.librechat.core.model.response.SharedConversationSnapshot
import com.garfiec.librechat.core.network.client.ServerUrlProvider
import com.garfiec.librechat.feature.chat.util.MessageNode
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SharedChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val shareRepository = mockk<ShareRepository>(relaxed = true)
    private val conversationRepository = mockk<ConversationRepository>(relaxed = true)
    private val accounts = InMemoryActiveAccountProvider().apply { set(AccountId("acct-1")) }
    private val serverUrl = object : ServerUrlProvider {
        override fun getBaseUrl() = "https://chat.example.com"
    }
    private val backend = MutableStateFlow<DetectedBackend?>(DetectedBackend("0.8.8", BackendBuildClass.OFFICIAL))
    private val configRepository = mockk<ConfigRepository>(relaxed = true) {
        every { detectedBackend } returns backend
    }

    // u1 has two replies, a1 and a2 (the newer, shown by default); a1 was continued with u2.
    private val u1 = message("u1", NO_PARENT, user = true)
    private val a1 = message("a1", "u1")
    private val a2 = message("a2", "u1")
    private val u2 = message("u2", "a1", user = true)
    private val snapshot = SharedConversationSnapshot(
        shareId = "share-1",
        title = "Trip plans",
        updatedAt = "2026-10-20T10:00:00.000Z",
        messages = listOf(u1, a1, a2, u2),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { shareRepository.shareUrl("share-1") } returns "https://chat.example.com/share/share-1"
        coEvery { shareRepository.getSharedSnapshot("share-1") } returns Result.Success(snapshot)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        SharedChatViewModel("share-1", shareRepository, conversationRepository, accounts, serverUrl, configRepository)

    private fun TestScope.eventsOf(vm: SharedChatViewModel): List<SharedChatEvent> {
        val events = mutableListOf<SharedChatEvent>()
        // Unconfined: collects each event as it is sent, background work or not.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        return events
    }

    @Test
    fun `opens on the newest branch`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
        assertThat(state.shareUrl).isEqualTo("https://chat.example.com/share/share-1")
        assertThat(state.displayMessages.map { it.message.messageId }).containsExactly("u1", "a2").inOrder()
    }

    @Test
    fun `Copy on a reply kept in content parts copies the parts, not the blank text`() {
        val reply = Message(
            messageId = "a1",
            conversationId = "anon",
            parentMessageId = "u1",
            text = "",
            content = listOf(MessageContentPart(type = ContentType.TEXT, text = "agent answer")),
        )
        val state = SharedChatUiState(displayMessages = listOf(MessageNode(reply, emptyList(), 0, 1)))

        assertThat(state.clipboardText("a1")).isEqualTo("agent answer")
        assertThat(state.clipboardText("missing")).isEmpty()
    }

    @Test
    fun `Continue forks the tip of the newest branch with the snapshot's revision`() = runTest(testDispatcher) {
        coEvery { shareRepository.forkSharedConversation(any(), any(), any()) } returns forked("new-convo")
        val vm = viewModel()
        val events = eventsOf(vm)
        advanceUntilIdle()

        vm.continueChat()
        advanceUntilIdle()

        // a2 is at index 2 of the snapshot's messages.
        coVerify { shareRepository.forkSharedConversation("share-1", 2, "2026-10-20T10:00:00.000Z") }
        coVerify { conversationRepository.saveConversation(Conversation(conversationId = "new-convo"), AccountId("acct-1")) }
        assertThat(events).containsExactly(SharedChatEvent.Continued("new-convo"))
    }

    @Test
    fun `Continue forks the branch the user switched to`() = runTest(testDispatcher) {
        coEvery { shareRepository.forkSharedConversation(any(), any(), any()) } returns forked("new-convo")
        val vm = viewModel()
        advanceUntilIdle()

        vm.switchBranch(parentKey = "u1", siblingIndex = 0)
        assertThat(vm.uiState.value.displayMessages.map { it.message.messageId })
            .containsExactly("u1", "a1", "u2").inOrder()
        vm.continueChat()
        advanceUntilIdle()

        // u2, the tip of a1's branch, is at index 3.
        coVerify { shareRepository.forkSharedConversation("share-1", 3, "2026-10-20T10:00:00.000Z") }
    }

    @Test
    fun `a second tap while Continue is in flight sends nothing`() = runTest(testDispatcher) {
        val answer = CompletableDeferred<Result<ForkConversationResponse>>()
        coEvery { shareRepository.forkSharedConversation(any(), any(), any()) } coAnswers { answer.await() }
        val vm = viewModel()
        advanceUntilIdle()

        vm.continueChat()
        advanceUntilIdle()
        assertThat(vm.uiState.value.isContinuing).isTrue()
        vm.continueChat()
        advanceUntilIdle()
        answer.complete(forked("new-convo"))
        advanceUntilIdle()

        coVerify(exactly = 1) { shareRepository.forkSharedConversation(any(), any(), any()) }
    }

    @Test
    fun `Continue saves the copy under the account it was made on, even after a switch mid-call`() =
        runTest(testDispatcher) {
            val answer = CompletableDeferred<Result<ForkConversationResponse>>()
            coEvery { shareRepository.forkSharedConversation(any(), any(), any()) } coAnswers { answer.await() }
            val vm = viewModel()
            advanceUntilIdle()

            vm.continueChat()
            advanceUntilIdle()
            accounts.set(AccountId("acct-2"))
            answer.complete(forked("new-convo"))
            advanceUntilIdle()

            coVerify(exactly = 1) {
                conversationRepository.saveConversation(Conversation(conversationId = "new-convo"), AccountId("acct-1"))
            }
            coVerify(exactly = 0) { conversationRepository.saveConversation(any(), AccountId("acct-2")) }
        }

    @Test
    fun `a revision mismatch reloads the link, resets the branch and says it changed`() = runTest(testDispatcher) {
        coEvery { shareRepository.forkSharedConversation(any(), any(), any()) } returns
            apiError(409, """{"message":"Shared link was updated","code":"SHARE_REVISION_MISMATCH"}""")
        val vm = viewModel()
        val events = eventsOf(vm)
        advanceUntilIdle()
        vm.switchBranch(parentKey = "u1", siblingIndex = 0)

        vm.continueChat()
        advanceUntilIdle()

        coVerify(exactly = 2) { shareRepository.getSharedSnapshot("share-1") }
        coVerify(exactly = 1) { shareRepository.forkSharedConversation(any(), any(), any()) }
        val state = vm.uiState.value
        assertThat(state.isContinuing).isFalse()
        assertThat(state.activeBranches).isEmpty()
        assertThat(state.displayMessages.map { it.message.messageId }).containsExactly("u1", "a2").inOrder()
        assertThat(events).containsExactly(SharedChatEvent.LinkUpdated)
    }

    @Test
    fun `the fork rate limit is reported and Continue can be tried again`() = runTest(testDispatcher) {
        coEvery { shareRepository.forkSharedConversation(any(), any(), any()) } returns apiError(429, null)
        val vm = viewModel()
        val events = eventsOf(vm)
        advanceUntilIdle()

        vm.continueChat()
        advanceUntilIdle()

        assertThat(events).containsExactly(SharedChatEvent.ContinueRateLimited)
        assertThat(vm.uiState.value.isContinuing).isFalse()
    }

    @Test
    fun `a content-filter refusal of the fork carries the server's reason`() = runTest(testDispatcher) {
        coEvery { shareRepository.forkSharedConversation(any(), any(), any()) } returns apiError(
            400,
            """{"error":"content_filter_block","message":"Remove the API key from message 3.","source":"x","field":"text"}""",
            message = "Remove the API key from message 3.",
        )
        val vm = viewModel()
        val events = eventsOf(vm)
        advanceUntilIdle()

        vm.continueChat()
        advanceUntilIdle()

        assertThat(events).containsExactly(SharedChatEvent.ContinueBlocked("Remove the API key from message 3."))
    }

    @Test
    fun `a server confirmed older than the fork route offers no Continue`() = runTest(testDispatcher) {
        backend.value = DetectedBackend("0.8.7", BackendBuildClass.OFFICIAL)
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.canContinue).isFalse()
        vm.continueChat()
        advanceUntilIdle()

        coVerify(exactly = 0) { shareRepository.forkSharedConversation(any(), any(), any()) }
    }

    @Test
    fun `a 404 from fork on an unplaced server whose link still loads means no fork route`() = runTest(testDispatcher) {
        backend.value = null
        coEvery { shareRepository.forkSharedConversation(any(), any(), any()) } returns apiError(404, null)
        val vm = viewModel()
        val events = eventsOf(vm)
        advanceUntilIdle()
        assertThat(vm.uiState.value.canContinue).isTrue()

        vm.continueChat()
        advanceUntilIdle()

        assertThat(events).containsExactly(SharedChatEvent.ContinueUnsupported)
        assertThat(vm.uiState.value.canContinue).isFalse()
        assertThat(vm.uiState.value.snapshot).isNotNull()
    }

    @Test
    fun `a 404 from fork on a server with the route reloads and says Continue failed`() = runTest(testDispatcher) {
        coEvery { shareRepository.forkSharedConversation(any(), any(), any()) } returns apiError(404, null)
        val vm = viewModel()
        val events = eventsOf(vm)
        advanceUntilIdle()

        vm.continueChat()
        advanceUntilIdle()

        assertThat(events).containsExactly(SharedChatEvent.ContinueFailed)
        assertThat(vm.uiState.value.canContinue).isTrue()
        coVerify(exactly = 2) { shareRepository.getSharedSnapshot("share-1") }
    }

    @Test
    fun `a 404 from fork when the link itself is gone shows not found`() = runTest(testDispatcher) {
        backend.value = null
        coEvery { shareRepository.getSharedSnapshot("share-1") } returnsMany listOf(
            Result.Success(snapshot),
            apiError(404, null),
        )
        coEvery { shareRepository.forkSharedConversation(any(), any(), any()) } returns apiError(404, null)
        val vm = viewModel()
        val events = eventsOf(vm)
        advanceUntilIdle()

        vm.continueChat()
        advanceUntilIdle()

        assertThat(events).containsExactly(SharedChatEvent.ContinueFailed)
        assertThat(vm.uiState.value.error).isEqualTo(SharedChatError.NOT_FOUND)
    }

    @Test
    fun `a missing link shows not found, and Retry loads it`() = runTest(testDispatcher) {
        coEvery { shareRepository.getSharedSnapshot("share-1") } returnsMany listOf(apiError(404, null), Result.Success(snapshot))
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.uiState.value.error).isEqualTo(SharedChatError.NOT_FOUND)

        vm.reload()
        advanceUntilIdle()

        assertThat(vm.uiState.value.error).isNull()
        assertThat(vm.uiState.value.displayMessages).isNotEmpty()
    }

    @Test
    fun `an empty share reads as not found`() = runTest(testDispatcher) {
        coEvery { shareRepository.getSharedSnapshot("share-1") } returns Result.Success(snapshot.copy(messages = emptyList()))
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.error).isEqualTo(SharedChatError.NOT_FOUND)
        assertThat(vm.uiState.value.snapshot).isNull()
    }

    @Test
    fun `401 and 403 read as no access`() = runTest(testDispatcher) {
        coEvery { shareRepository.getSharedSnapshot("share-1") } returns apiError(403, null)
        val forbidden = viewModel()
        advanceUntilIdle()
        assertThat(forbidden.uiState.value.error).isEqualTo(SharedChatError.NO_ACCESS)

        coEvery { shareRepository.getSharedSnapshot("share-1") } returns apiError(401, null)
        val unauthorized = viewModel()
        advanceUntilIdle()
        assertThat(unauthorized.uiState.value.error).isEqualTo(SharedChatError.NO_ACCESS)
    }

    @Test
    fun `the share-view rate limit has its own state`() = runTest(testDispatcher) {
        coEvery { shareRepository.getSharedSnapshot("share-1") } returns apiError(429, null)
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.error).isEqualTo(SharedChatError.RATE_LIMITED)
    }

    @Test
    fun `a filtered share is blocked, with the server's reason`() = runTest(testDispatcher) {
        coEvery { shareRepository.getSharedSnapshot("share-1") } returns apiError(
            400,
            """{"error":"content_filter_uninspectable","message":"A file could not be inspected.","source":"x","field":"files"}""",
            message = "A file could not be inspected.",
        )
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.error).isEqualTo(SharedChatError.BLOCKED)
        assertThat(vm.uiState.value.blockedMessage).isEqualTo("A file could not be inspected.")
    }

    @Test
    fun `a refresh that can't get through keeps what is on screen and says so`() = runTest(testDispatcher) {
        val vm = viewModel()
        val events = eventsOf(vm)
        advanceUntilIdle()
        coEvery { shareRepository.getSharedSnapshot("share-1") } returns
            Result.Error(message = "offline", kind = FailureKind.Network)

        vm.reload()
        advanceUntilIdle()

        assertThat(vm.uiState.value.error).isNull()
        assertThat(vm.uiState.value.snapshot).isEqualTo(snapshot)
        assertThat(vm.uiState.value.isRefreshing).isFalse()
        assertThat(events).containsExactly(SharedChatEvent.RefreshFailed(rateLimited = false))
    }

    @Test
    fun `a rate-limited refresh says so over the content`() = runTest(testDispatcher) {
        val vm = viewModel()
        val events = eventsOf(vm)
        advanceUntilIdle()
        coEvery { shareRepository.getSharedSnapshot("share-1") } returns apiError(429, null)

        vm.reload()
        advanceUntilIdle()

        assertThat(vm.uiState.value.snapshot).isEqualTo(snapshot)
        assertThat(events).containsExactly(SharedChatEvent.RefreshFailed(rateLimited = true))
    }

    @Test
    fun `a first load that fails shows the error state, not a refresh notice`() = runTest(testDispatcher) {
        coEvery { shareRepository.getSharedSnapshot("share-1") } returns
            Result.Error(message = "offline", kind = FailureKind.Network)
        val vm = viewModel()
        val events = eventsOf(vm)
        advanceUntilIdle()

        assertThat(vm.uiState.value.error).isEqualTo(SharedChatError.NETWORK)
        assertThat(events).isEmpty()
    }

    @Test
    fun `a configured sender withholds the model name`() = runTest(testDispatcher) {
        coEvery { shareRepository.getSharedSnapshot("share-1") } returns
            Result.Success(snapshot.copy(hasConfiguredSender = true))
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.hidesModelName).isTrue()
    }

    @Test
    fun `a text-source file opens as its text`() = runTest(testDispatcher) {
        val withText = snapshot.copy(
            messages = listOf(
                u1.copy(files = listOf(FileReference(fileId = "f1", filename = "notes.pdf", source = FILE_SOURCE_TEXT))),
                a2.copy(attachments = listOf(Attachment(fileId = "f2", source = FILE_SOURCE_TEXT))),
            ),
        )
        coEvery { shareRepository.getSharedSnapshot("share-1") } returns Result.Success(withText)
        coEvery { shareRepository.downloadSharedFile("share-1", "f1") } returns Result.Success("hello".encodeToByteArray())
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.uiState.value.textSourceFileIds).containsExactly("f1", "f2")

        vm.openTextFile("f1", "notes.pdf")
        advanceUntilIdle()

        assertThat(vm.uiState.value.textFile).isEqualTo(SharedTextFile("notes.pdf", text = "hello"))
        vm.closeTextFile()
        assertThat(vm.uiState.value.textFile).isNull()
    }

    @Test
    fun `a text file that can't be read says so`() = runTest(testDispatcher) {
        coEvery { shareRepository.downloadSharedFile("share-1", "f1") } returns apiError(404, null)
        val vm = viewModel()
        advanceUntilIdle()

        vm.openTextFile("f1", "notes.txt")
        advanceUntilIdle()

        assertThat(vm.uiState.value.textFile).isEqualTo(SharedTextFile("notes.txt", failed = true))
    }

    @Test
    fun `files download through the share's own route`() = runTest(testDispatcher) {
        coEvery { shareRepository.downloadSharedFile("share-1", "f9") } returns Result.Success(byteArrayOf(1, 2))
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.downloadAttachment(Attachment(fileId = "f9"))?.toList()).containsExactly(1.toByte(), 2.toByte())
        assertThat(vm.downloadAttachment(Attachment(fileId = null))).isNull()
    }

    @Test
    fun `the tail index is the active path's last message in the snapshot`() {
        val path = com.garfiec.librechat.feature.chat.util.buildActiveMessagePath(snapshot.messages)
        assertThat(activeTailIndex(snapshot.messages, path)).isEqualTo(2)
        assertThat(activeTailIndex(snapshot.messages, emptyList())).isNull()
    }

    private fun message(id: String, parent: String, user: Boolean = false) =
        Message(messageId = id, conversationId = "anon", parentMessageId = parent, isCreatedByUser = user, text = id)

    private fun forked(id: String): Result<ForkConversationResponse> =
        Result.Success(ForkConversationResponse(conversation = Conversation(conversationId = id)))

    private fun apiError(status: Int, body: String?, message: String = "Request failed"): Result.Error =
        Result.Error(
            exception = ApiException(statusCode = status, message = message, body = body, serverAuthored = body != null),
            message = message,
            kind = FailureKind.Unknown,
        )
}
