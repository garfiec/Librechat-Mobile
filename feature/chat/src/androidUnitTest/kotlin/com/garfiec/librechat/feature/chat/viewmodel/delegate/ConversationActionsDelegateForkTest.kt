package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.DraftRepository
import com.garfiec.librechat.core.data.share.ShareLinkController
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.FileReference
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.request.ForkOption
import com.garfiec.librechat.core.model.request.NO_PARENT
import com.garfiec.librechat.core.model.response.ForkConversationResponse
import com.garfiec.librechat.feature.chat.util.MessageNode
import com.garfiec.librechat.feature.chat.viewmodel.ChatNotice
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ConversationActionsHandle
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMetaState
import com.garfiec.librechat.feature.chat.viewmodel.ForkNotice
import com.garfiec.librechat.feature.chat.viewmodel.MessagesState
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ConversationActionsDelegateForkTest {

    private val repository = mockk<ConversationRepository>(relaxed = true)
    private val drafts = mockk<DraftRepository>(relaxed = true)

    private fun files(id: String) = listOf(FileReference(fileId = id))

    private fun node(message: Message) = MessageNode(message, emptyList(), siblingIndex = 0, siblingCount = 1)

    /** u1 (files) → a1 → u2 (files, "send again") → a2 failed on the files. */
    private fun failedChat(): ChatUiState {
        val messages = listOf(
            Message("u1", "c1", parentMessageId = NO_PARENT, isCreatedByUser = true, files = files("f1")),
            Message("a1", "c1", parentMessageId = "u1"),
            Message("u2", "c1", parentMessageId = "a1", text = "send again", isCreatedByUser = true, files = files("f2")),
            Message("a2", "c1", parentMessageId = "u2", text = "prompt is too long", error = true),
        )
        return ChatUiState(
            content = MessagesState(messages = messages, displayMessages = messages.map(::node)),
            conversation = ConversationMetaState(conversationId = "c1"),
        )
    }

    private fun TestScope.delegate(state: ChatUiState): Pair<ChatStateHandle, ConversationActionsDelegate> {
        val root = ChatStateHandle(MutableStateFlow(state), backgroundScope)
        return root to ConversationActionsDelegate(
            handle = ConversationActionsHandle(root),
            conversationRepository = repository,
            shareRepository = mockk(relaxed = true),
            shareLink = mockk<ShareLinkController>(relaxed = true),
            draftRepository = drafts,
        )
    }

    @Test
    fun `branch without files copies up to the reply before the failed turn and stages its text`() = runTest {
        coEvery {
            repository.forkConversation("c1", "a1", ForkOption.DIRECT_PATH, null, null, true)
        } returns Result.Success(
            ForkConversationResponse(Conversation(conversationId = "fork"), listOf(Message("x", "fork"))),
        )
        val (root, delegate) = delegate(failedChat())

        delegate.branchWithoutFiles("a2")
        runCurrent()

        coVerify { drafts.saveDraft("fork", "send again") }
        assertThat(root.state.forkedConversationId).isEqualTo("fork")
        assertThat(root.state.forkNotice).isEqualTo(ForkNotice.COPIED_WITHOUT_FILES_DRAFT)
        assertThat(root.state.isForkInProgress).isFalse()
    }

    @Test
    fun `a copy that still has files stays here and says the server can't leave them out`() = runTest {
        coEvery { repository.forkConversation(any(), any(), any(), any(), any(), any()) } returns Result.Success(
            ForkConversationResponse(
                Conversation(conversationId = "fork"),
                listOf(Message("x", "fork", files = listOf(FileReference(fileId = "f1")))),
            ),
        )
        val (root, delegate) = delegate(failedChat())

        delegate.branchWithoutFiles("a2")
        runCurrent()

        assertThat(root.state.forkedConversationId).isNull()
        assertThat(root.state.error).isEqualTo(ChatNotice.BRANCH_WITHOUT_FILES_UNSUPPORTED.marker())
        coVerify(exactly = 0) { drafts.saveDraft(any(), any()) }
    }

    @Test
    fun `a fork failure is a localized notice, never the server's text, and 429 has its own`() = runTest {
        coEvery { repository.forkConversation(any(), any(), any(), any(), any(), any()) } returns
            Result.Error(ApiException(statusCode = 429, message = "raw"), "raw")
        val (root, delegate) = delegate(failedChat())

        delegate.forkFromMessage("a1", ForkOption.DIRECT_PATH)
        runCurrent()
        assertThat(root.state.error).isEqualTo(ChatNotice.FORK_RATE_LIMITED.marker())

        coEvery { repository.forkConversation(any(), any(), any(), any(), any(), any()) } returns
            Result.Error(ApiException(statusCode = 500, message = "raw"), "raw")
        delegate.forkFromMessage("a1", ForkOption.DIRECT_PATH)
        runCurrent()
        assertThat(root.state.error).isEqualTo(ChatNotice.FORK_FAILED.marker())
    }

    @Test
    fun `leave out files rides the fork only when asked for`() = runTest {
        coEvery { repository.forkConversation(any(), any(), any(), any(), any(), any()) } returns
            Result.Success(ForkConversationResponse(Conversation(conversationId = "fork")))
        val (_, delegate) = delegate(failedChat())

        delegate.forkFromMessage("a1", ForkOption.TARGET_LEVEL)
        runCurrent()
        coVerify { repository.forkConversation("c1", "a1", ForkOption.TARGET_LEVEL, null, null, null) }

        delegate.forkFromMessage("a1", ForkOption.TARGET_LEVEL, excludeFiles = true)
        runCurrent()
        coVerify { repository.forkConversation("c1", "a1", ForkOption.TARGET_LEVEL, null, null, true) }
    }
}
