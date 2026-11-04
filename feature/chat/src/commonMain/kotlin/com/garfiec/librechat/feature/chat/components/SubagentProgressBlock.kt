package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.background.SubagentDigest
import com.garfiec.librechat.core.model.background.SubagentDigestKind
import com.garfiec.librechat.core.model.background.SubagentDigestNode
import com.garfiec.librechat.core.model.background.SubagentDigestStatus
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import org.jetbrains.compose.resources.stringResource

/** A digest node with the children its path places under it. */
internal data class DigestTreeNode(val node: SubagentDigestNode, val children: List<DigestTreeNode>)

/** `3.2` belongs to `3`, `3.1-20` to `3`, and `1-14` to the run itself (upstream `parentPath`). */
internal fun digestParentPath(path: String): String {
    val base = path.substringBefore('-')
    val dot = base.lastIndexOf('.')
    return if (dot < 0) "" else base.substring(0, dot)
}

/** Rebuilds the tree the digest flattened into path-addressed nodes (upstream `buildDigestTree`). */
internal fun buildDigestTree(nodes: List<SubagentDigestNode>): List<DigestTreeNode> {
    class Builder(val node: SubagentDigestNode) {
        val children = mutableListOf<Builder>()
        fun build(): DigestTreeNode = DigestTreeNode(node, children.map { it.build() })
    }
    val roots = mutableListOf<Builder>()
    val byPath = mutableMapOf<String, Builder>()
    nodes.forEach { node ->
        val entry = Builder(node)
        byPath[node.path] = entry
        val parent = byPath[digestParentPath(node.path)]
        (parent?.children ?: roots).add(entry)
    }
    return roots.map { it.build() }
}

/** Whether [path] is on the way to the child's current step, so its branch opens by default. */
internal fun isOnDigestPath(path: String, active: String?): Boolean =
    active != null && (active == path || active.startsWith("$path."))

/**
 * The server's model-facing English tally of folded children ("bash_tool ×3, text +2 more",
 * "1 turn, 2 tools"), taken apart so every fragment but a tool name can be localized (upstream
 * `localizeDigestSummary`). MIRRORED grammar, registered in `scripts/mirrors.json`.
 */
internal sealed interface DigestSummary {
    data class Nested(val turns: String, val tools: String) : DigestSummary

    /** [name] `text` is a reply; [count] is null for a single one. */
    data class Entry(val name: String, val count: String?)

    data class Entries(val entries: List<Entry>, val more: String?) : DigestSummary
}

internal fun parseDigestSummary(summary: String): DigestSummary {
    NESTED_SUMMARY.matchEntire(summary)?.let { return DigestSummary.Nested(it.groupValues[1], it.groupValues[2]) }
    val more = MORE_SUFFIX.find(summary)
    val body = if (more == null) summary else summary.substring(0, more.range.first)
    val entries = body.split(", ").map { entry ->
        val counted = COUNTED_ENTRY.matchEntire(entry)
        if (counted == null) {
            DigestSummary.Entry(entry, null)
        } else {
            DigestSummary.Entry(counted.groupValues[1], counted.groupValues[2])
        }
    }
    return DigestSummary.Entries(entries, more?.groupValues?.get(1))
}

// `[0-9]`, not `\d`: ICU widens `\d` to every Unicode digit, JavaScript's is ASCII.
private val NESTED_SUMMARY = Regex("""([0-9]+) turns?, ([0-9]+) tools?""")
private val COUNTED_ENTRY = Regex("""(.+) ×([0-9]+)""")
private val MORE_SUFFIX = Regex(""" \+([0-9]+) more\z""")

/**
 * A background subagent's folded progress tree, under its task in a `check_background_task` card
 * (upstream `SubagentProgress.tsx`, v0.8.9-rc1): turns and ranges fold, and the branch holding the
 * child's current step opens by default. The full transcript stays in the thread viewer.
 *
 * A Column of rows, not a nested lazy list: it sits inside the message list's lazy item and is
 * bounded by the server at 200 nodes.
 */
@Composable
internal fun SubagentProgressBlock(digest: SubagentDigest, stateKey: String, modifier: Modifier = Modifier) {
    val tree = remember(digest.nodes) { buildDigestTree(digest.nodes) }
    val active = digest.active ?: digest.nodes.lastOrNull()?.path
    Column(modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.subagent_progress),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(Res.string.subagent_progress_counts, digest.turns, digest.tools, digest.errors),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (digest.thinking) {
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.subagent_progress_thinking),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        tree.forEach { DigestBranch(it, active, stateKey, depth = 0) }
        if (digest.truncated) {
            Text(
                stringResource(Res.string.subagent_progress_truncated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun DigestBranch(entry: DigestTreeNode, active: String?, stateKey: String, depth: Int) {
    val indent = Modifier.padding(start = (depth * 12).dp)
    if (entry.children.isEmpty()) {
        DigestNodeRow(entry.node, modifier = indent)
        return
    }
    // Saveable: this sits in a lazy item, and plain state would re-fold on scroll-back.
    var open by rememberSaveable(key = "digest:$stateKey:${entry.node.path}") {
        mutableStateOf(isOnDigestPath(entry.node.path, active))
    }
    val state = stringResource(if (open) Res.string.state_expanded else Res.string.state_collapsed)
    DigestNodeRow(
        entry.node,
        expanded = open,
        modifier = indent
            .clickable { open = !open }
            .semantics {
                role = Role.Button
                stateDescription = state
            },
    )
    if (open) entry.children.forEach { DigestBranch(it, active, stateKey, depth + 1) }
}

@Composable
private fun DigestNodeRow(node: SubagentDigestNode, modifier: Modifier = Modifier, expanded: Boolean? = null) {
    val title = when (node.kind) {
        SubagentDigestKind.TOOL -> node.label ?: node.name.orEmpty()
        SubagentDigestKind.TEXT -> stringResource(Res.string.subagent_progress_reply)
        SubagentDigestKind.RANGE -> stringResource(Res.string.subagent_progress_turns, node.path)
        SubagentDigestKind.TURN -> stringResource(Res.string.subagent_progress_turn, node.path)
    }
    val summary = node.summary?.let { localizedDigestSummary(it) }
    val localeTag = LocalDateTimeFormat.current.localeTag
    Column(modifier.fillMaxWidth().heightIn(min = 24.dp).padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (expanded != null) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                color = if (node.kind == SubagentDigestKind.TOOL || node.kind == SubagentDigestKind.TEXT) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (summary != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Spacer(Modifier.weight(1f))
            node.ms?.let {
                Text(
                    formatTraceDuration(it, localeTag),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            node.status?.let { DigestStatusDot(it) }
        }
        if (node.evicted) {
            Text(
                stringResource(Res.string.subagent_progress_evicted),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DigestStatusDot(status: SubagentDigestStatus) {
    val label = stringResource(
        when (status) {
            SubagentDigestStatus.RUNNING -> Res.string.background_task_status_running
            SubagentDigestStatus.OK -> Res.string.wakeup_status_completed
            SubagentDigestStatus.ERROR -> Res.string.wakeup_status_failed
            SubagentDigestStatus.CANCELLED -> Res.string.wakeup_status_cancelled
        },
    )
    val color = when (status) {
        SubagentDigestStatus.RUNNING -> MaterialTheme.colorScheme.primary
        SubagentDigestStatus.OK -> MaterialTheme.colorScheme.tertiary
        SubagentDigestStatus.ERROR -> MaterialTheme.colorScheme.error
        SubagentDigestStatus.CANCELLED -> MaterialTheme.colorScheme.outline
    }
    Box(
        Modifier
            .padding(start = 6.dp)
            .size(8.dp)
            .background(color = color, shape = CircleShape)
            .semantics { contentDescription = label },
    )
}

@Composable
private fun localizedDigestSummary(summary: String): String =
    when (val parsed = remember(summary) { parseDigestSummary(summary) }) {
        is DigestSummary.Nested -> stringResource(Res.string.subagent_progress_nested, parsed.turns, parsed.tools)
        is DigestSummary.Entries -> {
            val reply = stringResource(Res.string.subagent_progress_reply)
            val parts = parsed.entries.map { entry ->
                val label = if (entry.name == "text") reply else entry.name
                if (entry.count == null) label else "$label ×${entry.count}"
            }
            val more = parsed.more?.let { stringResource(Res.string.subagent_progress_more, it) }
            (parts + listOfNotNull(more)).joinToString(", ")
        }
    }
