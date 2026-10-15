package com.garfiec.librechat.core.data.search

import androidx.room.Transactor
import androidx.room.execSQL
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.garfiec.librechat.core.data.db.LibreChatDatabase
import kotlinx.coroutines.yield

/**
 * FTS4 housekeeping commands, which are writes to the index's hidden `messages_fts` column and
 * so can't be expressed as DAO queries.
 *
 * Never `'optimize'`: it merges the whole index in one exclusive write that can last seconds.
 * Merging is done in bounded `'merge=300,8'` steps instead, with a yield between them.
 */
class SearchIndexMaintenance(private val db: LibreChatDatabase) {

    /**
     * Has SQLite merge index segments as part of ordinary writes. Persisted in the index's
     * `%_stat` table, so setting it again is a no-op.
     */
    suspend fun enableAutomerge() {
        db.useWriterConnection { it.execSQL("INSERT INTO messages_fts(messages_fts) VALUES('automerge=8')") }
    }

    /**
     * Merges segments one bounded step at a time until a step does no work, or [maxSteps] steps.
     * Returns how many steps ran. A step that changed fewer than two rows did nothing, which is how
     * the FTS docs say to detect that the merge is finished.
     */
    suspend fun mergeUntilDone(maxSteps: Int = 200, beforeStep: suspend () -> Unit = {}): Int {
        var steps = 0
        while (steps < maxSteps) {
            beforeStep()
            val changed = db.useWriterConnection { connection ->
                val before = connection.totalChanges()
                connection.execSQL("INSERT INTO messages_fts(messages_fts) VALUES('merge=300,8')")
                connection.totalChanges() - before
            }
            steps++
            if (changed < 2) break
            yield()
        }
        return steps
    }

    /**
     * Empties the index, for a rebuild after the flatten rules change. A plain `DELETE FROM
     * message_search` would run the FTS delete trigger once per row; with the triggers dropped the
     * delete is a table wipe and `'rebuild'` over the now-empty table clears the index.
     */
    suspend fun resetIndex() = withoutSyncTriggers { connection ->
        connection.execSQL("DELETE FROM message_search")
    }

    /**
     * Deletes every cached message of [accountId], the cheaper way. An ordinary delete runs the
     * index's delete trigger once per message, tokenizing each one back out; when this account holds
     * most of the index, [deleteAccountMessages] and a rebuild from what remains costs less.
     * Otherwise the rebuild would cost more than it saves.
     */
    suspend fun purgeAccountMessages(accountId: String): PurgePath {
        val searchDao = db.messageSearchDao()
        val own = searchDao.countForAccount(accountId)
        val remaining = searchDao.countAllIndexed() - own
        if (own >= FAST_PURGE_MIN_ROWS && own > remaining) {
            deleteAccountMessages(accountId)
            return PurgePath.Rebuild
        }
        db.messageDao().deleteAllForAccount(accountId)
        return PurgePath.PerRow
    }

    /** How [purgeAccountMessages] deleted. */
    enum class PurgePath { Rebuild, PerRow }

    /**
     * Deletes [accountId]'s messages (and, through the foreign key, their side rows) without
     * tokenizing each one out of the index: with the triggers dropped the cascade is a plain delete,
     * and `'rebuild'` then re-indexes the rows that remain, which costs what the remaining accounts
     * hold rather than what this one held. For a large logout purge.
     */
    suspend fun deleteAccountMessages(accountId: String) = withoutSyncTriggers { connection ->
        connection.usePrepared("DELETE FROM messages WHERE accountId = ?") { statement ->
            statement.bindText(1, accountId)
            statement.step()
        }
    }

    /**
     * Runs [block] in one write transaction with the FTS sync triggers dropped, then rebuilds the
     * index from `message_search` and puts the triggers back. Everything commits together, so no
     * reader sees the triggers missing or the index stale.
     */
    private suspend fun withoutSyncTriggers(block: suspend (Transactor) -> Unit) {
        db.useWriterConnection { connection ->
            connection.immediateTransaction {
                SYNC_TRIGGER_NAMES.forEach { connection.execSQL("DROP TRIGGER IF EXISTS $it") }
                block(connection)
                connection.execSQL("INSERT INTO messages_fts(messages_fts) VALUES('rebuild')")
                SYNC_TRIGGERS.forEach { connection.execSQL(it) }
            }
        }
    }

    companion object {
        private const val FAST_PURGE_MIN_ROWS = 2_000

        /**
         * Room's content-sync triggers for `messages_fts`, verbatim from the exported schema's
         * `contentSyncTriggers` (`SearchIndexMaintenanceTest` compares them, so a Room change that
         * renames or reshapes them fails a test rather than leaving the index unsynced).
         */
        val SYNC_TRIGGERS: List<String> = listOf(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_messages_fts_BEFORE_UPDATE BEFORE UPDATE ON " +
                "`message_search` BEGIN DELETE FROM `messages_fts` WHERE `docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_messages_fts_BEFORE_DELETE BEFORE DELETE ON " +
                "`message_search` BEGIN DELETE FROM `messages_fts` WHERE `docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_messages_fts_AFTER_UPDATE AFTER UPDATE ON " +
                "`message_search` BEGIN INSERT INTO `messages_fts`(`docid`, `searchText`) VALUES " +
                "(NEW.`rowid`, NEW.`searchText`); END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_messages_fts_AFTER_INSERT AFTER INSERT ON " +
                "`message_search` BEGIN INSERT INTO `messages_fts`(`docid`, `searchText`) VALUES " +
                "(NEW.`rowid`, NEW.`searchText`); END",
        )

        private val SYNC_TRIGGER_NAMES = listOf("BEFORE_UPDATE", "BEFORE_DELETE", "AFTER_UPDATE", "AFTER_INSERT")
            .map { "room_fts_content_sync_messages_fts_$it" }
    }

    private suspend fun Transactor.totalChanges(): Long =
        usePrepared("SELECT total_changes()") { statement ->
            statement.step()
            statement.getLong(0)
        }
}
