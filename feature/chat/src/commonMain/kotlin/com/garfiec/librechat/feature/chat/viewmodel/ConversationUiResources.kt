package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.mcp.UiResource
import com.garfiec.librechat.core.model.mcp.collectConversationUiResources
import com.garfiec.librechat.feature.chat.util.MessageNode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * The UI resources a reply's `\ui{id}` markers can name (upstream `useConversationUIResources`): the
 * shown messages' attachments and the live reply's, by id. Rebuilt only when either list changes,
 * never per streamed delta, and off the main thread.
 */
internal fun conversationUiResources(
    state: Flow<ChatUiState>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): Flow<Map<String, UiResource>> = state
    .map { Sources(it.displayMessages, it.streamingAttachments) }
    .distinctUntilChanged { old, new ->
        old.messages === new.messages && old.live === new.live
    }
    .map { sources ->
        collectConversationUiResources(
            sources.messages.asSequence().flatMap { it.message.attachments.orEmpty() } + sources.live,
        )
    }
    .distinctUntilChanged()
    .flowOn(dispatcher)

private class Sources(val messages: List<MessageNode>, val live: List<Attachment>)
