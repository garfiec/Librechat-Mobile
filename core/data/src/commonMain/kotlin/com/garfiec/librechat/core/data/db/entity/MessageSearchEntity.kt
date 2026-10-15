package com.garfiec.librechat.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The search side row of one cached message: the narrow columns search filters and sorts on, plus
 * the flattened plain text [MessageSearchFtsEntity] indexes. Kept off `messages` on purpose: there
 * `text`/`content` come before the trailing columns, so any read of `createdAt`/`accountId` walks
 * the row's overflow pages, and a column added there would make the backfill rewrite every wide
 * row.
 *
 * Written only through `MessageDao.upsertAllIndexed` (and the backfill and repair), in the same
 * transaction as the parent row. [accountId] is copied from that already-stamped parent, never
 * re-resolved, so a reply finishing after an account switch indexes under the account that sent
 * it. Unclaimed legacy messages (`messages.accountId IS NULL`) get no side row.
 *
 * The cascade removes the side row, and through the FTS delete trigger its index entry, wherever a
 * message is deleted, so no delete path needs to know this table exists.
 *
 * Landmines, because the FTS index addresses rows by this table's implicit rowid:
 * - Never `VACUUM` without a following `INSERT INTO messages_fts(messages_fts) VALUES('rebuild')`:
 *   VACUUM may renumber implicit rowids.
 * - Only ever add **nullable columns** here. Any other schema change makes Room recreate the table
 *   with `INSERT INTO _new SELECT`, which renumbers rowids and never rebuilds the index; it must end
 *   with that `'rebuild'` in a manual migration or `AutoMigrationSpec.onPostMigrate`.
 * - Never write this table (or `messages`) with `OnConflictStrategy.REPLACE`: REPLACE's implicit
 *   delete skips the FTS delete trigger unless `recursive_triggers` is on, orphaning index entries.
 */
@Entity(
    tableName = "message_search",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["messageId"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["accountId", "createdAt"]),
        Index("conversationId"),
    ],
)
data class MessageSearchEntity(
    @PrimaryKey
    val messageId: String,
    val accountId: String,
    val conversationId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isCreatedByUser: Boolean,
    val model: String?,
    val endpoint: String?,
    val searchText: String?,
    /** Where the reasoning starts in [searchText] (null: none). */
    val visibleLength: Int? = null,
)
