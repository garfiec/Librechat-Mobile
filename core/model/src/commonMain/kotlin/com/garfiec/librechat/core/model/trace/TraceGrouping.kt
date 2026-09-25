package com.garfiec.librechat.core.model.trace

/** One record placed in its turn's tree. [depth] is 0 for a root. */
data class TraceRow(
    val record: TraceRecord,
    val depth: Int,
)

/**
 * What a set of records adds up to. Mirrors upstream's `buildTraceModel` summary — see
 * `scripts/mirrors.json` — and is used both per turn and over everything loaded.
 */
data class TraceSummary(
    val recordCount: Int = 0,
    val turnCount: Int = 0,
    val generationCount: Int = 0,
    val toolCallCount: Int = 0,
    val errorCount: Int = 0,
    val runningCount: Int = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val totalTokens: Long = 0,
    /**
     * Null unless something was priced AND **every** generation was.
     *
     * A total that silently skips a model call the backend did not price would under-report spend,
     * and there is no way to tell that from the number itself, so no number is shown at all.
     */
    val cost: Double? = null,
)

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
    val rows: List<TraceRow>,
) {
    val records: List<TraceRecord> get() = rows.map { it.record }

    /** Earliest start in the turn, which is what orders turns against each other. */
    val startTime: String =
        rows.mapNotNull { it.record.startTime.takeIf(String::isNotEmpty) }.minOrNull().orEmpty()

    val summary: TraceSummary = summarizeTrace(rows.map { it.record })
}

/**
 * Groups accumulated records into turns, newest first, each turn's records in tree order.
 *
 * Takes everything loaded so far rather than one page: a turn that spans a page boundary is only
 * complete once both pages are in, so this must be re-run over the accumulated list on every page
 * rather than appended to.
 *
 * Ordering is by `startTime` — ISO-8601, so lexicographic order is chronological, and no date
 * parsing is needed here — with the record id as the tiebreak so two records stamped in the same
 * millisecond do not swap between renders. A record with no start sorts last among its siblings
 * rather than first, since an absent timestamp says nothing about when it ran; upstream drops such
 * a record entirely, which on a diagnostic surface hides the one row most likely to be the problem.
 *
 * **Turns are newest first, where upstream's desktop viewer is oldest first.** Deliberate: the
 * whole surface is opened to look at the turn that just settled, and on a phone oldest-first means
 * scrolling past every earlier turn to reach it. It also matches the direction the server pages in,
 * so "load older" appends at the bottom.
 */
fun groupTraceRecords(records: List<TraceRecord>): List<TraceTurn> =
    records
        .distinctBy { it.id }
        .groupBy { it.messageId }
        .map { (messageId, turnRecords) -> TraceTurn(messageId, buildRows(turnRecords)) }
        .sortedWith(compareByDescending<TraceTurn> { it.startTime }.thenBy { it.messageId })

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

/** See [TraceSummary]. Counts and token totals cover generations only, as upstream's do. */
fun summarizeTrace(records: List<TraceRecord>): TraceSummary {
    var generations = 0
    var toolCalls = 0
    var errors = 0
    var running = 0
    var input = 0L
    var output = 0L
    var total = 0L
    var cost = 0.0
    var priced = 0
    var unpricedGenerations = 0

    for (record in records) {
        when (record.status) {
            TraceStatus.ERROR -> errors++
            TraceStatus.RUNNING -> running++
        }
        if (record.kind == TraceRecordKind.TOOL) toolCalls++
        if (record.kind == TraceRecordKind.GENERATION) {
            generations++
            val recordInput = record.usage?.input ?: 0
            val recordOutput = record.usage?.output ?: 0
            input += recordInput
            output += recordOutput
            total += record.usage?.total ?: (recordInput + recordOutput)
            if (record.cost == null) unpricedGenerations++
        }
        record.cost?.let {
            priced++
            cost += it
        }
    }

    return TraceSummary(
        recordCount = records.size,
        turnCount = records.distinctBy { it.messageId }.size,
        generationCount = generations,
        toolCallCount = toolCalls,
        errorCount = errors,
        runningCount = running,
        inputTokens = input,
        outputTokens = output,
        totalTokens = total,
        cost = if (priced > 0 && unpricedGenerations == 0) cost else null,
    )
}

/**
 * Resolves one turn's records into a depth-annotated tree, flattened depth-first.
 *
 * A `parentId` is honoured only when it names a record that is **loaded and in this same turn** —
 * a partial page routinely cites a parent that has not arrived, and a record whose parent is
 * missing becomes a root rather than disappearing. Parent cycles are cut, which is not paranoia
 * about the server so much as about what a cycle costs here: a naive walk would not terminate.
 */
private fun buildRows(records: List<TraceRecord>): List<TraceRow> {
    val byId = records.associateBy { it.id }
    val parents = records.associate { record ->
        val parentId = record.parentId
        record.id to parentId?.takeIf { it != record.id && byId.containsKey(it) }
    }.toMutableMap()

    cutCycles(records, parents)

    val order = compareBy<TraceRecord> { it.startTime.ifEmpty { LAST } }.thenBy { it.id }
    val children = records.groupBy { parents[it.id] }.mapValues { (_, group) -> group.sortedWith(order) }

    val rows = ArrayList<TraceRow>(records.size)
    val stack = ArrayDeque<TraceRow>()
    children[null].orEmpty().asReversed().forEach { stack.addLast(TraceRow(it, 0)) }
    while (stack.isNotEmpty()) {
        val row = stack.removeLast()
        rows += row
        children[row.record.id].orEmpty().asReversed().forEach {
            stack.addLast(TraceRow(it, row.depth + 1))
        }
    }
    return rows
}

private fun cutCycles(records: List<TraceRecord>, parents: MutableMap<String, String?>) {
    val settled = HashSet<String>()
    for (record in records) {
        val path = LinkedHashSet<String>()
        var current: String? = record.id
        var previous: String? = null
        while (current != null && current !in settled) {
            if (!path.add(current)) {
                parents[previous ?: current] = null
                break
            }
            previous = current
            current = parents[current]
        }
        settled += path
    }
}

/** Sorts after every real ISO-8601 timestamp. */
private const val LAST = "￿"
