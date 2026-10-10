package com.garfiec.librechat.core.model.trace

/**
 * One step of a turn in the simple view (v0.8.8-rc4): a model call and the tools that followed it,
 * read as "the model decided, then these ran". [rows] lists only what the simple view shows —
 * model calls, tools, failures and the title run — each under its nearest shown ancestor.
 */
data class TraceStep(
    /** Keyed by the step's own record, so a fold survives an older page renumbering the steps. */
    val key: String,
    val messageId: String,
    /** 1-based among the turn's steps of the same [origin]; the title run numbers its own. */
    val index: Int,
    /** `run` for the response run, [TraceOrigin.TITLE] for the title generation. */
    val origin: String,
    val generationId: String?,
    /**
     * The tool round that ran the step's calls. A model call asks for one round, so a step holding
     * several is one an approval paused, and the last round is the one that ran them.
     */
    val roundId: String?,
    val agentId: String?,
    val rows: List<TraceRow>,
    val recordCount: Int,
    val errorCount: Int,
    val toolCallCount: Int,
    /** Tool call counts by tool name, in first-call order. */
    val toolNames: Map<String, Int>,
    /** What the step's whole subtree cost, when every model call in it has a price. */
    val cost: Double?,
    val startTime: String,
) {
    val isTitle: Boolean get() = origin == TraceOrigin.TITLE

    companion object {
        const val ORIGIN_RUN = "run"
    }
}

/**
 * The simple view of one turn, and the steps it groups into.
 *
 * MIRRORED from upstream `buildTraceModel`'s simple mode (`client/src/components/Chat/Trace/model.ts`:
 * `isListed`, `nearestStepAncestor`, `stepRoots`, `groupSteps`). The lane logic is what keeps a
 * page cut from misfiling steps: a record limit cuts a long response's earliest records, so the
 * walk to a record's lane can end at a wrapper that only frames one model call, and parallel agents
 * cut at the same place cannot be told apart. Ported as upstream has it rather than simplified — a
 * simpler grouping reads correctly on every whole trace and only goes wrong on a paged one.
 */
internal class TraceStepGrouping(
    private val graph: TraceGraph,
    private val messageId: String,
    turnRecords: List<TraceRecord>,
) {
    private val records = turnRecords.sortedWith(TraceGraph.ORDER)

    /** A turn with no model call, tool or failure yet lists its wrappers instead of nothing. */
    private val hasWork = records.any { it.isSimpleRecord() }

    private val ancestors = HashMap<String, String?>()

    private val listed = records.filter { isListed(it) }

    /** Shown records by the shown ancestor they hang from, in ledger order. */
    private val viewChildren: Map<String, List<TraceRecord>> = listed
        .mapNotNull { record -> nearestStepAncestor(record.id)?.let { it to record } }
        .groupBy({ it.first }, { it.second })

    /** Tools a round named without recording each one; they count toward the turn's tool calls. */
    var namedToolCalls = 0
        private set

    fun steps(): List<TraceStep> {
        val roots = listed.filter { nearestStepAncestor(it.id) == null }
        val steps = roots
            .groupBy { it.origin ?: TraceStep.ORIGIN_RUN }
            .flatMap { (origin, originRoots) -> stepsOf(origin, originRoots.map { it.id }) }
        return steps.sortedWith(
            compareBy<TraceStep> { it.startTime.ifEmpty { TraceGraph.LAST } }.thenBy { if (it.isTitle) 1 else 0 },
        )
    }

    private fun isListed(record: TraceRecord): Boolean =
        record.isSimpleRecord() || (graph.parentOf[record.id] == null && !hasWork)

    /** Nearest model/tool ancestor, cached with path compression. */
    private fun nearestStepAncestor(id: String): String? {
        val path = ArrayList<String>()
        var current = id
        val result: String?
        while (true) {
            if (ancestors.containsKey(current)) {
                result = ancestors[current]
                break
            }
            path += current
            val parent = graph.parentOf[current]
            if (parent == null || graph.byId[parent]?.isStepAnchor() == true) {
                result = parent
                break
            }
            current = parent
        }
        path.forEach { ancestors[it] = result }
        return result
    }

    private class Group(val rootIds: MutableList<String>, val lane: String)

    private fun stepsOf(origin: String, rootIds: List<String>): List<TraceStep> {
        val groups = ArrayList<Group>()
        val latestByLane = HashMap<String, Group>()
        // Model calls whose lane is a private wrapper, until the round each asked for arrives.
        val waiting = LinkedHashSet<Group>()
        var leading = ArrayList<String>()
        for (id in rootIds) {
            val record = graph.byId[id] ?: continue
            val lane = laneOf(id)
            val current = groups.lastOrNull()
            // The cut can fall inside a model call's own wrappers; the round it asked for then
            // arrives in a lane no model call holds. Only attributable when exactly one such model
            // call is waiting — with two, nothing says which asked.
            val asked = if (record.isToolWork() && lane !in latestByLane && waiting.size == 1) {
                waiting.first()
            } else {
                null
            }
            if (record.isModelCall() || (record.isToolWork() && lane !in latestByLane && asked == null)) {
                val group = Group((leading + id).toMutableList(), lane)
                groups += group
                latestByLane[lane] = group
                leading = ArrayList()
                if (record.isModelCall() && lane in graph.privateWrappers) waiting += group
            } else if (current == null) {
                leading += id
            } else {
                (latestByLane[lane] ?: asked ?: current).rootIds += id
                if (asked != null) {
                    latestByLane[lane] = asked
                    waiting -= asked
                }
            }
        }
        if (leading.isNotEmpty()) groups += Group(leading, laneOf(leading.first()))
        return groups.mapIndexed { index, group -> step(origin, index + 1, group.rootIds) }
    }

    private fun step(origin: String, index: Int, rootIds: List<String>): TraceStep {
        val generationId = rootIds.firstOrNull { graph.byId[it]?.isModelCall() == true }
        val roundId = rootIds.lastOrNull { graph.byId[it]?.role == TraceRole.TOOLS }
        val anchor = generationId ?: rootIds.first()
        val rows = depthFirst(rootIds.mapNotNull { graph.byId[it] }) { viewChildren[it.id] }

        var errors = 0
        val toolNames = LinkedHashMap<String, Int>()
        for (row in rows) {
            val record = row.record
            if (record.status == TraceStatus.ERROR) errors++
            // Names stand in for tools that were never recorded: only for the round that ran, and
            // only when it holds no recorded tool of its own to count instead.
            val named = if (record.id == roundId && graph.children[record.id].orEmpty().none { it.isToolWork() }) {
                record.tools.orEmpty()
            } else {
                emptyList()
            }
            namedToolCalls += named.size
            val calls = if (record.kind == TraceRecordKind.TOOL) listOf(record.name) else named
            calls.forEach { toolNames[it] = (toolNames[it] ?: 0) + 1 }
        }

        // Spend is the whole subtree's, not the listed projection's: the simple view rolls spans
        // up out of sight, and any record may carry a cost.
        val spend = Spend()
        depthFirst(rootIds.mapNotNull { graph.byId[it] }) { graph.children[it.id] }.forEach { spend.add(it.record) }

        return TraceStep(
            key = "step:$messageId:$origin:$anchor",
            messageId = messageId,
            index = index,
            origin = origin,
            generationId = generationId,
            roundId = roundId,
            agentId = graph.agentOf[anchor],
            rows = rows,
            recordCount = rows.size,
            errorCount = errors,
            toolCallCount = toolNames.values.sum(),
            toolNames = toolNames,
            cost = spend.cost,
            startTime = rows.mapNotNull { it.record.startTime.takeIf(String::isNotEmpty) }.minOrNull().orEmpty(),
        )
    }

    private val branches = HashMap<String, String>()

    /**
     * A wrapper that only frames one model call is no lane of its own: when the cut left it
     * topmost, its lane is the unloaded parent it shares with the tool round the model call asked for.
     */
    private fun laneAbove(id: String): String? =
        if (graph.byId[id]?.role == TraceRole.PLUMBING) graph.unloadedParentOf(id) else null

    /** A record's branch immediately below its structural root, cached for nested failure rows. */
    private fun branchOf(id: String): String {
        val path = ArrayList<String>()
        var current = id
        val branch: String
        while (true) {
            val cached = branches[current]
            if (cached != null) {
                branch = cached
                break
            }
            path += current
            val parent = graph.parentOf[current]
            if (parent == null) {
                branch = laneAbove(current) ?: current
                break
            }
            if (graph.parentOf[parent] == null) {
                // A wrapper framing one model call is never a lane, so when the cut left the graph
                // as the topmost loaded record, the lane is that graph, where its tool rounds hang.
                val framing = graph.byId[current]?.role == TraceRole.PLUMBING
                branch = laneAbove(parent) ?: if (framing) parent else current
                break
            }
            current = parent
        }
        path.forEach { branches[it] = branch }
        return branch
    }

    /** Unloaded parents stay distinct lanes: merging them would guess at absent relationships. */
    private fun laneOf(id: String): String =
        graph.parentOf[id]?.let(::branchOf) ?: graph.unloadedParentOf(id) ?: ""
}

/** The records the simple view lists: what the model did, anything that failed, and the title run. */
private fun TraceRecord.isSimpleRecord(): Boolean =
    kind == TraceRecordKind.GENERATION || isToolWork() || status == TraceStatus.ERROR || origin == TraceOrigin.TITLE

/** The records others hang from in the simple view: what the model called. */
private fun TraceRecord.isStepAnchor(): Boolean = kind == TraceRecordKind.GENERATION || isToolWork()
