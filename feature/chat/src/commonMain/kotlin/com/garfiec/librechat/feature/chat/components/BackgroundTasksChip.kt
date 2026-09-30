package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.background.BackgroundTaskDelivery
import com.garfiec.librechat.core.model.background.BackgroundTaskStatus
import com.garfiec.librechat.core.model.background.BackgroundTaskSummary
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.viewmodel.BackgroundTasksUiState
import com.garfiec.librechat.feature.chat.viewmodel.BackgroundTasksViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The chat header's Background Tasks chip (v0.8.8). Renders nothing until the conversation has
 * tasks, so it costs one read per conversation open and per settled run — see
 * [BackgroundTasksViewModel] for when it polls.
 */
@Composable
internal fun BackgroundTasksChip(
    conversationId: String?,
    isStreaming: Boolean,
    viewModel: BackgroundTasksViewModel = koinViewModel(),
) {
    LaunchedEffect(conversationId, isStreaming) { viewModel.bind(conversationId, isStreaming) }
    LifecycleStartEffect(viewModel) {
        viewModel.setActive(true)
        onStopOrDispose { viewModel.setActive(false) }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sheetOpen) { viewModel.setSheetOpen(sheetOpen) }

    val visible = conversationId != null && state.conversationId == conversationId && state.isVisible
    // One emitting root; the sheet is a window of its own and takes no space here.
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (visible) {
        val chipCd = stringResource(Res.string.background_tasks_cd_chip, state.runningCount)
        FloatingBarChip(
            modifier = Modifier.height(FloatingBarChipSize).semantics { contentDescription = chipCd },
            onClick = { sheetOpen = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.HourglassTop,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (state.failedDeliveryCount > 0 || state.incomplete) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (state.runningCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(state.runningCount.toString(), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        }
        if (sheetOpen && visible) {
            BackgroundTasksSheet(
                state = state,
                onStop = viewModel::stop,
                onStopAll = viewModel::stopAll,
                onDismiss = { sheetOpen = false },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackgroundTasksSheet(
    state: BackgroundTasksUiState,
    onStop: (String) -> Unit,
    onStopAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { LowProfileDragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.background_tasks_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.canStopAll) {
                    TextButton(onClick = onStopAll) { Text(stringResource(Res.string.background_tasks_stop_all)) }
                }
            }
            if (state.incomplete) {
                Text(
                    stringResource(Res.string.background_tasks_incomplete),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (state.stopFailed) {
                Text(
                    stringResource(Res.string.background_tasks_stop_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (state.tasks.isEmpty()) {
                    Text(
                        stringResource(Res.string.background_tasks_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.tasks.forEachIndexed { index, task ->
                    if (index > 0) HorizontalDivider()
                    BackgroundTaskRow(
                        task = task,
                        stopping = task.cancellationRequested || task.taskId in state.stoppingTaskIds ||
                            (state.stoppingAll && task.isRunning),
                        canStop = state.canStop(task),
                        onStop = { onStop(task.taskId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BackgroundTaskRow(
    task: BackgroundTaskSummary,
    stopping: Boolean,
    canStop: Boolean,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                task.toolName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val failed = task.status == BackgroundTaskStatus.ERROR
            Text(
                summaryStatusLabel(task, stopping),
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            deliveryLabel(task.delivery, task.isRunning)?.let { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.delivery == BackgroundTaskDelivery.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        if (canStop) {
            TextButton(onClick = onStop) { Text(stringResource(Res.string.background_tasks_stop)) }
        }
    }
}

@Composable
private fun summaryStatusLabel(task: BackgroundTaskSummary, stopping: Boolean): String = when {
    task.isRunning && stopping -> stringResource(Res.string.background_task_status_stopping)
    task.status == BackgroundTaskStatus.RUNNING -> stringResource(Res.string.background_task_status_running)
    task.status == BackgroundTaskStatus.COMPLETED -> stringResource(Res.string.wakeup_status_completed)
    task.status == BackgroundTaskStatus.ERROR -> stringResource(Res.string.wakeup_status_failed)
    task.status == BackgroundTaskStatus.CANCELLED -> stringResource(Res.string.wakeup_status_cancelled)
    else -> task.status
}

/** Upstream shows delivery only when it needs the reader: a result still on its way, or one that never will be. */
@Composable
internal fun deliveryLabel(delivery: String?, running: Boolean): String? = when {
    delivery == BackgroundTaskDelivery.PENDING && !running -> stringResource(Res.string.background_task_result_pending)
    delivery == BackgroundTaskDelivery.FAILED -> stringResource(Res.string.background_task_result_undelivered)
    else -> null
}
