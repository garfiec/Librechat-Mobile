package com.garfiec.librechat.core.data.db

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import com.garfiec.librechat.core.data.mapper.toEntity
import com.garfiec.librechat.core.data.mapper.toModel
import com.garfiec.librechat.core.model.Message
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageSkillsMigrationTest {
    @Test
    fun migrationKeepsExistingMessagesAndAddsNullableSkillColumns() {
        val connection = AndroidSQLiteDriver().open(":memory:")
        try {
            connection.execSQL("CREATE TABLE messages (messageId TEXT PRIMARY KEY, text TEXT)")
            connection.execSQL("INSERT INTO messages VALUES ('existing', 'Hello')")
            LibreChatDatabase_AutoMigration_11_12_Impl().migrate(connection)
            connection.prepare("SELECT text, manualSkills, alwaysAppliedSkills FROM messages WHERE messageId = 'existing'").use {
                assertTrue(it.step())
                assertEquals("Hello", it.getText(0))
                assertTrue(it.isNull(1))
                assertTrue(it.isNull(2))
            }
            connection.execSQL("UPDATE messages SET manualSkills = '[\"pdf-tools\"]' WHERE messageId = 'existing'")
            connection.prepare("SELECT manualSkills FROM messages").use {
                assertTrue(it.step())
                assertEquals("[\"pdf-tools\"]", it.getText(0))
            }
        } finally {
            connection.close()
        }
    }

    @Test
    fun theMessageCacheRetainsBothSkillLists() {
        val cached = Message(
            messageId = "m",
            conversationId = "c",
            isCreatedByUser = true,
            manualSkills = listOf("pdf-tools", "brand-voice"),
            alwaysAppliedSkills = listOf("house-style"),
        ).toEntity().toModel()
        assertEquals(listOf("pdf-tools", "brand-voice"), cached.manualSkills)
        assertEquals(listOf("house-style"), cached.alwaysAppliedSkills)
    }

    @Test
    fun aTurnWithoutSkillsStaysNull() {
        val cached = Message(messageId = "m", conversationId = "c").toEntity().toModel()
        assertNull(cached.manualSkills)
        assertNull(cached.alwaysAppliedSkills)
    }
}
