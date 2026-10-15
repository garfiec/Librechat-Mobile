package com.garfiec.librechat.core.data.search

import com.garfiec.librechat.core.data.db.dao.MessageSearchDao
import com.garfiec.librechat.core.data.util.SessionTask
import com.garfiec.librechat.core.logging.Diag
import com.garfiec.librechat.core.logging.LogOrigin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Fills the message search index for messages cached before it existed, then keeps it complete.
 *
 * - **Backfill** (once per index version): walks `messages` newest rowid first in batches of 100,
 *   each one transaction that reads the rows and inserts their missing side rows. It never writes
 *   `messages`, so no chat observer re-fires and no wide row is rewritten. The cursor is saved every
 *   10 batches, so a restart doesn't re-read finished ranges. Rows written after it started already
 *   carry side rows, which is why rowid order is enough.
 * - **Repair** (every session, after the backfill): indexes anything that has no side row, such
 *   as legacy rows claimed after the backfill passed them. Skipped when the row counts haven't
 *   moved since the last repair looked.
 * - **Merge**: after the backfill, merges the index's segments in bounded steps.
 *
 * Runs only while the app is in front, idle, and not power-constrained, and steps aside for a
 * local search; see [allowsIndexing] for why the prefetch opt-in doesn't gate it. Session tasks fire on login,
 * cold start and account switch, so overlapping runs are collapsed into one.
 */
class SearchIndexBackfillSessionTask(
    private val dao: MessageSearchDao,
    private val state: SearchIndexState,
    private val maintenance: SearchIndexMaintenance,
    private val window: SearchIndexWindow,
    private val activity: LocalSearchActivity,
    private val startDelay: Duration = 10.seconds,
    private val batchPause: Duration = 75.milliseconds,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : SessionTask {

    private val running = Mutex()

    override suspend fun run() {
        if (!running.tryLock()) return
        try {
            delay(startDelay)
            awaitWindow()
            maintenance.enableAutomerge()
            if (!state.isBackfillDone()) {
                if (state.indexFormat() != SearchIndexState.CURRENT_FORMAT) {
                    // Rows in an older shape: empty the index once, then the backfill refills it.
                    awaitWindow()
                    maintenance.resetIndex()
                    state.saveIndexFormat(SearchIndexState.CURRENT_FORMAT)
                }
                backfill()
                maintenance.mergeUntilDone(beforeStep = ::awaitWindow)
            }
            repair()
        } finally {
            running.unlock()
        }
    }

    private suspend fun backfill() {
        val started = timeSource.markNow()
        var cursor = state.backfillCursor() ?: Long.MAX_VALUE
        var batches = 0
        while (true) {
            awaitWindow()
            val lowest = dao.indexBatch(beforeRowId = cursor, limit = BATCH_SIZE) ?: break
            cursor = lowest
            batches++
            if (batches % CURSOR_SAVE_EVERY == 0) state.saveBackfillCursor(cursor)
            delay(batchPause)
        }
        state.markBackfillDone()
        Diag.d(
            "SearchIndex",
            origin = LogOrigin.CLIENT,
            attrs = mapOf(
                "event" to "backfill_done",
                "batches" to batches.toString(),
                "ms" to started.elapsedNow().inWholeMilliseconds.toString(),
            ),
        ) { "Search index backfill finished" }
    }

    private suspend fun repair() {
        val messages = dao.countAllMessages()
        val indexed = dao.countAllIndexed()
        if (messages == indexed || state.repairSettledAt() == (messages to indexed)) return
        var repaired = 0
        while (true) {
            awaitWindow()
            val read = dao.repairBatch(REPAIR_BATCH_SIZE)
            repaired += read
            if (read < REPAIR_BATCH_SIZE) break
            delay(batchPause)
        }
        state.saveRepairSettledAt(dao.countAllMessages(), dao.countAllIndexed())
        if (repaired > 0) {
            Diag.d(
                "SearchIndex",
                origin = LogOrigin.CLIENT,
                attrs = mapOf("event" to "repair", "rows" to repaired.toString()),
            ) { "Indexed messages the search index was missing" }
        }
    }

    private suspend fun awaitWindow() {
        combine(window.isOpen, activity.inFlight) { open, searches -> open && searches == 0 }.first { it }
    }

    private companion object {
        const val BATCH_SIZE = 100
        const val REPAIR_BATCH_SIZE = 500
        const val CURSOR_SAVE_EVERY = 10
    }
}
