package com.garfiec.librechat.feature.chat.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.MessageRepository
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.response.SubagentAttachmentCallsEntry
import com.garfiec.librechat.core.model.response.SubagentAttachmentFile
import com.garfiec.librechat.feature.chat.util.subagentFilesInDoubt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Asks `GET …/subagent-calls` (v0.8.9-rc1, subagent file delivery slice 2) which child call each
 * unplaced file of a previewed subagent message was saved under, and publishes the answers by message
 * id for the display overlay (`withStoredSubagentHints`). Held in memory only (no Room write, so no
 * streaming-anchor question), keyed by the files asked, so a newer file asks again.
 *
 * Asked only for a saved message holding a previewed subagent call with files no call claims
 * ([subagentFilesInDoubt]): only an rc1+ server previews, so the route exists whenever this asks. A
 * failed lookup leaves slice 1's placement and is asked again when connectivity returns, at most
 * [MAX_ATTEMPTS] times per file set (web retries with backoff and on reconnect). A conversation or
 * account switch drops everything. Confined to the scope's Main thread.
 */
class SubagentHintsDelegate(
    private val scope: CoroutineScope,
    private val messageRepository: MessageRepository,
    private val currentAccountId: () -> String?,
    private val defaultDispatcher: CoroutineDispatcher,
) {
    private val _hints = MutableStateFlow<Map<String, List<SubagentAttachmentCallsEntry>>>(emptyMap())

    /** The server's answers by message id; empty until one lands. */
    val hints: StateFlow<Map<String, List<SubagentAttachmentCallsEntry>>> = _hints.asStateFlow()

    private data class Ask(val messageId: String, val files: List<SubagentAttachmentFile>)

    private val answered = mutableSetOf<Ask>()
    private val failed = mutableMapOf<Ask, Int>()
    private val inFlight = mutableMapOf<Ask, Job>()
    private var scopeId: Pair<String?, String?>? = null
    private var conversationId: String? = null
    private var latest: List<Message> = emptyList()

    fun start(messages: Flow<Pair<String?, List<Message>>>, connectivity: Flow<Boolean> = emptyFlow()) {
        scope.launch {
            messages.distinctUntilChanged { a, b -> a.first == b.first && a.second === b.second }.collect { (id, current) ->
                ensureScope(id)
                latest = current
                val wanted = withContext(defaultDispatcher) { asksFor(current) }
                wanted.forEach(::ask)
            }
        }
        scope.launch {
            var wasConnected: Boolean? = null
            connectivity.distinctUntilChanged().collect { connected ->
                if (connected && wasConnected == false) retryFailed()
                wasConnected = connected
            }
        }
    }

    private fun asksFor(messages: List<Message>): List<Ask> = messages.mapNotNull { message ->
        subagentFilesInDoubt(message).takeIf { it.isNotEmpty() }?.let { Ask(message.messageId, it) }
    }

    private fun ask(target: Ask) {
        if (target in answered || inFlight[target]?.isActive == true) return
        if ((failed[target] ?: 0) > 0) return // Waits for a reconnect.
        launchAsk(target)
    }

    private fun launchAsk(target: Ask) {
        val conversation = conversationId ?: return
        val requestScope = scopeId
        inFlight[target] = scope.launch {
            val result = messageRepository.getSubagentAttachmentCalls(conversation, target.messageId, target.files)
            inFlight.remove(target)
            if (scopeId != requestScope) return@launch
            when (result) {
                is Result.Success -> {
                    answered += target
                    failed.remove(target)
                    _hints.value = _hints.value + (target.messageId to result.data)
                }
                is Result.Error -> {
                    Logger.w(result.exception) { "Subagent file lookup failed for ${target.messageId}" }
                    failed[target] = (failed[target] ?: 0) + 1
                }
                is Result.Loading -> Unit
            }
        }
    }

    private fun retryFailed() {
        val current = asksFor(latest).toSet()
        failed.filter { (target, attempts) -> target in current && attempts < MAX_ATTEMPTS }.keys.forEach { target ->
            if (inFlight[target]?.isActive != true) launchAsk(target)
        }
    }

    private fun ensureScope(id: String?) {
        val next = id to currentAccountId()
        if (scopeId == next) return
        scopeId = next
        conversationId = id
        inFlight.values.forEach { it.cancel() }
        inFlight.clear()
        answered.clear()
        failed.clear()
        if (_hints.value.isNotEmpty()) _hints.value = emptyMap()
    }

    companion object {
        /** Lookups per file set (the first included) before only a reload asks again. */
        const val MAX_ATTEMPTS: Int = 3
    }
}
