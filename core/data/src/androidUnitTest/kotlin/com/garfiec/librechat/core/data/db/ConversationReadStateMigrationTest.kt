package com.garfiec.librechat.core.data.db

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConversationReadStateMigrationTest {
    @Test
    fun migrationKeepsExistingConversationsAndAddsNullableReadState() {
        val connection = AndroidSQLiteDriver().open(":memory:")
        try {
            connection.execSQL("CREATE TABLE conversations (conversationId TEXT PRIMARY KEY, title TEXT)")
            connection.execSQL("INSERT INTO conversations VALUES ('existing', 'Hello')")
            LibreChatDatabase_AutoMigration_14_15_Impl().migrate(connection)
            connection.prepare(
                "SELECT title, lastResponseAt, lastResponseMessageId, lastResponseIsManual, isMarkedUnread, " +
                    "lastSeenAt FROM conversations WHERE conversationId = 'existing'",
            ).use {
                assertTrue(it.step())
                assertEquals("Hello", it.getText(0))
                for (column in 1..5) assertTrue(it.isNull(column))
            }
        } finally {
            connection.close()
        }
    }
}
