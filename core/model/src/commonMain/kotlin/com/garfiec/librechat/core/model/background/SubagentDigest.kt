package com.garfiec.librechat.core.model.background

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** One node's status in a [SubagentDigest] (upstream `SubagentDigestStatus`). */
enum class SubagentDigestStatus(val wire: String) {
    RUNNING("running"),
    OK("ok"),
    ERROR("error"),
    CANCELLED("cancelled"),
    ;

    companion object {
        fun fromWire(value: String?): SubagentDigestStatus? = entries.firstOrNull { it.wire == value }
    }
}

enum class SubagentDigestKind(val wire: String) {
    TURN("turn"),
    RANGE("range"),
    TOOL("tool"),
    TEXT("text"),
    ;

    companion object {
        fun fromWire(value: String?): SubagentDigestKind? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * One node of a background subagent's progress tree (upstream `SubagentDigestNode`, v0.8.9-rc1).
 * [path] addresses it positionally (`3` is turn 3, `3.2` its second child, `1-14` a folded range of
 * turns) and never shifts as the run grows. Labels are the child's own one-line tool intents; tool
 * arguments, outputs and reply text are never included.
 */
data class SubagentDigestNode(
    val path: String,
    val kind: SubagentDigestKind,
    val status: SubagentDigestStatus? = null,
    /** Tool name for a [SubagentDigestKind.TOOL] node. */
    val name: String? = null,
    val label: String? = null,
    /** Duration, or elapsed time while running. */
    val ms: Long? = null,
    /** Size of a tool output or text reply, never its content. */
    val chars: Long? = null,
    /** Folded children, by kind and count, in the server's model-facing English tally. */
    val summary: String? = null,
    val errors: Int? = null,
    /** The node has children this view did not include. */
    val folded: Boolean = false,
    /** Folded turns whose detail is no longer kept. */
    val evicted: Boolean = false,
)

/** Compact, navigable progress for a running or not-yet-delivered subagent task (upstream `SubagentDigest`). */
data class SubagentDigest(
    val turns: Long,
    val tools: Long,
    val errors: Long,
    val nodes: List<SubagentDigestNode>,
    /** Path of the node the child is working on now. */
    val active: String? = null,
    /** The child is reasoning with no tool or reply in flight. */
    val thinking: Boolean = false,
    val idleMs: Long? = null,
    /** Some nodes were folded or dropped to stay within the output budget. */
    val truncated: Boolean = false,
)

/**
 * MIRRORED from upstream `parseDigest` / `parseDigestNode`
 * (`client/src/components/Chat/Messages/Content/Parts/background.ts`, v0.8.9-rc1), registered in
 * `scripts/mirrors.json` with the rest of that file.
 *
 * **Progress is optional decoration**: a malformed digest is dropped, never the card. Strict like
 * upstream: counts must be finite non-negative numbers, every node must have a valid path and
 * kind, at most [MAX_NODES]; texts are bounded.
 */
object SubagentDigestParser {

    private const val MAX_NODES = 200
    private const val MAX_PATH_CHARS = 64

    @Suppress("ReturnCount")
    fun parse(value: JsonElement?): SubagentDigest? {
        val obj = value as? JsonObject ?: return null
        val turns = obj.count("turns") ?: return null
        val tools = obj.count("tools") ?: return null
        val errors = obj.count("errors") ?: return null
        val rawNodes = obj["nodes"] as? JsonArray ?: return null
        if (rawNodes.size > MAX_NODES) return null
        val nodes = rawNodes.map { parseNode(it) ?: return null }
        val active = obj.boundedText("active", MAX_PATH_CHARS)?.takeIf(::isDigestPath)
        return SubagentDigest(
            turns = turns,
            tools = tools,
            errors = errors,
            nodes = nodes,
            active = active,
            thinking = obj.string("phase") == "thinking",
            idleMs = obj.count("idle_ms"),
            truncated = obj.flag("truncated"),
        )
    }

    private fun parseNode(value: JsonElement): SubagentDigestNode? {
        val obj = value as? JsonObject ?: return null
        val path = obj.string("path")?.takeIf { it.length <= MAX_PATH_CHARS && isDigestPath(it) } ?: return null
        val kind = SubagentDigestKind.fromWire(obj.string("kind")) ?: return null
        return SubagentDigestNode(
            path = path,
            kind = kind,
            status = SubagentDigestStatus.fromWire(obj.string("status")),
            name = obj.boundedText("name", 128),
            label = obj.boundedText("label", 256),
            summary = obj.boundedText("summary", 512),
            ms = obj.count("ms"),
            chars = obj.count("chars"),
            errors = obj.count("errors")?.takeIf { it > 0 }?.toInt(),
            folded = obj.flag("folded"),
            evicted = obj.flag("evicted"),
        )
    }

    /**
     * Upstream `/^\d+(?:\.\d+)*(?:-\d+)?$/`, hand-scanned: ICU's `\d` matches every Unicode digit
     * where JavaScript's is ASCII, and a loop needs no pattern compiled at class init.
     */
    internal fun isDigestPath(path: String): Boolean {
        var i = 0
        fun digits(): Boolean {
            val start = i
            while (i < path.length && path[i] in '0'..'9') i++
            return i > start
        }
        if (!digits()) return false
        while (i < path.length && path[i] == '.') {
            i++
            if (!digits()) return false
        }
        if (i < path.length && path[i] == '-') {
            i++
            if (!digits()) return false
        }
        return i == path.length
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.boundedText(key: String, maxChars: Int): String? =
        string(key)?.takeIf { it.isNotEmpty() }?.take(maxChars)

    /** `typeof v === 'number' && Number.isFinite(v) && v >= 0`. */
    private fun JsonObject.count(key: String): Long? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        if (primitive.isString) return null
        val number = primitive.doubleOrNull ?: return null
        if (!number.isFinite() || number < 0) return null
        return number.toLong()
    }

    private fun JsonObject.flag(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull == true
}
