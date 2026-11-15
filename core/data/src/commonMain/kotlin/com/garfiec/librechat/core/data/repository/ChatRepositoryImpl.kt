package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.common.network.ConnectivityObserver
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.FailureKind
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.failureKind
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.FileReference
import com.garfiec.librechat.core.model.PendingSteer
import com.garfiec.librechat.core.model.ReasoningOverride
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.code.CodeWorkspaceSubmission
import com.garfiec.librechat.core.model.error.ServerErrorCode
import com.garfiec.librechat.core.model.request.AddedConversation
import com.garfiec.librechat.core.model.request.ChatResumeRequest
import com.garfiec.librechat.core.model.request.EditedContent
import com.garfiec.librechat.core.model.request.EphemeralAgent
import com.garfiec.librechat.core.model.request.SteerArmRequest
import com.garfiec.librechat.core.model.request.SteerCancelRequest
import com.garfiec.librechat.core.model.request.SteerRequest
import com.garfiec.librechat.core.model.response.ChatAbortResponse
import com.garfiec.librechat.core.model.response.ChatResumeResponse
import com.garfiec.librechat.core.model.response.ChatStatusResponse
import com.garfiec.librechat.core.model.response.SteerArmResponse
import com.garfiec.librechat.core.model.response.SteerCancelResponse
import com.garfiec.librechat.core.model.response.SteerResponse
import com.garfiec.librechat.core.network.api.ChatApi
import com.garfiec.librechat.core.network.sse.SseClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class ChatRepositoryImpl(
    private val chatApi: ChatApi,
    private val sseClient: SseClient,
    private val connectivityObserver: ConnectivityObserver,
    private val dispatcher: CoroutineDispatcher,
    private val json: Json,
    private val projectChanges: ProjectChangeSignal,
    private val activeAccountProvider: ActiveAccountProvider,
) : ChatRepository {

    // New chats sent with a chatProjectId whose first reply hasn't finished. The server saves the
    // conversation, filed, only then; the reply often finishes on a resumed stream, after the screen
    // that started it is gone, so both stream paths check here.
    private val filedChatsPending = MutableStateFlow<Set<String>>(emptySet())

    private fun Flow<StreamEvent>.announcingFiledChat(conversationId: String): Flow<StreamEvent> = onEach { event ->
        if (event is StreamEvent.Final && conversationId in filedChatsPending.value) {
            filedChatsPending.update { it - conversationId }
            projectChanges.bump()
        }
    }

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
        reasoningOverride: ReasoningOverride?,
        codeWorkspace: CodeWorkspaceSubmission?,
    ): Flow<StreamEvent> = flow {
        // Phase 1: POST to start the chat - get back a streamId (= conversationId)
        val request = ChatPayloadBuilder.build(
            text = text,
            conversationId = conversationId,
            endpoint = endpoint,
            endpointType = endpointType,
            key = key,
            modelDisplayLabel = modelDisplayLabel,
            model = model,
            spec = spec,
            userMessageId = userMessageId,
            parentMessageId = parentMessageId,
            agentId = agentId,
            overrideParentMessageId = overrideParentMessageId,
            responseMessageId = responseMessageId,
            isEdited = isEdited,
            isRegenerate = isRegenerate,
            compact = compact,
            isContinued = isContinued,
            webSearch = webSearch,
            files = files,
            addedConvo = addedConvo,
            ephemeralAgent = ephemeralAgent,
            isTemporary = isTemporary,
            quotes = quotes,
            chatProjectId = chatProjectId,
            manualSkills = manualSkills,
            editedContent = editedContent,
            reasoningOverride = reasoningOverride,
            codeWorkspace = codeWorkspace,
        )
        // Every retry re-sends this one body: the server deduplicates a start by its
        // `clientRequestId`, so a re-sent start resumes the run the first one began instead of
        // starting a second. Rebuilding the request per attempt would mint a new id.
        val body = ChatPayloadBuilder.toBody(json, request, modelParams)
        val startResponse = retryStart { chatApi.startChat(endpoint, body) }
        val streamId = startResponse.conversationId
        if (chatProjectId != null) filedChatsPending.update { it + streamId }

        // Emit a Created event from the POST response so the ViewModel
        // knows the conversationId before any SSE events arrive.
        emit(StreamEvent.Created(
            conversationId = streamId,
            messageId = "",
            parentMessageId = "",
            generationCreatedAt = startResponse.generationCreatedAt,
        ))

        // Phase 2: GET the SSE stream using the streamId
        val streamUrl = "api/agents/chat/stream/$streamId"
        emitAll(
            // A deduplicated start answers `resumed`: the run already streamed, so replay it.
            sseClient.connect(
                streamUrl,
                resume = startResponse.status == START_STATUS_RESUMED,
                connectivityFlow = connectivityObserver.isConnected,
            ).announcingFiledChat(streamId),
        )
    }.flowOn(dispatcher)

    /**
     * Runs the start POST [block] again on the failures that leave the send's fate unknown or
     * merely early, so the same body reaches the server once it can answer:
     * - an uncoded 409: the parent response is still being saved and clears in milliseconds.
     *   Only the uncoded one: every other 409 on this route is coded and needs the user or is a
     *   deduplication, so retrying it would loop or hide it. Read via [generationErrorCode], not
     *   `ServerErrorCode.from`, whose `error` fallback hands this body's English sentence back as
     *   a code;
     * - 503 `SERVER_NOT_READY`: retried for up to [START_READINESS_WINDOW_MS], each wait the
     *   server's `Retry-After` capped at [MAX_START_READINESS_DELAY_MS];
     * - a dropped connection or a timeout (the response may have been lost after the server
     *   started the run): [START_NETWORK_RETRIES] more attempts.
     *
     * Never retries once the active account changed: the client resolves the server and token per
     * request, so the retry would send this account's message to the other account's server.
     */
    @Suppress("TooGenericExceptionCaught") // Transport failures arrive as many unrelated types.
    private suspend fun <T> retryStart(block: suspend () -> T): T {
        val origin = activeAccountProvider.currentAccountId()
        var conflictRetries = 0
        var networkRetries = 0
        var readinessWaited = 0L
        while (true) {
            val (failure, wait) = try {
                return block()
            } catch (e: ApiException) {
                e to when {
                    e.statusCode == HTTP_CONFLICT && e.generationErrorCode == null ->
                        retryDelayMillis(e).takeIf { conflictRetries++ < MAX_TRANSIENT_RETRIES }

                    e.statusCode == HTTP_SERVICE_UNAVAILABLE &&
                        e.generationErrorCode == ServerErrorCode.SERVER_NOT_READY ->
                        (START_READINESS_WINDOW_MS - readinessWaited).takeIf { it > 0 }
                            ?.let { left -> minOf(startReadinessDelayMillis(e), left) }
                            ?.also { readinessWaited += it }

                    else -> null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val retryable = e.failureKind() in RETRYABLE_START_FAILURES && networkRetries < START_NETWORK_RETRIES
                e to if (retryable) START_NETWORK_BACKOFF_MS * ++networkRetries else null
            }
            delay(wait ?: throw failure)
            if (activeAccountProvider.currentAccountId() != origin) throw failure
        }
    }

    private fun startReadinessDelayMillis(exception: ApiException): Long =
        (exception.retryAfterSeconds?.times(1000) ?: DEFAULT_START_READINESS_DELAY_MS)
            .coerceIn(DEFAULT_RETRY_DELAY_MS, MAX_START_READINESS_DELAY_MS)

    override suspend fun abortChat(
        streamId: String?,
        isTemporary: Boolean,
        claimSteers: (List<PendingSteer>) -> Unit,
    ): Result<ChatAbortResponse> =
        when (
            val result = retryWhile(
                // The run acknowledged the stop but has not reached a point where it can be
                // stopped, or it stopped and saving the partial failed (the run is kept for a
                // retry). The server asks to be asked again (`Retry-After: 1`) for both, so
                // reporting a stop failure here would be a lie the user has to act on.
                retryable = {
                    it.statusCode == HTTP_CONFLICT &&
                        it.generationErrorCode in RETRYABLE_ABORT_CODES
                },
            ) { safeApiCall { chatApi.abortChat(streamId, isTemporary) } }
        ) {
            // Claimed here, not at the call site: the server dropped its copy writing this ack.
            is Result.Success -> {
                claimSteers(result.data.pendingSteers)
                Result.Success(result.data.copy(pendingSteers = emptyList()))
            }

            else -> result
        }

    override suspend fun resumeChat(request: ChatResumeRequest): Result<ChatResumeResponse> = safeApiCall {
        chatApi.resumeChat(request)
    }

    override suspend fun steerChat(request: SteerRequest): Result<SteerResponse> = safeApiCall {
        chatApi.steerChat(request)
    }

    override suspend fun cancelSteer(request: SteerCancelRequest): Result<SteerCancelResponse> = safeApiCall {
        chatApi.cancelSteer(request)
    }

    override suspend fun armSteer(request: SteerArmRequest): Result<SteerArmResponse> = safeApiCall {
        chatApi.armSteer(request)
    }

    override suspend fun checkStreamStatus(
        conversationId: String,
        claimSteers: (List<PendingSteer>) -> Unit,
    ): ChatStatusResponse {
        // Throws rather than returning a Result, so it never gets safeApiCall's dispatcher hop;
        // take it explicitly (#326).
        //
        // 503 SERVER_NOT_READY is retried here rather than at the three call sites, because all
        // three read this answer as "the run is gone" and act on it — one ends the stream as
        // ResumeFailed, one silently declines to resume, one abandons a network recovery. The
        // status route answers it while the generation epoch is still settling or while the
        // terminal owner is mid-persist, both of which resolve within about a second, so letting
        // it escape means giving up on a run that is still alive.
        val status = withContext(dispatcher) {
            retryWhileThrowing(
                retryable = {
                    it.statusCode == HTTP_SERVICE_UNAVAILABLE &&
                        it.generationErrorCode == ServerErrorCode.SERVER_NOT_READY
                },
            ) { chatApi.getChatStatus(conversationId) }
        }
        // Before returning, so no staleness guard at the call site can sit between the read and
        // the claim. The server already deleted its copy answering this request.
        claimSteers(status.unrecoveredSteers)
        return status.copy(unrecoveredSteers = emptyList())
    }

    override suspend fun getActiveJobIds(): Result<Set<String>> = safeApiCall {
        chatApi.getActiveJobs().activeJobIds.toSet()
    }

    override fun resumeStream(conversationId: String): Flow<StreamEvent> = flow {
        val streamUrl = "api/agents/chat/stream/$conversationId"
        emitAll(
            sseClient.connect(streamUrl, resume = true, connectivityFlow = connectivityObserver.isConnected)
                .announcingFiledChat(conversationId),
        )
    }.flowOn(dispatcher)

    /**
     * Runs [block] again while it fails with a transient, server-nominated retryable error.
     *
     * The generation routes gained several outcomes that are explicitly "ask me again in a
     * moment" — 409 `RUN_STILL_ACTIVE` on abort, 503 `SERVER_NOT_READY` on status — each carrying
     * `Retry-After: 1`. They are races inside the server's own generation fencing, not failures,
     * and treating them as failures abandons a run that is still alive.
     *
     * Bounded at [MAX_TRANSIENT_RETRIES] extra attempts, and the wait honours the server's
     * `Retry-After` when it sent one, clamped so a hostile or confused value cannot park the
     * caller. Exhausting the budget returns the last error, so every existing failure path stays
     * reachable — this only changes how quickly it is reached.
     */
    private suspend fun <T> retryWhile(
        retryable: (ApiException) -> Boolean,
        block: suspend () -> Result<T>,
    ): Result<T> {
        var attempt = 0
        while (true) {
            val result = block()
            val exception = (result as? Result.Error)?.exception as? ApiException
            if (exception == null || !retryable(exception) || attempt >= MAX_TRANSIENT_RETRIES) {
                return result
            }
            attempt++
            delay(retryDelayMillis(exception))
        }
    }

    /** [retryWhile] for the one call that reports failure by throwing rather than by `Result`. */
    private suspend fun <T> retryWhileThrowing(
        retryable: (ApiException) -> Boolean,
        block: suspend () -> T,
    ): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: ApiException) {
                if (!retryable(e) || attempt >= MAX_TRANSIENT_RETRIES) throw e
                attempt++
                delay(retryDelayMillis(e))
            }
        }
    }

    private fun retryDelayMillis(exception: ApiException): Long =
        (exception.retryAfterSeconds?.times(1000) ?: DEFAULT_RETRY_DELAY_MS)
            .coerceIn(DEFAULT_RETRY_DELAY_MS, MAX_RETRY_DELAY_MS)

    /**
     * The code on a generation-route error body. Every predicate here reads it through this one
     * accessor, so none of them can pick up `ServerErrorCode.from`'s `error` fallback — on these
     * routes an uncoded body is a distinct outcome, not a body whose code lives elsewhere.
     */
    private val ApiException.generationErrorCode: String? get() = ServerErrorCode.generationCodeOf(body)

    private companion object {
        val RETRYABLE_ABORT_CODES = setOf(
            ServerErrorCode.RUN_STILL_ACTIVE,
            ServerErrorCode.ABORT_PERSISTENCE_FAILED,
        )
        const val HTTP_CONFLICT = 409
        const val HTTP_SERVICE_UNAVAILABLE = 503
        const val START_STATUS_RESUMED = "resumed"

        /**
         * Extra attempts after the first. Three covers the status route's own three-read epoch
         * verification with room to spare; more would keep a user staring at a spinner for a
         * server that is not going to settle.
         */
        const val MAX_TRANSIENT_RETRIES = 3
        const val DEFAULT_RETRY_DELAY_MS = 500L
        const val MAX_RETRY_DELAY_MS = 2_000L

        /** The total of the Retry-After waits a send spends on a starting server; request time is not counted. */
        const val START_READINESS_WINDOW_MS = 120_000L
        const val DEFAULT_START_READINESS_DELAY_MS = 1_000L
        const val MAX_START_READINESS_DELAY_MS = 30_000L

        /** A start whose response was lost: 3 attempts in all, 1 s then 2 s apart, the web client's network-retry schedule. */
        val RETRYABLE_START_FAILURES = setOf(FailureKind.Network, FailureKind.Timeout)
        const val START_NETWORK_RETRIES = 2
        const val START_NETWORK_BACKOFF_MS = 1_000L
    }
}
