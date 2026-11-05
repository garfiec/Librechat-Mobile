package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.code.CodeEnvironmentMode
import com.garfiec.librechat.core.model.code.CodeEnvironmentMoveRequest
import com.garfiec.librechat.core.model.code.CodeEnvironmentStatus
import com.garfiec.librechat.core.model.code.CodeWorkspaceSelection
import com.garfiec.librechat.core.model.code.CodeWorkspaceUnavailableReason
import com.garfiec.librechat.core.model.error.StreamErrorType
import com.garfiec.librechat.core.network.api.CodeEnvironmentsApi
import com.garfiec.librechat.core.network.api.ConversationsApi

/** A saved chat's recorded code-workspace decision; both null when it has not decided. */
data class SavedCodeDecision(
    val mode: String?,
    val workspaces: List<CodeWorkspaceSelection>?,
    /** The approval mode the chat last ran under, when the server stored one. */
    val approvalMode: String? = null,
) {
    val holdsDecision: Boolean get() = mode != null || !workspaces.isNullOrEmpty()
}

/** How a `PATCH …/decision` ended. */
sealed interface CodeDecisionMove {
    /** The server now holds [decision]. */
    data class Moved(val decision: SavedCodeDecision) : CodeDecisionMove

    /**
     * Refused with a `code_workspace_unavailable` [reason]. [CodeWorkspaceUnavailableReason.LOCKED]
     * means the stored decision changed since it was read: what the server holds is unknown.
     */
    data class Refused(val reason: CodeWorkspaceUnavailableReason) : CodeDecisionMove

    /** 409 with no `code`: a response is still running, awaiting approval or saving. */
    data object Busy : CodeDecisionMove

    /** 403: moves, or attaching/detaching, are switched off for this user. */
    data object Disabled : CodeDecisionMove

    /** 404: the chat is gone. */
    data object NotFound : CodeDecisionMove

    /**
     * Anything else: a 5xx, a lost response, a 4xx this client does not know. [unknownOutcome]
     * when the server may have applied it (no status, or 5xx), so the decision must be re-read.
     */
    data class Failed(val unknownOutcome: Boolean) : CodeDecisionMove
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

    /** Replaces [conversationId]'s recorded decision [from] with [to] (empty = continue without one). */
    suspend fun moveDecision(
        conversationId: String,
        from: List<CodeWorkspaceSelection>,
        to: List<CodeWorkspaceSelection>,
    ): CodeDecisionMove
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
                approvalMode = conversation.codeApprovalMode,
            )
        }

    override suspend fun moveDecision(
        conversationId: String,
        from: List<CodeWorkspaceSelection>,
        to: List<CodeWorkspaceSelection>,
    ): CodeDecisionMove {
        val result = safeApiCall { api.moveDecision(conversationId, CodeEnvironmentMoveRequest(from, to)) }
        return when (result) {
            is Result.Success -> CodeDecisionMove.Moved(
                SavedCodeDecision(
                    mode = CodeEnvironmentMode.of(result.data.codeEnvironmentMode),
                    workspaces = result.data.codeWorkspaces,
                ),
            )
            is Result.Error -> moveFailure(result.exception as? ApiException)
            Result.Loading -> CodeDecisionMove.Failed(unknownOutcome = true)
        }
    }

    private fun moveFailure(error: ApiException?): CodeDecisionMove {
        val status = error?.statusCode ?: return CodeDecisionMove.Failed(unknownOutcome = true)
        val reason = error.body?.let(StreamErrorType::codeWorkspaceReason)
        return when {
            reason != null -> CodeDecisionMove.Refused(reason)
            status == HTTP_CONFLICT -> CodeDecisionMove.Busy
            status == HTTP_FORBIDDEN -> CodeDecisionMove.Disabled
            status == HTTP_NOT_FOUND -> CodeDecisionMove.NotFound
            else -> CodeDecisionMove.Failed(unknownOutcome = status >= HTTP_SERVER_ERROR)
        }
    }

    private companion object {
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_CONFLICT = 409
        const val HTTP_SERVER_ERROR = 500
    }
}
