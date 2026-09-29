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
    /** Model calls of the response. Excludes [labelCount]. */
    val generationCount: Int = 0,
    /** Model calls that wrote an activity label: spend, not work of the response (v0.8.8-rc4). */
    val labelCount: Int = 0,
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
 *
 * [rows] is every record as recorded (upstream's `full` mode); [steps] is the same turn as model
 * calls and the tools they ran (the `simple` mode).
 */
data class TraceTurn(
    val messageId: String,
    val rows: List<TraceRow>,
    val steps: List<TraceStep> = emptyList(),
    /**
     * Some of the response's records hang from a parent that is not loaded. Records load newest
     * first and a run's root starts first, so this is what a page boundary leaves of a response it
     * cut: its end.
     */
    val split: Boolean = false,
    val summary: TraceSummary = summarizeTrace(rows.map { it.record }),
) {
    val records: List<TraceRecord> get() = rows.map { it.record }

    /** Earliest start in the turn, which is what orders turns against each other. */
    val startTime: String =
        rows.mapNotNull { it.record.startTime.takeIf(String::isNotEmpty) }.minOrNull().orEmpty()
}

/**
 * Groups accumulated records into turns, newest first, each turn's records in tree order.
 *
 * Takes everything loaded so far rather than one page: a turn that spans a page boundary is only
 * complete once both pages are in, so this must be re-run over the accumulated list on every page
 * rather than appended to. [hasOlder] says older pages remain, which is what withholds the oldest
 * loaded turn's cost (see [TraceSummary.cost] and the per-turn rule in [groupTraceRecords]).
 *
 * Ordering is by `startTime` — ISO-8601, so lexicographic order is chronological, and no date
 * parsing is needed here — then by kind, so a model call sorts before the tool it asked for when
 * both are stamped in the same millisecond, with the record id as the final tiebreak so two records
 * stamped in the same millisecond do not swap between renders. A record with no start sorts last
 * among its siblings rather than first, since an absent timestamp says nothing about when it ran;
 * upstream drops such a record entirely, which on a diagnostic surface hides the one row most
 * likely to be the problem.
 *
 * **Turns are newest first, where upstream's desktop viewer is oldest first.** Deliberate: the
 * whole surface is opened to look at the turn that just settled, and on a phone oldest-first means
 * scrolling past every earlier turn to reach it. It also matches the direction the server pages in,
 * so "load older" appends at the bottom.
 *
 * **A turn's cost is withheld when the turn is not known to be whole** — when it is [TraceTurn.split],
 * or it is the oldest loaded turn while [hasOlder]: a page can end between a response's traces with
 * every loaded parent in place, and the sum of a response's newest records is not its cost.
 */
fun groupTraceRecords(records: List<TraceRecord>, hasOlder: Boolean = false): List<TraceTurn> {
    val graph = TraceGraph(records.distinctBy { it.id })
    val turns = graph.records
        .groupBy { it.messageId }
        .map { (messageId, turnRecords) -> graph.turn(messageId, turnRecords) }
        .sortedWith(compareByDescending<TraceTurn> { it.startTime }.thenBy { it.messageId })
    return turns.mapIndexed { index, turn ->
        val partial = turn.split || (hasOlder && index == turns.lastIndex)
        if (partial && turn.summary.cost != null) turn.copy(summary = turn.summary.copy(cost = null)) else turn
    }
}

/**
 * The summary over every loaded turn. Sums the per-turn tool counts, which include tools a round
 * named without recording them one by one; its cost is over every record, as upstream's is.
 */
fun summarizeTraceTurns(turns: List<TraceTurn>): TraceSummary {
    val all = summarizeTrace(turns.flatMap { it.records })
    return all.copy(toolCallCount = turns.sumOf { it.summary.toolCallCount })
}

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

/**
 * See [TraceSummary]. Counts and token totals cover generations only, as upstream's do; a
 * generation that wrote an activity label counts as a label, not a generation, but its tokens and
 * its price still count.
 */
fun summarizeTrace(records: List<TraceRecord>): TraceSummary {
    var generations = 0
    var labels = 0
    var toolCalls = 0
    var errors = 0
    var running = 0
    var input = 0L
    var output = 0L
    var total = 0L
    val spend = Spend()

    for (record in records) {
        when (record.status) {
            TraceStatus.ERROR -> errors++
            TraceStatus.RUNNING -> running++
        }
        if (record.kind == TraceRecordKind.TOOL) toolCalls++
        if (record.kind == TraceRecordKind.GENERATION) {
            if (record.isLabelRecord()) labels++ else generations++
            val recordInput = record.usage?.input ?: 0
            val recordOutput = record.usage?.output ?: 0
            input += recordInput
            output += recordOutput
            total += record.usage?.total ?: (recordInput + recordOutput)
        }
        spend.add(record)
    }

    return TraceSummary(
        recordCount = records.size,
        turnCount = records.distinctBy { it.messageId }.size,
        generationCount = generations,
        labelCount = labels,
        toolCallCount = toolCalls,
        errorCount = errors,
        runningCount = running,
        inputTokens = input,
        outputTokens = output,
        totalTokens = total,
        cost = spend.cost,
    )
}

/**
 * Spend over a set of records. MIRRORED from upstream's `Spend`/`costOf`: a total is given only
 * when something was priced and no model call went unpriced.
 */
internal class Spend {
    private var sum = 0.0
    private var priced = 0
    private var unpriced = 0

    fun add(record: TraceRecord) {
        val cost = record.cost
        if (cost != null) {
            priced++
            sum += cost
        } else if (record.kind == TraceRecordKind.GENERATION) {
            unpriced++
        }
    }

    val cost: Double? get() = if (priced > 0 && unpriced == 0) sum else null
}

/**
 * Every loaded record with its resolved tree: the parent each one is honoured under, the children
 * each one has, and the saved agent each one ran under.
 *
 * A `parentId` is honoured only when it names a record that is **loaded and in this same turn** —
 * a partial page routinely cites a parent that has not arrived, and a record whose parent is
 * missing becomes a root rather than disappearing. Parent cycles are cut, which is not paranoia
 * about the server so much as about what a cycle costs here: a naive walk would not terminate.
 */
internal class TraceGraph(val records: List<TraceRecord>) {
    val byId: Map<String, TraceRecord> = records.associateBy { it.id }

    val parentOf: Map<String, String?> = records.associate { record ->
        val parentId = record.parentId
        record.id to parentId?.takeIf { it != record.id && byId[it]?.messageId == record.messageId }
    }.toMutableMap().also(::cutCycles)

    /** Children by parent (null = roots), each list in ledger order. */
    val children: Map<String?, List<TraceRecord>> =
        records.groupBy { parentOf[it.id] }.mapValues { (_, group) -> group.sortedWith(ORDER) }

    /** The saved agent each record ran under: its nearest ancestor's (or its own) `agentId`. */
    val agentOf: Map<String, String?> = resolveAgents()

    /** A parent id that is cited but not loaded — a page boundary cut the record off from it. */
    fun unloadedParentOf(id: String): String? = byId[id]?.parentId?.takeIf { it !in byId }

    /**
     * Unloaded parents that frame a single model call. The SDK wraps each model call in wrappers
     * of its own, so an unloaded parent whose loaded children are only such wrappers and a model
     * call is one of those; a graph is told apart by what else hangs from it, the tool rounds.
     */
    val privateWrappers: Set<String> = run {
        val shared = HashSet<String>()
        val private = HashSet<String>()
        for (record in records) {
            val parentId = unloadedParentOf(record.id) ?: continue
            if (record.role == TraceRole.PLUMBING || record.role == TraceRole.MODEL) {
                private += parentId
            } else {
                shared += parentId
            }
        }
        private - shared
    }

    fun turn(messageId: String, turnRecords: List<TraceRecord>): TraceTurn {
        val rows = depthFirst(children[null].orEmpty().filter { it.messageId == messageId }) { children[it.id] }
        val stepping = TraceStepGrouping(this, messageId, turnRecords)
        val steps = stepping.steps()
        val split = turnRecords.any { it.origin == null && unloadedParentOf(it.id) != null }
        val summary = summarizeTrace(turnRecords).let {
            it.copy(toolCallCount = it.toolCallCount + stepping.namedToolCalls)
        }
        return TraceTurn(messageId = messageId, rows = rows, steps = steps, split = split, summary = summary)
    }

    private fun cutCycles(parents: MutableMap<String, String?>) {
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

    private fun resolveAgents(): Map<String, String?> {
        val resolved = HashMap<String, String?>()
        for (record in records) {
            val path = ArrayList<String>()
            var current: String? = record.id
            var agentId: String? = null
            while (current != null) {
                if (resolved.containsKey(current)) {
                    agentId = resolved[current]
                    break
                }
                path += current
                val own = byId[current]?.agentId
                if (own != null) {
                    agentId = own
                    break
                }
                current = parentOf[current]
            }
            path.forEach { resolved[it] = agentId }
        }
        return resolved
    }

    companion object {
        /** Ties at one timestamp are broken causally: the model call that asked before the tool. */
        private val KIND_ORDER = mapOf(
            TraceRecordKind.AGENT to 0,
            TraceRecordKind.SPAN to 0,
            TraceRecordKind.GENERATION to 1,
            TraceRecordKind.TOOL to 2,
            TraceRecordKind.EVENT to 3,
        )

        val ORDER: Comparator<TraceRecord> =
            compareBy<TraceRecord> { it.startTime.ifEmpty { LAST } }
                .thenBy { KIND_ORDER[it.kind] ?: 0 }
                .thenBy { it.id }

        /** Sorts after every real ISO-8601 timestamp. */
        const val LAST = "￿"
    }
}

/** Flattens [roots] and everything [childrenOf] hangs from them, depth-first, with depths. */
internal fun depthFirst(
    roots: List<TraceRecord>,
    childrenOf: (TraceRecord) -> List<TraceRecord>?,
): List<TraceRow> {
    val rows = ArrayList<TraceRow>()
    val stack = ArrayDeque<TraceRow>()
    roots.asReversed().forEach { stack.addLast(TraceRow(it, 0)) }
    while (stack.isNotEmpty()) {
        val row = stack.removeLast()
        rows += row
        childrenOf(row.record).orEmpty().asReversed().forEach { stack.addLast(TraceRow(it, row.depth + 1)) }
    }
    return rows
}

private val LABEL_ROLES = setOf(TraceRole.STEP_LABEL, TraceRole.REASONING_LABEL, TraceRole.PHASE_LABEL)

/** A model call that wrote one of the activity labels the chat shows while a response runs. */
fun TraceRecord.isLabelRecord(): Boolean = role != null && role in LABEL_ROLES

/** A model call of the response itself: it starts a step. */
fun TraceRecord.isModelCall(): Boolean = kind == TraceRecordKind.GENERATION && !isLabelRecord()

/** A tool, or the round of tool calls a host ran without recording each one. */
fun TraceRecord.isToolWork(): Boolean = kind == TraceRecordKind.TOOL || role == TraceRole.TOOLS
