package com.garfiec.librechat.core.model.trace

/**
 * One conversation turn's worth of trace records.
 *
 * A turn is identified by the response message it produced, and that grouping is the whole reason
 * this exists rather than a flat list: the server returns newest turns first, **a turn's records
 * may continue on the following page**, and **their order within a turn is undefined**. Rendering
 * each page as it arrives would split one turn across two headings and show its records in
 * whatever order the backend happened to store them.
 */
data class TraceTurn(
    val messageId: String,
    val records: List<TraceRecord>,
) {
    /** Earliest start in the turn, which is what orders turns against each other. */
    val startTime: String get() = records.firstOrNull()?.startTime.orEmpty()

    val hasError: Boolean get() = records.any { it.status == TraceStatus.ERROR }

    val isRunning: Boolean get() = records.any { it.status == TraceStatus.RUNNING }

    /** Summed over the records that were priced; null when none were. */
    val totalCost: Double?
        get() = records.mapNotNull { it.cost }.takeIf { it.isNotEmpty() }?.sum()

    val totalTokens: Long?
        get() = records.mapNotNull { it.usage?.total }.takeIf { it.isNotEmpty() }?.sum()
}

/**
 * Groups accumulated records into turns, newest first, each turn's records oldest first.
 *
 * Takes everything loaded so far rather than one page: a turn that spans a page boundary is only
 * complete once both pages are in, so this must be re-run over the accumulated list on every page
 * rather than appended to.
 *
 * Ordering is by `startTime` — ISO-8601, so lexicographic order is chronological — with the record
 * id as the tiebreak so two records stamped in the same millisecond do not swap between renders.
 * A record with no start sorts last within its turn rather than first, since an absent timestamp
 * says nothing about when it ran.
 */
fun groupTraceRecords(records: List<TraceRecord>): List<TraceTurn> =
    records
        .distinctBy { it.id }
        .groupBy { it.messageId }
        .map { (messageId, turnRecords) ->
            TraceTurn(
                messageId = messageId,
                records = turnRecords.sortedWith(
                    compareBy<TraceRecord> { it.startTime.ifEmpty { LAST } }.thenBy { it.id },
                ),
            )
        }
        // Newest turn first, matching the order the server pages in.
        .sortedWith(compareByDescending<TraceTurn> { it.startTime.ifEmpty { "" } }.thenBy { it.messageId })

/**
 * A record's duration in milliseconds, or null when it has not finished or the timestamps cannot
 * be read.
 *
 * Deliberately NOT computed for a `running` record: it has a start and no end, and showing the
 * time since it started would present a number that grows every time the page is re-read as though
 * it were a measurement.
 */
fun TraceRecord.durationMillis(parse: (String) -> Long?): Long? {
    if (status == TraceStatus.RUNNING) return null
    val start = parse(startTime) ?: return null
    val end = endTime?.let(parse) ?: return null
    return (end - start).takeIf { it >= 0 }
}

/** Sorts after every real ISO-8601 timestamp. */
private const val LAST = "￿"
