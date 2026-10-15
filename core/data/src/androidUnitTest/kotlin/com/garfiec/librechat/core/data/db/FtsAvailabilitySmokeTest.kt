package com.garfiec.librechat.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The SQLite facts the message search index is built on, checked against the framework SQLite
 * production Android uses (Robolectric supplies it on the host JVM):
 *
 * - FTS4 with the `unicode61` tokenizer exists and folds case.
 * - Room turns foreign keys on, so deleting a message cascades into `message_search`, and the
 *   cascade fires the FTS delete trigger (FK actions are internal, so they fire triggers even with
 *   `recursive_triggers` off).
 * - Migrating a real v9 database to v10 creates the index **and** its four content-sync triggers.
 *   Room creates those in `onPostMigrate`, not in the migration itself, so this is the path a
 *   fresh-create test would not cover.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FtsAvailabilitySmokeTest : AccountIsolationTestBase() {

    private val writable: SupportSQLiteDatabase get() = db.openHelper.writableDatabase

    @Test
    fun fts4Unicode61MatchesCaseInsensitively() {
        val sql = writable
        sql.execSQL("CREATE VIRTUAL TABLE probe USING FTS4(body, tokenize=unicode61)")
        sql.execSQL("INSERT INTO probe(body) VALUES ('Kotlin Flow débounce')")
        assertThat(count(sql, "SELECT COUNT(*) FROM probe WHERE probe MATCH 'kotlin'")).isEqualTo(1)
        assertThat(count(sql, "SELECT COUNT(*) FROM probe WHERE probe MATCH 'DÉBOUNCE'")).isEqualTo(1)
        assertThat(count(sql, "SELECT COUNT(*) FROM probe WHERE probe MATCH 'flo*'")).isEqualTo(1)
    }

    @Test
    fun foreignKeysAreOnAndADeletedMessageLeavesTheIndex() = runTest {
        db.messageDao().upsert(message("m1", conversationId = "c", accountId = accountA, text = "zebra"))
        val sql = writable
        sql.execSQL(
            "INSERT INTO message_search (messageId, accountId, conversationId, createdAt, updatedAt, " +
                "isCreatedByUser, searchText) VALUES ('m1', '$accountA', 'c', 0, 0, 0, 'zebra crossing')",
        )
        assertThat(count(sql, "PRAGMA foreign_keys")).isEqualTo(1)
        assertThat(ftsMatches(sql, "zebra")).isEqualTo(1)

        db.messageDao().deleteAllForConversation("c", accountA)

        assertThat(count(sql, "SELECT COUNT(*) FROM message_search")).isEqualTo(0)
        assertThat(ftsMatches(sql, "zebra")).isEqualTo(0)
    }

    @Test
    fun migratingV9ToV10CreatesTheIndexAndItsSyncTriggers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "fts-migration-probe.db"
        context.deleteDatabase(name)
        createV9Database(context.getDatabasePath(name))

        val migrated = Room.databaseBuilder(context, LibreChatDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()
        try {
            val sql = migrated.openHelper.writableDatabase
            assertThat(names(sql, "table")).containsAtLeast("message_search", "messages_fts")
            assertThat(names(sql, "trigger").filter { it.startsWith("room_fts_content_sync_messages_fts_") })
                .hasSize(4)
            // The v9 row survives untouched and is not indexed: the migration never reads messages.
            assertThat(count(sql, "SELECT COUNT(*) FROM messages")).isEqualTo(1)
            assertThat(count(sql, "SELECT COUNT(*) FROM message_search")).isEqualTo(0)

            sql.execSQL(
                "INSERT INTO message_search (messageId, accountId, conversationId, createdAt, updatedAt, " +
                    "isCreatedByUser, searchText) VALUES ('legacy', 'acct', 'c', 0, 0, 0, 'okapi')",
            )
            assertThat(ftsMatches(sql, "okapi")).isEqualTo(1)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    /** Builds a v9 database from the committed `9.json`, exactly as a v9 install left it. */
    private fun createV9Database(file: File) {
        val schema = JSONObject(File(SCHEMA_DIR, "9.json").readText()).getJSONObject("database")
        file.parentFile?.mkdirs()
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { raw ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                raw.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    raw.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) raw.execSQL(setup.getString(i))
            raw.execSQL(
                "INSERT INTO messages (messageId, conversationId, isCreatedByUser, unfinished, error, " +
                    "createdAt, updatedAt, accountId, text) VALUES ('legacy', 'c', 0, 0, 0, 0, 0, 'acct', 'okapi')",
            )
            raw.version = 9
        }
    }

    private fun ftsMatches(sql: SupportSQLiteDatabase, term: String): Int =
        count(sql, "SELECT COUNT(*) FROM messages_fts WHERE messages_fts MATCH '$term'")

    private fun count(sql: SupportSQLiteDatabase, query: String): Int =
        sql.query(query).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else -1 }

    private fun names(sql: SupportSQLiteDatabase, type: String): List<String> =
        sql.query("SELECT name FROM sqlite_master WHERE type = '$type'").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private companion object {
        const val SCHEMA_DIR = "schemas/com.garfiec.librechat.core.data.db.LibreChatDatabase"
    }
}
