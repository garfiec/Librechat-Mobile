package com.garfiec.librechat.feature.agents.util

import com.garfiec.librechat.core.model.EndpointToolApproval
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * An agent's per-tool approval mode, `tool_options[<toolId>].approval_mode` (v0.8.9-rc1). No value
 * (null at the call sites) is "use inherited policy". MCP tools only: upstream binds grants to an
 * MCP server, so a mode on any other tool would save and do nothing.
 */
enum class ToolApprovalMode(val wire: String) {
    ASK("ask"),
    ALLOW("allow"),
    CHAT("chat"),
    ALWAYS("always"),
    ;

    /** `chat` and `always` remember an approve; only those can be reset. */
    val remembers: Boolean get() = this == CHAT || this == ALWAYS

    companion object {
        fun fromWire(value: String?): ToolApprovalMode? = entries.firstOrNull { it.wire == value }
    }
}

/** What an admin rule leaves selectable for a tool: under either, only inherit and Always ask. */
enum class ToolApprovalConstraint { ASK, DENY }

private const val APPROVAL_MODE = "approval_mode"
private const val APPROVAL_REVISION = "approval_revision"

/** The mode saved for [toolId] in a `tool_options` map, or null for inherit (or a value this build doesn't know). */
fun JsonObject?.approvalModeOf(toolId: String): ToolApprovalMode? =
    ((this?.get(toolId) as? JsonObject)?.get(APPROVAL_MODE) as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.content
        .let(ToolApprovalMode::fromWire)

/**
 * Port of upstream `withApprovalModes` (`client/src/hooks/Agents/useMCPToolOptions.ts`) for one tool.
 *
 * Setting the mode the tool already has is a no-op, unless it is a remembering mode without a valid
 * revision (the server ignores chat/always without one). Any other change writes a fresh
 * `approval_revision`, which invalidates every grant made under the old one. Inherit removes both
 * fields and keeps the tool's other options (`defer_loading` and the like), dropping the entry only
 * when nothing else is left. Returns null for an empty map, as the editor holds no-options.
 */
@OptIn(ExperimentalUuidApi::class)
fun JsonObject?.withApprovalMode(
    toolId: String,
    mode: ToolApprovalMode?,
    newRevision: () -> String = { Uuid.random().toString() },
): JsonObject? {
    val current = this?.get(toolId) as? JsonObject
    val currentMode = this.approvalModeOf(toolId)
    val revision = (current?.get(APPROVAL_REVISION) as? JsonPrimitive)?.takeIf { it.isString }?.content
    if (currentMode == mode && (mode?.remembers != true || isUuid(revision))) return this
    val rest = current.orEmpty().filterKeys { it != APPROVAL_MODE && it != APPROVAL_REVISION }
    val updated = this.orEmpty().toMutableMap()
    when {
        mode != null -> updated[toolId] = JsonObject(
            rest + mapOf(APPROVAL_MODE to JsonPrimitive(mode.wire), APPROVAL_REVISION to JsonPrimitive(newRevision())),
        )
        rest.isNotEmpty() -> updated[toolId] = JsonObject(rest)
        else -> updated.remove(toolId)
    }
    return if (updated.isEmpty()) null else JsonObject(updated)
}

/** The 8-4-4-4-12 hex shape the server's `z.string().uuid()` checks. Hand-written, no Regex. */
internal fun isUuid(value: String?): Boolean {
    if (value == null || value.length != UUID_LENGTH) return false
    return value.withIndex().all { (index, char) ->
        if (index in UUID_HYPHENS) char == '-' else char.isHexDigit()
    }
}

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private const val UUID_LENGTH = 36
private val UUID_HYPHENS = setOf(8, 13, 18, 23)

/**
 * Port of upstream `getToolApprovalConstraint` (`packages/data-provider/src/approval.ts`): which
 * modes the admin's policy leaves for [toolName] (the full MCP id). Presentation only; the server
 * enforces the real policy. `deny` wins, then `ask`, then `allow`; a tool on no list is `ask` unless
 * the mode is `bypass` (`dontAsk` denies). Mirror `tool-approval-constraint` in scripts/mirrors.json.
 */
fun toolApprovalConstraint(policy: EndpointToolApproval?, toolName: String): ToolApprovalConstraint? {
    if (policy?.enabled != true) return null
    fun matches(patterns: List<String>?) = patterns?.any { globMatches(it, toolName) } == true
    return when {
        matches(policy.deny) -> ToolApprovalConstraint.DENY
        matches(policy.ask) -> ToolApprovalConstraint.ASK
        matches(policy.allow) -> null
        policy.mode == "dontAsk" -> ToolApprovalConstraint.DENY
        policy.mode != "bypass" -> ToolApprovalConstraint.ASK
        else -> null
    }
}

/**
 * Whole-string glob match where `*` is any run (including none) and every other character is
 * literal, as upstream's escaped `RegExp('^' + … + '$')`. Hand-written two-pointer match rather
 * than a Regex built from admin-supplied patterns, which ICU and the JVM need not parse alike.
 */
internal fun globMatches(pattern: String, text: String): Boolean {
    var p = 0
    var t = 0
    var starAt = -1
    var resumeAt = 0
    while (t < text.length) {
        when {
            p < pattern.length && pattern[p] == '*' -> {
                starAt = p++
                resumeAt = t
            }
            p < pattern.length && pattern[p] == text[t] -> {
                p++
                t++
            }
            starAt >= 0 -> {
                p = starAt + 1
                t = ++resumeAt
            }
            else -> return false
        }
    }
    while (p < pattern.length && pattern[p] == '*') p++
    return p == pattern.length
}
