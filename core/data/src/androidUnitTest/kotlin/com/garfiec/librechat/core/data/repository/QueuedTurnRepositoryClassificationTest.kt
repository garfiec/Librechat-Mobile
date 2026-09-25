package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.model.queuedturn.AgentQueuedTurnCapability
import com.garfiec.librechat.core.model.queuedturn.AgentQueuedTurnReceipt
import com.garfiec.librechat.core.model.queuedturn.EnqueueQueuedTurnRequest
import com.garfiec.librechat.core.model.queuedturn.EnqueueQueuedTurnResponse
import com.garfiec.librechat.core.model.queuedturn.ListQueuedTurnsResponse
import com.garfiec.librechat.core.model.queuedturn.QueuedTurnErrorCode
import com.garfiec.librechat.core.model.queuedturn.QueuedTurnOutcome
import com.garfiec.librechat.core.network.api.QueuedTurnsApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * What a failed enqueue PROVES. The server has no guard against a turn being enqueued and then
 * also sent the ordinary way, so misreading an ambiguous failure as "never committed" is how the
 * same words get submitted twice — these assertions are the only thing standing in front of that.
 */
class QueuedTurnRepositoryClassificationTest {

    private val api = mockk<QueuedTurnsApi>()
    private val accounts = InMemoryActiveAccountProvider(AccountState.Resolved(AccountId("acct-a")))
    private val repository = QueuedTurnRepositoryImpl(api, accounts)

    private val request = EnqueueQueuedTurnRequest(
        conversationId = "convo-1",
        parentMessageId = "msg-1",
        clientRequestId = "req-1",
        text = "follow up",
    )

    private fun failing(statusCode: Int, body: String?) {
        coEvery { api.enqueueQueuedTurn(any()) } throws
            ApiException(statusCode = statusCode, message = "failed", body = body)
        coEvery { api.listQueuedTurns(any(), any()) } throws
            ApiException(statusCode = statusCode, message = "failed", body = body)
    }

    @Test
    fun `an old server's uncoded 404 is definitive`() = runTest {
        // `apiNotFound` answers `{"message":"Endpoint not found"}` — no `code` — and it runs
        // before the SPA fallback, so this really is the shape an rc1 server sends.
        failing(404, """{"message":"Endpoint not found"}""")

        assertThat(repository.enqueue(request)).isEqualTo(QueuedTurnOutcome.Unsupported)
    }

    @Test
    fun `a proxy's 404 sentence is not mistaken for an error code`() = runTest {
        // Base-path and gateway deployments put a reverse proxy in front, and its 404 body is
        // whatever the proxy writes. Read through `ServerErrorCode.from`, the sentence under
        // `error` comes back as a code, the uncoded arm stops matching, and the client polls a
        // route that does not exist for the rest of the session.
        failing(404, """{"error":"Not Found"}""")

        assertThat(repository.enqueue(request)).isEqualTo(QueuedTurnOutcome.Unsupported)
    }

    @Test
    fun `a coded 404 came from the feature and is not an absent route`() = runTest {
        failing(404, """{"code":"CONVERSATION_NOT_FOUND"}""")

        assertThat(repository.enqueue(request)).isInstanceOf(QueuedTurnOutcome.Rejected::class.java)
    }

    @Test
    fun `a bounded 4xx proves the row was not committed`() = runTest {
        failing(409, """{"code":"${QueuedTurnErrorCode.IDEMPOTENCY_CONFLICT}"}""")

        val outcome = repository.enqueue(request)
        assertThat(outcome).isInstanceOf(QueuedTurnOutcome.Rejected::class.java)
        assertThat((outcome as QueuedTurnOutcome.Rejected).code)
            .isEqualTo(QueuedTurnErrorCode.IDEMPOTENCY_CONFLICT)
    }

    @Test
    fun `a 5xx is ambiguous and must never be resendable`() = runTest {
        failing(500, null)

        assertThat(repository.enqueue(request))
            .isInstanceOf(QueuedTurnOutcome.Indeterminate::class.java)
    }

    @Test
    fun `408 and 425 are ambiguous despite being 4xx`() = runTest {
        // Both can be generated while the origin request is still running, so neither proves the
        // row was not committed — they are the two holes in "a bounded 4xx is proof".
        failing(408, null)
        assertThat(repository.enqueue(request))
            .isInstanceOf(QueuedTurnOutcome.Indeterminate::class.java)

        failing(425, null)
        assertThat(repository.enqueue(request))
            .isInstanceOf(QueuedTurnOutcome.Indeterminate::class.java)
    }

    @Test
    fun `a dropped connection is ambiguous`() = runTest {
        coEvery { api.enqueueQueuedTurn(any()) } throws IllegalStateException("socket closed")

        assertThat(repository.enqueue(request))
            .isInstanceOf(QueuedTurnOutcome.Indeterminate::class.java)
    }

    @Test
    fun `a 2xx that announces no support is not a committed row`() = runTest {
        // The trap: this decodes cleanly and looks like success. Read as committed, the local
        // queue would refuse to drain a turn that nothing will ever run.
        coEvery { api.enqueueQueuedTurn(any()) } returns EnqueueQueuedTurnResponse(
            receipt = receipt(),
            capability = AgentQueuedTurnCapability(supported = false),
        )

        assertThat(repository.enqueue(request)).isEqualTo(QueuedTurnOutcome.Unsupported)
    }

    @Test
    fun `a 2xx with no receipt is ambiguous rather than committed`() = runTest {
        coEvery { api.enqueueQueuedTurn(any()) } returns EnqueueQueuedTurnResponse(
            receipt = null,
            capability = AgentQueuedTurnCapability(supported = true),
        )

        assertThat(repository.enqueue(request))
            .isInstanceOf(QueuedTurnOutcome.Indeterminate::class.java)
    }

    @Test
    fun `an announced capability commits the row`() = runTest {
        coEvery { api.enqueueQueuedTurn(any()) } returns EnqueueQueuedTurnResponse(
            receipt = receipt(),
            capability = AgentQueuedTurnCapability(supported = true, durability = "durable"),
        )

        assertThat(repository.enqueue(request))
            .isEqualTo(QueuedTurnOutcome.Committed(receipt()))
    }

    @Test
    fun `a list call on a supporting server returns its rows`() = runTest {
        coEvery { api.listQueuedTurns("convo-1", listOf("req-1")) } returns ListQueuedTurnsResponse(
            queuedTurns = listOf(receipt()),
            capability = AgentQueuedTurnCapability(supported = true, durability = "durable"),
        )

        assertThat(repository.list("convo-1", listOf("req-1")))
            .isEqualTo(QueuedTurnOutcome.Committed(listOf(receipt())))
    }

    @Test
    fun `an absent route latches`() = runTest {
        failing(404, """{"message":"Endpoint not found"}""")
        assertThat(repository.isUnsupported()).isFalse()

        repository.enqueue(request)

        assertThat(repository.isUnsupported()).isTrue()
    }

    @Test
    fun `a coded refusal answers for one conversation and must not latch`() = runTest {
        // `QUEUED_TURNS_UNSUPPORTED` is what `authorizeConversation` answers when THIS
        // conversation is not an agents one or its agent is EPHEMERAL — which is an everyday
        // case here. Latching it would disable queued turns for the whole account after one
        // ephemeral-agent chat, with nothing on screen to explain why.
        failing(501, """{"code":"${QueuedTurnErrorCode.UNSUPPORTED}"}""")

        assertThat(repository.enqueue(request)).isEqualTo(QueuedTurnOutcome.Unsupported)
        assertThat(repository.isUnsupported()).isFalse()
    }

    @Test
    fun `a coded priority refusal is about the request and must not latch`() = runTest {
        failing(501, """{"code":"${QueuedTurnErrorCode.PRIORITY_UNSUPPORTED}"}""")

        assertThat(repository.enqueue(request)).isEqualTo(QueuedTurnOutcome.Unsupported)
        assertThat(repository.isUnsupported()).isFalse()
    }

    @Test
    fun `an uncoded 501 is the route itself and latches`() = runTest {
        failing(501, null)

        assertThat(repository.enqueue(request)).isEqualTo(QueuedTurnOutcome.Unsupported)
        assertThat(repository.isUnsupported()).isTrue()
    }

    @Test
    fun `a capability that announces no support does not latch either`() = runTest {
        // The route is plainly there — it answered 200. Only the conversation is ineligible.
        coEvery { api.enqueueQueuedTurn(any()) } returns EnqueueQueuedTurnResponse(
            receipt = null,
            capability = AgentQueuedTurnCapability(supported = false),
        )

        assertThat(repository.enqueue(request)).isEqualTo(QueuedTurnOutcome.Unsupported)
        assertThat(repository.isUnsupported()).isFalse()
    }

    @Test
    fun `the latch does not carry onto another account`() = runTest {
        // A latch is per server. Carried across a switch it would suppress the feature on a
        // server that has it, with nothing on screen to explain why.
        failing(404, """{"message":"Endpoint not found"}""")
        repository.enqueue(request)

        accounts.set(AccountId("acct-b"))

        assertThat(repository.isUnsupported()).isFalse()
    }

    private fun receipt() = AgentQueuedTurnReceipt(
        queuedTurnId = "qt-1",
        clientRequestId = "req-1",
        conversationId = "convo-1",
        parentMessageId = "msg-1",
        text = "follow up",
        status = "queued",
        revision = 1,
        position = 1,
    )
}
