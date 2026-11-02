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
class MessageReasoningOverrideMigrationTest {
    @Test
    fun migrationKeepsExistingMessagesAndAddsANullableColumn() {
        val connection = AndroidSQLiteDriver().open(":memory:")
        try {
            connection.execSQL("CREATE TABLE messages (messageId TEXT PRIMARY KEY, text TEXT)")
            connection.execSQL("INSERT INTO messages VALUES ('existing', 'Hello')")
            LibreChatDatabase_AutoMigration_15_16_Impl().migrate(connection)
            connection.prepare("SELECT text, reasoningOverride FROM messages WHERE messageId = 'existing'").use {
                assertTrue(it.step())
                assertEquals("Hello", it.getText(0))
                assertTrue(it.isNull(1))
            }
        } finally {
            connection.close()
        }
    }
}
