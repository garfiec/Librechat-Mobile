package com.garfiec.librechat.core.data.db

import com.garfiec.librechat.core.data.db.entity.ToolCallPartEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The stored full tool calls: they live beside the message rows, survive a refresh that keeps the
 * message, go with the message however it is deleted, never attach to another account's row, and
 * stay within the account's budget.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ToolCallPartDaoTest : AccountIsolationTestBase() {

    private val dao get() = db.toolCallPartDao()

    private fun row(messageId: String, callId: String, accountId: String = accountA, chars: Long = 10, storedAt: Long = 1) =
        ToolCallPartEntity(
            messageId = messageId, toolCallId = callId, stepId = "", agentId = "", accountId = accountId,
            conversationId = "c1", toolCall = """{"id":"$callId"}""", chars = chars, storedAt = storedAt,
        )

    private suspend fun stored(accountId: String = accountA) =
        dao.forMessages(accountId, "c1", listOf("m1", "m2")).map { it.messageId to it.toolCallId }

    @Test
    fun aRowIsWrittenOnlyForAMessageCachedForItsAccount() = runTest {
        db.messageDao().upsertAllIndexed(listOf(message("m1", "c1", accountA)))

        val written = dao.storeWithinBudget(listOf(row("m1", "a"), row("m2", "b"), row("m1", "c", accountId = accountB)), 1_000)

        assertThat(written).isEqualTo(1)
        assertThat(stored()).containsExactly("m1" to "a")
        assertThat(stored(accountB)).isEmpty()
    }

    @Test
    fun aRefreshThatKeepsTheMessageKeepsItsStoredParts() = runTest {
        db.messageDao().upsertAllIndexed(listOf(message("m1", "c1", accountA), message("m2", "c1", accountA)))
        dao.storeWithinBudget(listOf(row("m1", "a"), row("m2", "b")), 1_000)

        // The server's answer no longer holds m2; m1 comes back (as a preview, in practice).
        db.messageDao().replaceAllForConversation("c1", accountA, listOf(message("m1", "c1", accountA, text = "again")))

        assertThat(stored()).containsExactly("m1" to "a")
    }

    @Test
    fun deletingTheConversationsMessagesDropsTheirParts() = runTest {
        db.messageDao().upsertAllIndexed(listOf(message("m1", "c1", accountA)))
        dao.storeWithinBudget(listOf(row("m1", "a")), 1_000)

        db.messageDao().deleteAllForConversation("c1", accountA)

        assertThat(stored()).isEmpty()
    }

    @Test
    fun pastTheBudgetTheOldestRowsGo() = runTest {
        db.messageDao().upsertAllIndexed(listOf(message("m1", "c1", accountA)))
        dao.storeWithinBudget(listOf(row("m1", "old", chars = 60, storedAt = 1)), 100)
        dao.storeWithinBudget(listOf(row("m1", "new", chars = 60, storedAt = 2)), 100)

        assertThat(stored()).containsExactly("m1" to "new")
    }

    @Test
    fun writingTheSameIdentityAgainReplacesIt() = runTest {
        db.messageDao().upsertAllIndexed(listOf(message("m1", "c1", accountA)))
        dao.storeWithinBudget(listOf(row("m1", "a", chars = 10)), 1_000)
        dao.storeWithinBudget(listOf(row("m1", "a", chars = 20)), 1_000)

        assertThat(dao.forMessages(accountA, "c1", listOf("m1")).single().chars).isEqualTo(20)
    }
}
