package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.code.CodeEnvironmentMode
import com.garfiec.librechat.core.model.code.CodeEnvironmentStatus
import com.garfiec.librechat.core.model.code.CodeWorkspaceSelection
import com.garfiec.librechat.core.network.api.CodeEnvironmentsApi
import com.garfiec.librechat.core.network.api.ConversationsApi

/** A saved chat's recorded code-workspace decision; both null when it has not decided. */
data class SavedCodeDecision(
    val mode: String?,
    val workspaces: List<CodeWorkspaceSelection>?,
) {
    val holdsDecision: Boolean get() = mode != null || !workspaces.isNullOrEmpty()
}

/** Live status of the attached code machines a chat's agents run on. Network-direct, no cache. */
interface CodeEnvironmentRepository {
    /** The machine's state and registered workspaces. A failure's status code is on the exception. */
    suspend fun status(environmentId: String): Result<CodeEnvironmentStatus>

    /**
     * The decision [conversationId] holds, read off the single-conversation GET. Deliberately not
     * the Room row: the cache does not keep it, and a decision inferred from anything local could
     * disagree with the server's, which then refuses the turn as `locked`. Writes nothing.
     */
    suspend fun savedDecision(conversationId: String): Result<SavedCodeDecision>
}

class CodeEnvironmentRepositoryImpl(
    private val api: CodeEnvironmentsApi,
    private val conversationsApi: ConversationsApi,
) : CodeEnvironmentRepository {
    override suspend fun status(environmentId: String): Result<CodeEnvironmentStatus> =
        safeApiCall { api.status(environmentId) }

    override suspend fun savedDecision(conversationId: String): Result<SavedCodeDecision> =
        safeApiCall {
            val conversation = conversationsApi.getConversation(conversationId)
            SavedCodeDecision(
                mode = CodeEnvironmentMode.of(conversation.codeEnvironmentMode),
                workspaces = conversation.codeWorkspaces,
            )
        }
}
