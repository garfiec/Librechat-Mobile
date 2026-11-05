package com.garfiec.librechat.feature.chat.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.CodeRunRepository
import com.garfiec.librechat.core.model.code.CodeRunRequest
import com.garfiec.librechat.feature.chat.components.CodeRunStatus
import com.garfiec.librechat.feature.chat.components.CodeRunTarget
import com.garfiec.librechat.feature.chat.components.CodeRunsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Runs a reply's fenced code blocks and keeps their outputs (web `RunCode.tsx`, `CodeBlock.tsx`,
 * `useToolCallsMap`). Its own flow, off `ChatUiState`: only the code blocks read it, and a run must
 * not recompose the thread.
 *
 * The stored runs are read once per conversation, and only when a runnable block is shown
 * ([ensureLoaded]); web reads them on every conversation open. A run appends its result to its
 * block's list, as web's mutation does to its cache. Main-confined, like the scope it runs on.
 */
class CodeRunDelegate(
    private val scope: CoroutineScope,
    private val repository: CodeRunRepository,
    private val conversationId: () -> String?,
    private val isTemporary: () -> Boolean,
    /** A run the server refused or that failed; the chat says so once. */
    private val onRunFailed: () -> Unit,
) {
    private val _state = MutableStateFlow(CodeRunsState())
    val state: StateFlow<CodeRunsState> = _state.asStateFlow()

    private var stateFor: String? = null
    private var loadedFor: String? = null
    private var loadJob: Job? = null
    private val resetJobs = HashMap<String, Job>()

    /** The chat moved to another conversation: its runs are not this one's. */
    fun onConversationChanged(id: String?) {
        if (id == stateFor) return
        stateFor = id
        loadJob?.cancel()
        loadedFor = null
        resetJobs.values.forEach(Job::cancel)
        resetJobs.clear()
        _state.value = CodeRunsState()
    }

    /** A runnable block is on screen: read this conversation's stored runs, once. */
    fun ensureLoaded() {
        val id = conversationId() ?: return
        onConversationChanged(id)
        if (loadedFor == id) return
        loadedFor = id
        loadJob = scope.launch {
            when (val result = repository.runs(id)) {
                is Result.Success -> if (stateFor == id) {
                    // A run made while the read was out stays where the read doesn't know the block.
                    _state.update { it.copy(results = result.data + it.results.filterKeys { key -> key !in result.data }) }
                }
                is Result.Error -> {
                    Logger.d { "CodeRun: stored runs of $id failed: ${result.message}" }
                    // Asked again the next time a block shows.
                    if (loadedFor == id) loadedFor = null
                }
                else -> Unit
            }
        }
    }

    /** Runs [target]; a block already running ignores the tap (web disables it and debounces). */
    fun run(target: CodeRunTarget) {
        val id = conversationId() ?: return
        onConversationChanged(id)
        val key = target.key
        if (_state.value.status[key] == CodeRunStatus.RUNNING) return
        resetJobs.remove(key)?.cancel()
        setStatus(key, CodeRunStatus.RUNNING)
        scope.launch {
            val result = repository.run(
                CodeRunRequest(
                    messageId = target.messageId,
                    conversationId = id,
                    blockIndex = target.blockIndex,
                    lang = target.lang,
                    code = target.code,
                    partIndex = target.partIndex,
                    isTemporary = isTemporary(),
                ),
            )
            if (stateFor != id) return@launch
            val outcome = if (result is Result.Success) {
                _state.update { it.copy(results = it.results + (key to it.results[key].orEmpty() + result.data)) }
                CodeRunStatus.COMPLETE
            } else {
                onRunFailed()
                CodeRunStatus.FAILED
            }
            setStatus(key, outcome)
            resetJobs[key] = scope.launch {
                delay(if (outcome == CodeRunStatus.FAILED) FAILED_SHOWN_MS else COMPLETE_SHOWN_MS)
                setStatus(key, null)
                resetJobs.remove(key)
            }
        }
    }

    private fun setStatus(key: String, status: CodeRunStatus?) {
        _state.update {
            it.copy(status = if (status == null) it.status - key else it.status + (key to status))
        }
    }

    private companion object {
        /** Web's 1.5 s on Complete and 2 s on Failed before the button reads Run again. */
        const val COMPLETE_SHOWN_MS = 1_500L
        const val FAILED_SHOWN_MS = 2_000L
    }
}
