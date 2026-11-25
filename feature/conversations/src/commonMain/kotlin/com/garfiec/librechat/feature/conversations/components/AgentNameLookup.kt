package com.garfiec.librechat.feature.conversations.components

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.model.Conversation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Agent chats' names, looked up once per agent. Not thread-safe: call [request] and [clear] from the
 * owning ViewModel's Main-dispatched flows only.
 */
internal class AgentNameLookup(
    private val agentRepository: AgentRepository,
    private val scope: CoroutineScope,
) {
    private val _names = MutableStateFlow<Map<String, String>>(emptyMap())
    val names: StateFlow<Map<String, String>> = _names.asStateFlow()

    private val requested = mutableSetOf<String>()

    fun request(conversations: List<Conversation>) {
        for (conversation in conversations) {
            val id = conversation.agentKey() ?: continue
            if (!requested.add(id)) continue
            scope.launch {
                val name = (agentRepository.getAgent(id) as? Result.Success)?.data?.name
                    ?.takeIf { it.isNotBlank() } ?: return@launch
                _names.update { it + (id to name) }
            }
        }
    }

    fun clear() {
        requested.clear()
        _names.value = emptyMap()
    }
}
