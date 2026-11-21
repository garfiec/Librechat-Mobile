package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.BackgroundTaskRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The chat header's Background Tasks chip and sheet (v0.8.8, upstream
 * `client/src/components/Chat/BackgroundTasks/`), for ordinary background tool tasks.
 *
 * **Polls only while something is running or the sheet is open.** Otherwise the list is read once
 * when a conversation is bound and once each time a run settles — the moment a tool can have been
 * sent to the background. Every tick re-asks [BackgroundTaskRepository.isRuledOutForServer], since
 * the answer flips when the first probe 404s.
 */
class BackgroundTasksViewModel(
    private val repository: BackgroundTaskRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackgroundTasksUiState())
    val uiState: StateFlow<BackgroundTasksUiState> = _uiState.asStateFlow()

    private var conversationId: String? = null
    private var isStreaming = false
    private var sheetOpen = false
    private var active = false
    private var pollJob: Job? = null

    /** The screen's current conversation and run state. A settle (true → false) re-reads the list. */
    fun bind(conversationId: String?, isStreaming: Boolean) {
        val conversationChanged = conversationId != this.conversationId
        val settled = this.isStreaming && !isStreaming
        this.conversationId = conversationId
        this.isStreaming = isStreaming
        if (conversationChanged) {
            _uiState.value = BackgroundTasksUiState(conversationId = conversationId)
        }
        if (conversationChanged || settled) restart()
    }

    /** Lifecycle: stop reading while the screen is not started. */
    fun setActive(active: Boolean) {
        if (this.active == active) return
        this.active = active
        if (active) restart() else stopPolling()
    }

    fun setSheetOpen(open: Boolean) {
        if (sheetOpen == open) return
        sheetOpen = open
        if (open) {
            _uiState.update { it.copy(stopFailed = false) }
            restart()
        }
    }

    fun stop(taskId: String) = cancel(listOf(taskId))

    fun stopAll() = cancel(null)

    private fun cancel(taskIds: List<String>?) {
        val id = conversationId ?: return
        _uiState.update {
            it.copy(
                stoppingTaskIds = it.stoppingTaskIds + taskIds.orEmpty(),
                stoppingAll = it.stoppingAll || taskIds == null,
                stopFailed = false,
            )
        }
        viewModelScope.launch {
            val result = repository.cancel(id, taskIds)
            if (conversationId != id) return@launch
            val failed = when (result) {
                is Result.Success -> result.data.results.any { !it.isResolved }
                else -> true
            }
            // A 403 means the deployment turned cancellation off since the list was read.
            val forbidden = ((result as? Result.Error)?.exception as? ApiException)?.statusCode == HTTP_FORBIDDEN
            _uiState.update {
                it.copy(
                    stoppingTaskIds = it.stoppingTaskIds - taskIds.orEmpty().toSet(),
                    stoppingAll = if (taskIds == null) false else it.stoppingAll,
                    stopFailed = failed,
                    cancellable = it.cancellable && !forbidden,
                )
            }
            restart()
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun restart() {
        stopPolling()
        val id = conversationId ?: return
        if (!active) return
        pollJob = viewModelScope.launch {
            while (true) {
                if (repository.isRuledOutForServer()) {
                    _uiState.value = BackgroundTasksUiState(conversationId = id)
                    return@launch
                }
                load(id)
                val interval = when {
                    _uiState.value.runningCount > 0 -> RUNNING_REFRESH_MS
                    sheetOpen -> SHEET_REFRESH_MS
                    else -> return@launch
                }
                delay(interval)
            }
        }
    }

    private suspend fun load(id: String) {
        val result = repository.getTasks(id)
        if (conversationId != id) return
        when (result) {
            is Result.Success -> {
                val index = result.data
                _uiState.update {
                    if (index == null) {
                        BackgroundTasksUiState(conversationId = id)
                    } else {
                        it.copy(
                            tasks = index.tasks,
                            cancellable = index.cancellable,
                            incomplete = index.complete == false,
                        )
                    }
                }
            }
            // A transient failure keeps the last list; the next tick or settle asks again.
            else -> Unit
        }
    }

    private companion object {
        /** Upstream `RUNNING_REFRESH_MS`. */
        const val RUNNING_REFRESH_MS = 2_000L

        /** Upstream `AWAITING_DELIVERY_REFRESH_MS`: finished results change slowly. */
        const val SHEET_REFRESH_MS = 10_000L
        const val HTTP_FORBIDDEN = 403
    }
}
