package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.FileReference
import com.garfiec.librechat.core.model.PendingSteer
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.request.AddedConversation
import com.garfiec.librechat.core.model.request.ChatResumeRequest
import com.garfiec.librechat.core.model.request.EditedContent
import com.garfiec.librechat.core.model.request.EphemeralAgent
import com.garfiec.librechat.core.model.request.SteerCancelRequest
import com.garfiec.librechat.core.model.request.SteerRequest
import com.garfiec.librechat.core.model.response.ChatAbortResponse
import com.garfiec.librechat.core.model.response.ChatResumeResponse
import com.garfiec.librechat.core.model.response.ChatStatusResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.JsonObject

/**
 * Records what `startChat` was called with, by NAME.
 *
 * A hand-written fake rather than a mockk stub because `startChat` takes thirty-odd parameters:
 * `arg(19)` reads nothing and silently starts asserting about a different field the next time a
 * parameter is inserted above it. Here a renamed or reordered parameter is a compile error.
 */
class RecordingChatRepository : com.garfiec.librechat.core.data.repository.ChatRepository {

    data class Sent(
        val text: String,
        val parentMessageId: String?,
        val userMessageId: String?,
        val overrideParentMessageId: String?,
        val responseMessageId: String?,
        val isEdited: Boolean,
        val isRegenerate: Boolean,
        val editedContent: EditedContent?,
        val quotes: List<String>?,
        val manualSkills: List<String>?,
        val isContinued: Boolean = false,
        val compact: Boolean? = null,
        val addedConvo: AddedConversation? = null,
    )

    val sent = mutableListOf<Sent>()

    @Suppress("LongParameterList")
    override fun startChat(
        text: String,
        conversationId: String?,
        endpoint: String,
        endpointType: String?,
        key: String?,
        modelDisplayLabel: String?,
        model: String?,
        userMessageId: String?,
        parentMessageId: String?,
        agentId: String?,
        overrideParentMessageId: String?,
        responseMessageId: String?,
        isEdited: Boolean,
        isRegenerate: Boolean,
        compact: Boolean?,
        isContinued: Boolean,
        webSearch: Boolean,
        files: List<FileReference>?,
        addedConvo: AddedConversation?,
        ephemeralAgent: EphemeralAgent?,
        isTemporary: Boolean,
        modelParams: JsonObject?,
        quotes: List<String>?,
        spec: String?,
        chatProjectId: String?,
        manualSkills: List<String>?,
        editedContent: EditedContent?,
    ): Flow<StreamEvent> {
        sent += Sent(
            text = text,
            parentMessageId = parentMessageId,
            userMessageId = userMessageId,
            overrideParentMessageId = overrideParentMessageId,
            responseMessageId = responseMessageId,
            isEdited = isEdited,
            isRegenerate = isRegenerate,
            editedContent = editedContent,
            quotes = quotes,
            manualSkills = manualSkills,
            isContinued = isContinued,
            compact = compact,
            addedConvo = addedConvo,
        )
        return emptyFlow()
    }

    override suspend fun abortChat(
        streamId: String?,
        isTemporary: Boolean,
        claimSteers: (List<PendingSteer>) -> Unit,
    ): Result<ChatAbortResponse> = unused()

    override suspend fun resumeChat(request: ChatResumeRequest): Result<ChatResumeResponse> = unused()

    override suspend fun steerChat(request: SteerRequest) = unused<Nothing>()

    override suspend fun cancelSteer(request: SteerCancelRequest) = unused<Nothing>()

    override suspend fun checkStreamStatus(
        conversationId: String,
        claimSteers: (List<PendingSteer>) -> Unit,
    ): ChatStatusResponse = unused()

    override fun resumeStream(conversationId: String): Flow<StreamEvent> = unused()

    override suspend fun getActiveJobIds(): Result<Set<String>> = unused()

    private fun <T> unused(): T = throw UnsupportedOperationException("not used by the editing tests")
}
