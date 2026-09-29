package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.wakeup.WakeupDisplay
import com.garfiec.librechat.core.model.wakeup.WakeupKind
import com.garfiec.librechat.core.model.wakeup.WakeupMessage
import com.garfiec.librechat.core.model.wakeup.WakeupTask
import com.garfiec.librechat.core.model.wakeup.WakeupTaskStatus
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import org.jetbrains.compose.resources.stringResource

/**
 * The wake-up this message is, when it should render as a system event rather than a user bubble.
 *
 * Only a user-authored message can be one (the server saves wake-ups as user turns), and never
 * while it is being edited: the row's footer offers Edit, and upstream's `MessageRender` drops the
 * system treatment under edit too, so the ordinary bubble's edit field shows the text the user is
 * actually changing. Callers must pass the same `isEditing` to the list's `contentType` as to the
 * body, or a slot composed for one layout is reused for the other.
 */
internal fun systemEventFor(message: Message, isEditing: Boolean): WakeupDisplay? =
    if (!message.isCreatedByUser || isEditing) null else WakeupMessage.parse(message.text)

/**
 * A host-authored wake-up turn — a detached subagent or background tool task settling and
 * resuming the run — as a system event: a "System" label, an outlined header naming what settled,
 * and the durable results behind a toggle. The web renders the same turn right-aligned and
 * outlined under the same label (upstream `Wakeup.tsx` / `SystemEvent.tsx`).
 *
 * A subagent's thread is shown by id only. The web can open it in a side panel; this app has no
 * subagent-thread surface, so it is a label, not a link.
 *
 * The footer keeps what the row would have had as a user bubble — the sibling switcher and the
 * user-turn actions — as upstream's footer does (`MessageRender`'s `SiblingSwitch` + `HoverButtons`).
 * A branch that forks at a wake-up must stay reachable from it. The footer is always shown rather
 * than tap-revealed: a tap on this row toggles the results.
 */
@Composable
internal fun SystemEventRow(
    display: WakeupDisplay,
    messageId: String,
    modifier: Modifier = Modifier,
    siblingIndex: Int = 0,
    siblingCount: Int = 1,
    onSiblingNavigation: ((Int) -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onReadAloud: (() -> Unit)? = null,
    isReading: Boolean = false,
    onFork: (() -> Unit)? = null,
) {
    var expanded by rememberSaveable(messageId) { mutableStateOf(false) }
    val anyFailed = display.tasks.any { it.status == WakeupTaskStatus.ERROR }
    val headerColor = if (anyFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            stringResource(Res.string.system_event_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth(if (expanded) 1f else COLLAPSED_WIDTH_FRACTION)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded },
            ) {
                Icon(
                    if (display.kind == WakeupKind.SUBAGENT) Icons.Default.Groups else Icons.Default.Build,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = headerColor,
                )
                Text(
                    wakeupHeaderLabel(display),
                    style = MaterialTheme.typography.labelLarge,
                    color = headerColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                wakeupNameSummary(display).takeIf { it.isNotEmpty() }?.let { names ->
                    Text(
                        "· $names",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Text(
                    stringResource(Res.string.wakeup_explainer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                display.tasks.forEach { task -> WakeupTaskCard(task = task, kind = display.kind) }
            }
        }
        val navigable = siblingCount > 1 && onSiblingNavigation != null
        if (navigable || onCopy != null || onEdit != null || onReadAloud != null || onFork != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp).testTag("message_actions"),
            ) {
                ActionButtons(
                    isUser = true,
                    onFeedback = null,
                    currentFeedback = null,
                    onPickFeedbackTag = {},
                    onCopy = onCopy,
                    onEdit = onEdit,
                    onRegenerate = null,
                    onReadAloud = onReadAloud,
                    isReading = isReading,
                    onFork = onFork,
                )
                // A user turn is right-aligned, so its sibling switcher sits at the outer edge.
                onSiblingNavigation?.takeIf { navigable }?.let { navigate ->
                    SiblingNavigator(siblingIndex = siblingIndex, siblingCount = siblingCount, onNavigate = navigate)
                }
            }
        }
    }
}

@Composable
private fun WakeupTaskCard(task: WakeupTask, kind: WakeupKind) {
    val failed = task.status == WakeupTaskStatus.ERROR
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val title = if (kind == WakeupKind.SUBAGENT) task.subagentType else task.toolName
            if (!title.isNullOrEmpty()) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                stringResource(
                    when (task.status) {
                        WakeupTaskStatus.COMPLETED -> Res.string.wakeup_status_completed
                        WakeupTaskStatus.ERROR -> Res.string.wakeup_status_failed
                        WakeupTaskStatus.CANCELLED -> Res.string.wakeup_status_cancelled
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        task.threadId?.let { threadId ->
            Text(
                stringResource(Res.string.wakeup_subagent_thread, threadId),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (task.result.isNotBlank()) {
            // Bounded like the web's `max-h-96`: one runaway result must not push the rest of the
            // thread a screen away.
            Column(
                Modifier
                    .padding(top = 6.dp)
                    .heightIn(max = RESULT_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState()),
            ) {
                MarkdownContent(text = task.result)
            }
        }
    }
}

@Composable
private fun wakeupHeaderLabel(display: WakeupDisplay): String {
    if (display.kind == WakeupKind.SUBAGENT) {
        return stringResource(
            when (display.tasks.firstOrNull()?.status ?: WakeupTaskStatus.COMPLETED) {
                WakeupTaskStatus.COMPLETED -> Res.string.wakeup_subagent_completed
                WakeupTaskStatus.ERROR -> Res.string.wakeup_subagent_failed
                WakeupTaskStatus.CANCELLED -> Res.string.wakeup_subagent_cancelled
            },
        )
    }
    if (display.tasks.size > 1) return stringResource(Res.string.wakeup_tasks_finished, display.tasks.size)
    return stringResource(
        if (display.tasks.firstOrNull()?.status == WakeupTaskStatus.ERROR) {
            Res.string.wakeup_task_failed
        } else {
            Res.string.wakeup_task_finished
        },
    )
}

/**
 * The muted detail after the header: the subagent type, or up to three distinct tool names and a
 * `+N` for the rest, as upstream's `nameSummary`. Tool names are shown raw; the web's display-label
 * lookup has no counterpart here.
 */
internal fun wakeupNameSummary(display: WakeupDisplay): String {
    if (display.kind == WakeupKind.SUBAGENT) return display.tasks.firstOrNull()?.subagentType.orEmpty()
    val names = display.tasks.mapNotNull { it.toolName?.takeIf(String::isNotEmpty) }.distinct()
    return if (names.size > MAX_SUMMARY_NAMES) {
        names.take(MAX_SUMMARY_NAMES).joinToString(", ") + ", +${names.size - MAX_SUMMARY_NAMES}"
    } else {
        names.joinToString(", ")
    }
}

private const val MAX_SUMMARY_NAMES = 3
private const val COLLAPSED_WIDTH_FRACTION = 0.85f
private val RESULT_MAX_HEIGHT = 384.dp
