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
class Migration13To14Test {
    @Test
    fun migrationKeepsExistingRowsAndAddsNullableColumns() {
        val connection = AndroidSQLiteDriver().open(":memory:")
        try {
            connection.execSQL("CREATE TABLE messages (messageId TEXT PRIMARY KEY, text TEXT)")
            connection.execSQL("INSERT INTO messages VALUES ('existing', 'Hello')")
            connection.execSQL("CREATE TABLE conversations (conversationId TEXT PRIMARY KEY, title TEXT)")
            connection.execSQL("INSERT INTO conversations VALUES ('existing', 'Hi')")
            LibreChatDatabase_AutoMigration_13_14_Impl().migrate(connection)
            connection.prepare(
                "SELECT text, isUserSubmitted, reasoningOverride, privacyRevision FROM messages " +
                    "WHERE messageId = 'existing'",
            ).use {
                assertTrue(it.step())
                assertEquals("Hello", it.getText(0))
                for (column in 1..3) assertTrue(it.isNull(column))
            }
            connection.prepare(
                "SELECT title, lastResponseAt, lastResponseMessageId, lastResponseIsManual, isMarkedUnread, " +
                    "lastSeenAt, isCodeConversation FROM conversations WHERE conversationId = 'existing'",
            ).use {
                assertTrue(it.step())
                assertEquals("Hi", it.getText(0))
                for (column in 1..6) assertTrue(it.isNull(column))
            }
        } finally {
            connection.close()
        }
    }
}
