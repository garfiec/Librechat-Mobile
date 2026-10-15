package com.garfiec.librechat.core.data.search

import com.garfiec.librechat.core.data.db.dao.MessageSearchDao
import com.garfiec.librechat.core.data.db.dao.MessageSearchRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Finds the first match in one message's text, or null. */
fun interface TextMatcher {
    fun find(text: CharSequence): IntRange?

    companion object {
        /** A substring, for scripts the FTS tokenizer can't split into words. */
        fun literal(needle: String, ignoreCase: Boolean = true): TextMatcher = TextMatcher { text ->
            val at = text.indexOf(needle, ignoreCase = ignoreCase)
            if (at < 0) null else at until at + needle.length
        }
    }
}

/**
 * Where a scan stopped: the next page starts strictly past this (createdAt, rowid), below it for a
 * newest-first scan and above it for an [ascending] one.
 */
data class ScanCursor(val createdAt: Long, val rowId: Long, val ascending: Boolean = false) {
    companion object {
        val START = ScanCursor(Long.MAX_VALUE, Long.MAX_VALUE)
        val START_OLDEST = ScanCursor(Long.MIN_VALUE, Long.MIN_VALUE, ascending = true)
    }
}

data class ScanBudget(
    val maxMatches: Int = 200,
    val maxDuration: Duration = 3.seconds,
    /** Only this much of each message is matched, so one huge row can't hold a page hostage. */
    val maxRowChars: Int = 64_000,
)

data class ScanHit(val row: MessageSearchRow, val firstMatch: IntRange)

/**
 * @property next where to resume with "Scan more", or null when every indexed message was read.
 * @property scanned how many messages this call examined.
 */
data class ScanResult(val hits: List<ScanHit>, val scanned: Int, val next: ScanCursor?)

/**
 * Searches an account's indexed messages a keyset page at a time, newest first or, from an
 * ascending cursor, oldest first, for matches the FTS index can't find (regex, unsegmented scripts).
 *
 * Bounded by [ScanBudget]: it stops at the match cap or the deadline and hands back a cursor, so
 * the caller shows partial results with "Scan more" rather than a spinner with no end. The budget
 * and cancellation are checked **between rows**. A single match can't be interrupted (Android's
 * regex engine copies the input and matches it natively), which is why each row is capped and why
 * dangerous regexes are rejected before they get here.
 */
class MessageScanEngine(
    private val dao: MessageSearchDao,
    private val matchDispatcher: CoroutineDispatcher,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {

    /**
     * [admit] skips rows a filter excludes before their text is matched. [notBefore] is a date
     * floor: newest first it ends the scan at the first row older than it, and oldest first the
     * scan starts there.
     */
    suspend fun scan(
        accountId: String,
        matcher: TextMatcher,
        from: ScanCursor = ScanCursor.START,
        budget: ScanBudget = ScanBudget(),
        notBefore: Long? = null,
        admit: (MessageSearchRow) -> Boolean = { true },
        /** The text matched for a row: its whole indexed text unless the caller narrows it. */
        textOf: (MessageSearchRow) -> String? = { it.searchText },
    ): ScanResult {
        val started = timeSource.markNow()
        val hits = ArrayList<ScanHit>()
        var scanned = 0
        var cursor = if (from.ascending && notBefore != null && from.createdAt < notBefore) {
            ScanCursor(notBefore, Long.MIN_VALUE, ascending = true)
        } else {
            from
        }
        while (true) {
            val page = if (cursor.ascending) {
                dao.scanPageOldest(accountId, cursor.createdAt, cursor.rowId, PAGE_SIZE)
            } else {
                dao.scanPage(accountId, cursor.createdAt, cursor.rowId, PAGE_SIZE)
            }
            if (page.isEmpty()) return ScanResult(hits, scanned, next = null)
            val stoppedAt = withContext(matchDispatcher) {
                for ((index, row) in page.withIndex()) {
                    ensureActive()
                    if (notBefore != null && row.createdAt < notBefore) return@withContext EXHAUSTED
                    scanned++
                    val text = if (admit(row)) textOf(row) else null
                    if (text != null) {
                        val window = if (text.length > budget.maxRowChars) text.subSequence(0, budget.maxRowChars) else text
                        matcher.find(window)?.let { hits += ScanHit(row, it) }
                    }
                    if (hits.size >= budget.maxMatches || started.elapsedNow() >= budget.maxDuration) {
                        return@withContext index
                    }
                }
                -1
            }
            if (stoppedAt == EXHAUSTED) return ScanResult(hits, scanned, next = null)
            if (stoppedAt >= 0) {
                val last = page[stoppedAt]
                val exhausted = stoppedAt == page.lastIndex && page.size < PAGE_SIZE
                return ScanResult(hits, scanned, next = if (exhausted) null else ScanCursor(last.createdAt, last.rowId, cursor.ascending))
            }
            if (page.size < PAGE_SIZE) return ScanResult(hits, scanned, next = null)
            val last = page.last()
            cursor = ScanCursor(last.createdAt, last.rowId, cursor.ascending)
        }
    }

    private companion object {
        const val PAGE_SIZE = 200

        /** Sentinel from the page loop: the date floor was crossed, nothing further can match. */
        const val EXHAUSTED = -2
    }
}
