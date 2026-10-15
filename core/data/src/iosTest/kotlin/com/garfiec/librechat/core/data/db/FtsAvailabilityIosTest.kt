package com.garfiec.librechat.core.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.garfiec.librechat.core.data.db.entity.MessageEntity
import com.garfiec.librechat.core.data.search.FtsQuery
import com.garfiec.librechat.core.data.search.FtsQueryPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The iOS half of `FtsAvailabilitySmokeTest`: the bundled SQLite iOS ships has FTS4 with
 * `unicode61`, and Room's foreign key pragma reaches the driver's connections, so a deleted
 * message leaves the index.
 */
class FtsAvailabilityIosTest {

    private lateinit var db: LibreChatDatabase

    @BeforeTest
    fun open() {
        db = Room.inMemoryDatabaseBuilder<LibreChatDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    @AfterTest
    fun close() = db.close()

    private fun message(id: String, accountId: String, text: String) = MessageEntity(
        messageId = id, conversationId = "conv", parentMessageId = null, sender = null, text = text,
        content = null, isCreatedByUser = false, model = null, endpoint = null, iconURL = null,
        finishReason = null, tokenCount = null, feedback = null, files = null, attachments = null,
        metadata = null, createdAt = 0L, updatedAt = 0L, accountId = accountId,
    )

    private suspend fun hits(accountId: String, query: String): List<String> {
        val plan = FtsQuery.plan(query) as FtsQueryPlan.Match
        return db.messageSearchDao().search(accountId, plan.expression, limit = 10).map { it.messageId }
    }

    @Test
    fun indexesSearchesAndCascades() = runBlocking {
        db.messageDao().upsertAllIndexed(listOf(message("m1", "acct", "Kotlin Coroutines")))

        assertEquals(listOf("m1"), hits("acct", "corou"))
        assertEquals(listOf("m1"), hits("acct", "KOTLIN "))
        assertEquals(emptyList<String>(), hits("other", "kotlin "))

        db.messageDao().deleteAllForAccount("acct")

        assertEquals(emptyList<String>(), hits("acct", "kotlin "))
        assertEquals(0, db.messageSearchDao().countAllIndexed())
    }
}
