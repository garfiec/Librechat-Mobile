package com.garfiec.librechat.core.data.db.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

/**
 * External-content FTS4 index over [MessageSearchEntity.searchText], kept in sync by the triggers
 * Room generates for a `contentEntity`.
 *
 * FTS4 with `unicode61` is the only full-text setup both drivers have: Android's framework SQLite
 * doesn't guarantee FTS5, and iOS uses the bundled one. `matchInfo = FTS3` drops the `%_docsize`
 * table, which only ranking would read; results are ordered by recency. No `remove_diacritics=2`
 * (SQLite 3.27+, so it fails on API 26–28) and no `prefix=` index.
 *
 * **Never select content from this table.** The index spans every account; reads go MATCH → rowid
 * set → `message_search WHERE accountId = :a`, which is what keeps one account's text out of
 * another's results.
 */
@Fts4(
    contentEntity = MessageSearchEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    matchInfo = FtsOptions.MatchInfo.FTS3,
)
@Entity(tableName = "messages_fts")
data class MessageSearchFtsEntity(
    val searchText: String?,
)
