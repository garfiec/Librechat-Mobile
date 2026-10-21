package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.PermissionsRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.ShareRepository
import com.garfiec.librechat.core.data.share.ShareLinkControllerFactory
import com.garfiec.librechat.core.data.share.ShareTarget
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.response.ShareLinkLookup
import com.garfiec.librechat.core.model.share.ShareSheetState
import com.garfiec.librechat.feature.chat.util.MessageNode
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ConversationActionsHandle
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMetaState
import com.garfiec.librechat.feature.chat.viewmodel.MessagesState
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ConversationActionsDelegateShareTest {

    private val revision = MutableStateFlow(0L)
    private val shareRepository = mockk<ShareRepository>(relaxed = true) {
        every { revision } returns this@ConversationActionsDelegateShareTest.revision
        every { shareUrl(any()) } answers { "https://chat.example.com/share/${firstArg<String>()}" }
    }
    private val configRepository = mockk<ConfigRepository>(relaxed = true) {
        every { startupConfig } returns MutableStateFlow(StartupConfig(sharedLinksEnabled = true))
        every { detectedBackendVersion } returns MutableStateFlow("0.8.8")
    }
    private val roleRepository = mockk<RoleRepository>(relaxed = true) {
        every { userPermissions } returns MutableStateFlow<UserRolePermissions?>(null)
    }
    private val factory = ShareLinkControllerFactory(
        shareRepository = shareRepository,
        configRepository = configRepository,
        roleRepository = roleRepository,
        permissionsRepository = mockk<PermissionsRepository>(relaxed = true),
    )

    private val live = Result.Success(ShareLinkLookup(success = true, shareId = "s1"))
    private val none = Result.Success(ShareLinkLookup(success = false))

    private fun message(id: String, parent: String? = null) =
        Message(messageId = id, conversationId = "c1", parentMessageId = parent)

    private fun node(message: Message) = MessageNode(message, emptyList(), siblingIndex = 0, siblingCount = 1)

    /** A chat whose visible branch is [ids], oldest first. */
    private fun chat(
        vararg ids: String,
        streaming: Boolean = false,
        pending: Message? = null,
        sharedLinksEnabled: Boolean = true,
    ): ChatUiState {
        val messages = ids.mapIndexed { i, id -> message(id, ids.getOrNull(i - 1)) }
        return ChatUiState(
            content = MessagesState(
                messages = messages,
                displayMessages = messages.map(::node),
                isStreaming = streaming,
                pendingResumeUserMessage = pending,
            ),
            conversation = ConversationMetaState(conversationId = "c1", sharedLinksEnabled = sharedLinksEnabled),
        )
    }

    private class Harness(val root: ChatStateHandle, val delegate: ConversationActionsDelegate)

    private fun TestScope.harness(initial: ChatUiState): Harness {
        val root = ChatStateHandle(MutableStateFlow(initial), backgroundScope)
        val delegate = ConversationActionsDelegate(
            handle = ConversationActionsHandle(root),
            conversationRepository = mockk<ConversationRepository>(relaxed = true),
            shareRepository = shareRepository,
            shareLink = factory.create(backgroundScope),
        )
        return Harness(root, delegate)
    }

    // ── target ────────────────────────────────────────────────────────────

    @Test
    fun `the target is the tail of the branch on screen`() {
        assertThat(resolveShareTarget(chat("u1", "a1", "u2", "a2"))).isEqualTo(ShareTarget.Saved("a2"))
    }

    @Test
    fun `a chat with no messages has nothing to share`() {
        assertThat(resolveShareTarget(chat())).isEqualTo(ShareTarget.NoMessages)
    }

    @Test
    fun `a streaming reply or an unsaved sent message is not saved yet`() {
        assertThat(resolveShareTarget(chat("u1", "a1", "u2", streaming = true))).isEqualTo(ShareTarget.NotSaved)
        assertThat(resolveShareTarget(chat("u1", pending = message("u1")))).isEqualTo(ShareTarget.NotSaved)
    }

    @Test
    fun `Create from the open chat sends the branch tail as the target`() = runTest {
        coEvery { shareRepository.lookupShareLink("c1") } returns none
        coEvery { shareRepository.createShareLink("c1", any(), any()) } returns Result.Success("s1")
        val h = harness(chat("u1", "a1"))

        h.delegate.shareConversation()
        runCurrent()
        assertThat(h.delegate.shareLink.state.value).isInstanceOf(ShareSheetState.NoLink::class.java)
        coVerify(exactly = 0) { shareRepository.createShareLink(any(), any(), any()) }

        h.delegate.shareLink.create()
        runCurrent()

        coVerify(exactly = 1) { shareRepository.createShareLink("c1", "a1", true) }
    }

    // ── first turn ────────────────────────────────────────────────────────

    @Test
    fun `the first turn streaming hides Share, later turns don't`() {
        assertThat(chat("u1", streaming = true).isFirstTurnStreaming).isTrue()
        assertThat(chat(streaming = true).isFirstTurnStreaming).isTrue()
        assertThat(chat("u1", "a1", "u2", streaming = true).isFirstTurnStreaming).isFalse()
        assertThat(chat("u1").isFirstTurnStreaming).isFalse()
    }

    // ── link active ───────────────────────────────────────────────────────

    @Test
    fun `an open chat with a live link is marked`() = runTest {
        coEvery { shareRepository.lookupShareLink("c1") } returns live
        val h = harness(chat("u1", "a1"))
        runCurrent()

        assertThat(h.root.state.shareLinkActive).isTrue()
    }

    @Test
    fun `a change to the user's links looks again`() = runTest {
        coEvery { shareRepository.lookupShareLink("c1") } returns live
        val h = harness(chat("u1", "a1"))
        runCurrent()

        coEvery { shareRepository.lookupShareLink("c1") } returns none
        revision.value++
        runCurrent()

        assertThat(h.root.state.shareLinkActive).isFalse()
    }

    @Test
    fun `closing the sheet looks again`() = runTest {
        coEvery { shareRepository.lookupShareLink("c1") } returns none
        val h = harness(chat("u1", "a1"))
        runCurrent()
        assertThat(h.root.state.shareLinkActive).isFalse()

        coEvery { shareRepository.lookupShareLink("c1") } returns live
        h.delegate.closeShareSheet()
        runCurrent()

        assertThat(h.root.state.shareLinkActive).isTrue()
        assertThat(h.delegate.shareLink.state.value).isEqualTo(ShareSheetState.Closed)
    }

    @Test
    fun `another chat starts unmarked, and a failed lookup leaves it so`() = runTest {
        coEvery { shareRepository.lookupShareLink("c1") } returns live
        coEvery { shareRepository.lookupShareLink("c2") } returns Result.Error(message = "offline")
        val h = harness(chat("u1", "a1"))
        runCurrent()
        assertThat(h.root.state.shareLinkActive).isTrue()

        h.root.stateFlow.update { it.copy(conversation = it.conversation.copy(conversationId = "c2")) }
        runCurrent()

        assertThat(h.root.state.shareLinkActive).isFalse()
    }

    @Test
    fun `no lookup when the chat can't share`() = runTest {
        val h = harness(chat("u1", "a1", sharedLinksEnabled = false))
        runCurrent()

        assertThat(h.root.state.shareLinkActive).isFalse()
        coVerify(exactly = 0) { shareRepository.lookupShareLink(any()) }
    }
}
