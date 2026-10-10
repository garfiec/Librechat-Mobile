package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.RunStepStatus
import com.garfiec.librechat.core.model.background.BackgroundTaskDisplay
import com.garfiec.librechat.core.model.background.BackgroundTaskOutcome
import com.garfiec.librechat.core.model.background.BackgroundTaskOutput
import com.garfiec.librechat.core.model.background.PolledTask
import com.garfiec.librechat.core.model.background.PolledTaskStatus
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import org.jetbrains.compose.resources.stringResource

/**
 * A settled `check_background_task` call as a task, list or notice card (v0.8.8, upstream
 * `Parts/BackgroundTaskCall.tsx`). Only host-shaped output gets here — the caller falls back to
 * the generic card when [display] would be null — and the raw output stays one tap away.
 *
 * The verdict comes from the TASK, not the poll step: a poll that ran fine can report a failed
 * task, a rejected control or an incomplete list, and those must not read as a success.
 */
@Composable
internal fun BackgroundTaskCallCard(
    display: BackgroundTaskDisplay,
    output: String,
    intent: String?,
    closedStatus: RunStepStatus?,
    modifier: Modifier = Modifier,
    stateKey: String = "",
) {
    var isExpanded by rememberSaveable(key = "bgtask:$stateKey") { mutableStateOf(false) }
    var showRaw by rememberSaveable(key = "bgtask-raw:$stateKey") { mutableStateOf(false) }
    val outcome = BackgroundTaskOutput.outcome(display)
    val noticeText = (display as? BackgroundTaskDisplay.Notice)
        ?.let { stringResource(BackgroundTaskGuidance.noticeKey(it.status, it.message)) }
    val incompleteText = stringResource(Res.string.background_tasks_incomplete)
    val title = when {
        closedStatus == RunStepStatus.CANCELLED && outcome != BackgroundTaskOutcome.CANCELLED ->
            stringResource(Res.string.cd_tool_cancelled)
        noticeText != null -> noticeText
        display is BackgroundTaskDisplay.TaskList && outcome == BackgroundTaskOutcome.FAILED -> incompleteText
        else -> intent ?: stringResource(Res.string.background_tasks_checked)
    }
    val verdict = when {
        outcome == BackgroundTaskOutcome.FAILED -> RunStepStatus.FAILED
        outcome == BackgroundTaskOutcome.CANCELLED -> RunStepStatus.CANCELLED
        else -> closedStatus
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(12.dp)
                    .semantics { role = Role.Button },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.HourglassTop,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                when (verdict) {
                    RunStepStatus.CANCELLED -> Icon(
                        Icons.Default.Block,
                        stringResource(Res.string.cd_tool_cancelled),
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    RunStepStatus.FAILED -> Icon(
                        Icons.Default.ErrorOutline,
                        stringResource(Res.string.cd_tool_failed),
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    else -> Unit
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    stringResource(if (isExpanded) Res.string.cd_collapse else Res.string.cd_expand),
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    when (display) {
                        is BackgroundTaskDisplay.Task -> PolledTaskBlock(display.task)
                        is BackgroundTaskDisplay.TaskList -> {
                            Text(
                                "${stringResource(Res.string.background_tasks_title)} · ${display.tasks.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (display.tasks.isEmpty()) {
                                Text(
                                    stringResource(Res.string.background_tasks_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            display.tasks.forEachIndexed { index, task ->
                                if (index > 0) AdaptiveDivider(Modifier.padding(vertical = 8.dp))
                                PolledTaskBlock(task)
                            }
                            if (display.partial || display.warning != null) {
                                Text(
                                    incompleteText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                        is BackgroundTaskDisplay.Notice -> Text(
                            noticeText.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (outcome == BackgroundTaskOutcome.FAILED) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    TextButton(onClick = { showRaw = !showRaw }, modifier = Modifier.padding(top = 4.dp)) {
                        Text(stringResource(Res.string.background_tasks_raw_details))
                    }
                    if (showRaw) {
                        Text(output, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
private fun PolledTaskBlock(task: PolledTask) {
    val failed = task.status.isFailure
    val result = task.result?.takeIf { it.isNotBlank() }
    val error = task.error?.takeIf { it.isNotBlank() && it != result }
    val noteKey = task.note?.let(BackgroundTaskGuidance::noteKey)
    val messageKey = task.message?.let(BackgroundTaskGuidance::messageKey)
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (task.toolName == SUBAGENT_TOOL) stringResource(Res.string.background_tasks_subagent) else task.toolName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                task.subagentType?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                deliveryLabel(task.delivery, running = false)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                polledStatusLabel(task.status),
                style = MaterialTheme.typography.labelMedium,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (result != null) {
            TaskText(
                label = stringResource(if (failed && error == null) Res.string.label_error else Res.string.label_output),
                text = result,
                isError = failed && error == null,
            )
        }
        if (error != null) {
            TaskText(label = stringResource(Res.string.label_error), text = error, isError = true)
        }
        if (result == null && error == null) {
            if (task.resultClaimed && task.status != PolledTaskStatus.CLAIMED) {
                Hint(stringResource(Res.string.background_task_status_claimed))
            } else if (task.resultAvailable && !task.resultClaimed) {
                Hint(stringResource(Res.string.background_tasks_result_available))
            }
        }
        noteKey?.let { Hint(stringResource(it)) }
        if (messageKey != null && messageKey != noteKey) Hint(stringResource(messageKey))
    }
}

@Composable
private fun TaskText(label: String, text: String, isError: Boolean) {
    Column(Modifier.padding(top = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = TASK_TEXT_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun polledStatusLabel(status: PolledTaskStatus): String = stringResource(
    when (status) {
        PolledTaskStatus.RUNNING -> Res.string.background_task_status_running
        PolledTaskStatus.STOPPING -> Res.string.background_task_status_stopping
        PolledTaskStatus.ACCEPTED -> Res.string.background_task_status_accepted
        PolledTaskStatus.CLAIMED -> Res.string.background_task_status_claimed
        PolledTaskStatus.NOT_RUNNING -> Res.string.background_task_status_not_running
        PolledTaskStatus.CONTROL_NOT_FOUND -> Res.string.background_task_status_control_not_found
        PolledTaskStatus.COMPLETED -> Res.string.wakeup_status_completed
        PolledTaskStatus.ERROR, PolledTaskStatus.FAILED -> Res.string.wakeup_status_failed
        PolledTaskStatus.CANCELLED -> Res.string.wakeup_status_cancelled
        PolledTaskStatus.DISPATCHED -> Res.string.background_task_status_dispatched
        PolledTaskStatus.INTERRUPTED -> Res.string.background_task_status_interrupted
    },
)

private const val SUBAGENT_TOOL = "subagent"
private const val TASK_TEXT_MAX_LINES = 40
