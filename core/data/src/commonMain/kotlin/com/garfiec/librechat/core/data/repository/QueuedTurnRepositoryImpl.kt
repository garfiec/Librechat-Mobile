package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.onApiDispatcher
import com.garfiec.librechat.core.model.error.ServerErrorCode
import com.garfiec.librechat.core.model.queuedturn.AgentQueuedTurnReceipt
import com.garfiec.librechat.core.model.queuedturn.EnqueueQueuedTurnRequest
import com.garfiec.librechat.core.model.queuedturn.QueuedTurnOutcome
import com.garfiec.librechat.core.model.queuedturn.isDefiniteQueuedTurnRejection
import com.garfiec.librechat.core.model.queuedturn.isDefiniteQueuedTurnsUnsupported
import com.garfiec.librechat.core.network.api.QueuedTurnsApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class QueuedTurnRepositoryImpl(
    private val queuedTurnsApi: QueuedTurnsApi,
    private val activeAccountProvider: ActiveAccountProvider,
) : QueuedTurnRepository {

    private val latchMutex = Mutex()
    private var unsupportedFor: String? = null
    private var unsupportedLatched = false

    override suspend fun enqueue(
        request: EnqueueQueuedTurnRequest,
    ): QueuedTurnOutcome<AgentQueuedTurnReceipt> = call {
        val response = queuedTurnsApi.enqueueQueuedTurn(request)
        // A 2xx with `supported:false` is the announced capability saying no. It reads as an
        // ordinary success, so without this the caller would treat an absent receipt as a
        // committed row and refuse to drain a turn nothing will ever run.
        if (!response.capability.supported) return@call QueuedTurnOutcome.Unsupported
        val receipt = response.receipt ?: return@call QueuedTurnOutcome.Indeterminate(
            statusCode = null,
            code = null,
            message = "Enqueue accepted without a receipt",
        )
        QueuedTurnOutcome.Committed(receipt)
    }

    override suspend fun list(
        conversationId: String,
        clientRequestIds: List<String>,
    ): QueuedTurnOutcome<List<AgentQueuedTurnReceipt>> = call {
        val response = queuedTurnsApi.listQueuedTurns(conversationId, clientRequestIds)
        if (!response.capability.supported) {
            QueuedTurnOutcome.Unsupported
        } else {
            QueuedTurnOutcome.Committed(response.queuedTurns)
        }
    }

    override suspend fun cancel(queuedTurnId: String): QueuedTurnOutcome<AgentQueuedTurnReceipt> =
        call {
            val receipt = queuedTurnsApi.cancelQueuedTurn(queuedTurnId).receipt
                ?: return@call QueuedTurnOutcome.Indeterminate(
                    statusCode = null,
                    code = null,
                    message = "Cancel accepted without a receipt",
                )
            QueuedTurnOutcome.Committed(receipt)
        }

    override suspend fun isUnsupported(): Boolean = latchMutex.withLock {
        unsupportedLatched && unsupportedFor == activeAccountId()
    }

    /**
     * Runs [block] off the main thread and turns anything it throws into the outcome the failure
     * actually proves.
     *
     * Deliberately not `safeApiCall`: that maps every failure to one displayable `Result.Error`,
     * which is precisely the collapse this feature cannot survive — and it logs at error level on
     * a path where an old server answering 404 is the expected, correct answer.
     */
    private suspend fun <T> call(
        block: suspend () -> QueuedTurnOutcome<T>,
    ): QueuedTurnOutcome<T> = try {
        onApiDispatcher { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val api = e as? ApiException
        val code = ServerErrorCode.generationCodeOf(api?.body)
        classify(api?.statusCode, code, api?.message).also {
            // **Only an UNCODED answer latches.** A coded `QUEUED_TURNS_UNSUPPORTED` is
            // `authorizeConversation` saying THIS conversation is ineligible — it is not an agents
            // conversation, or its agent is ephemeral, which is an everyday case here. Latching
            // that would disable the feature for the whole account after one ephemeral-agent chat,
            // silently and for the rest of the session. An uncoded 404/501 is the route itself
            // being absent, which is a property of the deployment.
            if (it is QueuedTurnOutcome.Unsupported && code == null) latch()
        }
    }

    private suspend fun latch() = latchMutex.withLock {
        unsupportedFor = activeAccountId()
        unsupportedLatched = true
    }

    private fun activeAccountId(): String? = activeAccountProvider.currentAccountId()?.value

    /**
     * The outcome a failure proves.
     *
     * [code] must come from `generationCodeOf`, not `ServerErrorCode.from`: the unsupported test is
     * `code == null`, and `from`'s `error` fallback would read an English sentence there as a code
     * — turning "this route does not exist" into "it exists and refused", which keeps the client
     * polling it.
     */
    private fun classify(
        status: Int?,
        code: String?,
        message: String?,
    ): QueuedTurnOutcome<Nothing> = when {
        isDefiniteQueuedTurnsUnsupported(status, code) -> QueuedTurnOutcome.Unsupported
        isDefiniteQueuedTurnRejection(status, code) -> QueuedTurnOutcome.Rejected(status, code, message)
        else -> QueuedTurnOutcome.Indeterminate(status, code, message)
    }
}
