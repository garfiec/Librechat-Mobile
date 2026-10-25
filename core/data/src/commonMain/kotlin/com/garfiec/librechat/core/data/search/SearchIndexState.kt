package com.garfiec.librechat.core.data.search

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Bookkeeping for the message search index's background work. Not preferences (nothing here is a
 * user's choice), so deliberately absent from `AppPreferences`. Device-scoped like the index itself,
 * which holds every account's rows.
 *
 * Versioned by [CURRENT_FORMAT]: when the flatten rules change, the format is bumped, the new
 * version's flag and cursor start unset, and the backfill empties the index before rebuilding it,
 * so no row keeps the old shape.
 */
class SearchIndexState(private val dataStore: DataStore<Preferences>) {

    suspend fun isBackfillDone(): Boolean = dataStore.data.first()[BACKFILL_DONE] == true

    /** The flatten format the index rows were last rebuilt for (1 = before the field existed). */
    suspend fun indexFormat(): Int = dataStore.data.first()[INDEX_FORMAT] ?: 1

    suspend fun saveIndexFormat(format: Int) {
        dataStore.edit { it[INDEX_FORMAT] = format }
    }

    /** The rowid the next backfill batch starts below, or null to start from the newest row. */
    suspend fun backfillCursor(): Long? = dataStore.data.first()[BACKFILL_CURSOR]

    suspend fun saveBackfillCursor(rowId: Long) {
        dataStore.edit { it[BACKFILL_CURSOR] = rowId }
    }

    suspend fun markBackfillDone() {
        dataStore.edit {
            it[BACKFILL_DONE] = true
            it.remove(BACKFILL_CURSOR)
        }
    }

    /**
     * How many more rows `messages` held than the index when the last repair finished. Unclaimed
     * legacy rows are counted in `messages` but never indexed, so equal counts can't be the only
     * "nothing to do" signal; an unchanged gap means the last repair already looked. Comparing the
     * gap rather than both counts keeps new and pruned messages, which move both, from re-running
     * a repair that has to read every wide row.
     */
    suspend fun repairSettledGap(): Int? =
        dataStore.data.first()[REPAIR_SETTLED]?.split(':')?.let { parts ->
            val messages = parts.getOrNull(0)?.toIntOrNull()
            val indexed = parts.getOrNull(1)?.toIntOrNull()
            if (messages != null && indexed != null) messages - indexed else null
        }

    suspend fun saveRepairSettledAt(messages: Int, indexed: Int) {
        dataStore.edit { it[REPAIR_SETTLED] = "$messages:$indexed" }
    }

    companion object {
        /**
         * Makes the next repair look again, whatever the counts say. For writes that index-eligible
         * rows appear through without changing the gap, which is what the legacy claim does:
         * stamping `accountId` on unclaimed rows makes them indexable, but `messages` already counted
         * them. Takes the caller's edit so it lands in the same write as that change.
         */
        fun forgetRepair(prefs: MutablePreferences) {
            prefs.remove(REPAIR_SETTLED)
        }

        /**
         * 2: reasoning moved after the visible text, with `visibleLength` marking the split.
         * 3: a legacy `:::thinking` block in a message's text counts as reasoning.
         * 4: inline citation markers (`\ue202turn0search0`) are stripped, as the reply shows none.
         * Bumping this rebuilds every user's index once; do it whenever [com.garfiec.librechat.core.data.mapper.searchTextOf] changes.
         */
        const val CURRENT_FORMAT: Int = 4

        private val BACKFILL_DONE = booleanPreferencesKey("search_index_backfill_done_v$CURRENT_FORMAT")
        private val BACKFILL_CURSOR = longPreferencesKey("search_index_backfill_cursor_v$CURRENT_FORMAT")
        private val INDEX_FORMAT = intPreferencesKey("search_index_format")
        private val REPAIR_SETTLED = stringPreferencesKey("search_index_repair_settled")
    }
}
