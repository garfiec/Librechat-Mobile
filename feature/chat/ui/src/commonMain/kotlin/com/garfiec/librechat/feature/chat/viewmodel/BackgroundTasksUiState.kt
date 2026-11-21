package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.background.BackgroundTaskDelivery
import com.garfiec.librechat.core.model.background.BackgroundTaskSummary

@Immutable
data class BackgroundTasksUiState(
    val conversationId: String? = null,
    val tasks: List<BackgroundTaskSummary> = emptyList(),
    /** The deployment lets users stop ordinary background tools; otherwise the cancel route 403s. */
    val cancellable: Boolean = false,
    /** The server's list may be missing finished results (durable store down or list truncated). */
    val incomplete: Boolean = false,
    val stoppingTaskIds: Set<String> = emptySet(),
    val stoppingAll: Boolean = false,
    val stopFailed: Boolean = false,
) {
    val runningCount: Int get() = tasks.count { it.isRunning }

    /** Finished results that will still arrive as a new agent turn. */
    val awaitingCount: Int
        get() = tasks.count { !it.isRunning && it.delivery == BackgroundTaskDelivery.PENDING }

    /** Automatic deliveries that failed; the result never arrives on its own. */
    val failedDeliveryCount: Int get() = tasks.count { it.delivery == BackgroundTaskDelivery.FAILED }

    /** Upstream renders nothing for a conversation with no tasks and a complete list. */
    val isVisible: Boolean get() = tasks.isNotEmpty() || incomplete

    fun canStop(task: BackgroundTaskSummary): Boolean =
        cancellable && task.isRunning && !task.cancellationRequested &&
            task.taskId !in stoppingTaskIds && !stoppingAll

    val canStopAll: Boolean get() = cancellable && !stoppingAll && tasks.count { canStop(it) } > 1
}
