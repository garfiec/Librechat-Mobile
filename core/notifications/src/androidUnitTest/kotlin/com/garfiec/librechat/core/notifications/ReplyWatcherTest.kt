package com.garfiec.librechat.core.notifications

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ChatRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.MessageRepository
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.Message
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class ReplyWatcherTest {

    private val alice = AccountId("server:alice")
    private val watch = ReplyWatch(alice, "c1")
    private val chat = mockk<ChatRepository>()
    private val conversations = mockk<ConversationRepository>()
    private val messages = mockk<MessageRepository>()
    private val notifications = mockk<ReplyNotifications>(relaxed = true)
    private val accounts = InMemoryActiveAccountProvider().apply { set(alice) }

    private fun TestScope.watcher() = ReplyWatcher(
        chatRepository = chat,
        replyReader = ReplyReader(conversations, messages),
        accounts = accounts,
        notifications = notifications,
        timeSource = testScheduler.timeSource,
    )

    private fun stored(conversation: Conversation, vararg replies: Message) {
        coEvery { conversations.refreshConversation("c1", alice) } returns Result.Success(conversation)
        coEvery { messages.getMessages("c1", toolPreviews = false) } returns Result.Success(replies.toList())
    }

    @Test
    fun announcesTheReplyTheServerNames_onceTheRunLeavesTheActiveSet() = runTest {
        coEvery { chat.getActiveJobIds() } returnsMany listOf(
            Result.Success(setOf("c1")),
            Result.Error(),
            Result.Success(emptySet()),
        )
        stored(
            Conversation(conversationId = "c1", title = "Trip", lastResponseMessageId = "a1"),
            Message(messageId = "u1", conversationId = "c1", text = "Plan it", isCreatedByUser = true),
            Message(messageId = "a1", conversationId = "c1", text = "**Pack** light"),
            Message(messageId = "a2", conversationId = "c1", text = "a sibling branch"),
        )
        val announced = slot<ReplyNotification>()
        coEvery { notifications.announce(capture(announced)) } returns Unit

        assertEquals(ReplyWatchOutcome.Ended, watcher().watch(watch, budget = 10.minutes))

        assertEquals(ReplyNotification(alice, "c1", title = "Trip", preview = "Pack light", failed = false), announced.captured)
    }

    @Test
    fun aReplyThatEndedInAnError_isAnnouncedAsFailed() = runTest {
        coEvery { chat.getActiveJobIds() } returns Result.Success(emptySet())
        stored(Conversation(conversationId = "c1"), Message(messageId = "a1", conversationId = "c1", error = true))
        val announced = slot<ReplyNotification>()
        coEvery { notifications.announce(capture(announced)) } returns Unit

        watcher().watch(watch, budget = 10.minutes)

        assertTrue(announced.captured.failed)
    }

    @Test
    fun aTemporaryChat_isNotAnnounced() = runTest {
        coEvery { chat.getActiveJobIds() } returns Result.Success(emptySet())
        stored(Conversation(conversationId = "c1", isTemporary = true))

        assertEquals(ReplyWatchOutcome.Ended, watcher().watch(watch, budget = 10.minutes))

        coVerify(exactly = 0) { notifications.announce(any()) }
    }

    @Test
    fun anotherAccountBecomingActive_abandonsTheWatch() = runTest {
        coEvery { chat.getActiveJobIds() } returns Result.Success(setOf("c1"))
        accounts.set(AccountId("server:bob"))

        assertEquals(ReplyWatchOutcome.Abandoned, watcher().watch(watch, budget = 10.minutes))

        coVerify(exactly = 0) { notifications.announce(any()) }
    }

    @Test
    fun aProcessStartedForTheWatch_waitsForTheAccountToLoad() = runTest {
        val loading = InMemoryActiveAccountProvider()
        coEvery { chat.getActiveJobIds() } returns Result.Success(emptySet())
        stored(Conversation(conversationId = "c1"))
        val outcome = async {
            ReplyWatcher(chat, ReplyReader(conversations, messages), loading, notifications, timeSource = testScheduler.timeSource)
                .watch(watch, budget = 10.minutes)
        }
        runCurrent()
        loading.set(alice)

        assertEquals(ReplyWatchOutcome.Ended, outcome.await())
    }

    @Test
    fun aRunStillGoingWhenTheBudgetEnds_isLeftForTheNextWatch() = runTest {
        coEvery { chat.getActiveJobIds() } returns Result.Success(setOf("c1"))

        val outcome = watcher().watch(watch, budget = 1.minutes)

        assertEquals(ReplyWatchOutcome.StillRunning, outcome)
        coVerify(exactly = 0) { notifications.announce(any()) }
        assertFalse(testScheduler.currentTime < 1.minutes.inWholeMilliseconds)
    }
}
