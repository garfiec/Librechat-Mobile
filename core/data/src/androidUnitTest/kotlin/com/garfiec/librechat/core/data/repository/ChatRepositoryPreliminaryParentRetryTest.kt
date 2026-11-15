package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.network.ConnectivityObserver
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.response.ChatStartResponse
import com.garfiec.librechat.core.network.api.ChatApi
import com.garfiec.librechat.core.network.sse.SseClient
import com.google.common.truth.Truth.assertThat
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.IOException
import kotlin.test.assertFailsWith

/**
 * Start request retries. A send whose parent response is still being written 409s, and that one
 * 409 is transient — it clears in milliseconds, so the repository retries it rather than telling
 * the user their message failed. A start whose response is lost, or that a starting server answers
 * 503 `SERVER_NOT_READY`, is re-sent with the same body, so the server deduplicates it by
 * `clientRequestId` instead of starting a second run.
 *
 * These tests exist because the discriminator is subtle and getting it wrong fails SILENTLY: the
 * body carries no `code`, only an English sentence under `error`, so a reader with the MCP
 * controller's `error` fallback hands that sentence back as if it were a code and the retry never
 * runs. Nothing fails to compile, nothing logs, and the send just surfaces as an error.
 *
 * Driven through the repository rather than the code reader alone, because the reader agreeing in
 * isolation is exactly what the broken version also did.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepositoryPreliminaryParentRetryTest {

    private val chatApi = mockk<ChatApi>()
    private val sseClient = mockk<SseClient>(relaxed = true)
    private val connectivityObserver = mockk<ConnectivityObserver>(relaxed = true)

    /** The exact 409 body `rejectPreliminaryParentMessageId` writes: an `error`, and no `code`. */
    private val preliminaryParent = ApiException(
        statusCode = 409,
        message = "Conflict",
        body = """{"error":"${
            "Cannot submit a follow-up while the selected parent response is still being saved. " +
                "Please wait and try again."
        }","generationProtocolVersion":1}""",
    )

    private fun notReady(retryAfterSeconds: Long? = 1) = ApiException(
        statusCode = 503,
        message = "Service Unavailable",
        body = """{"code":"SERVER_NOT_READY","error":"Server is still starting. Please retry shortly."}""",
        retryAfterSeconds = retryAfterSeconds,
    )

    private val accounts = InMemoryActiveAccountProvider(AccountState.Resolved(AccountId("acct-a")))

    private fun repository(dispatcher: CoroutineDispatcher) = ChatRepositoryImpl(
        chatApi = chatApi,
        sseClient = sseClient,
        connectivityObserver = connectivityObserver,
        dispatcher = dispatcher,
        json = Json,
        projectChanges = ProjectChangeSignal(),
        activeAccountProvider = accounts,
    )

    private fun ChatRepositoryImpl.send() = startChat(
        text = "and the second thing",
        conversationId = "conv-1",
        endpoint = "agents",
        model = "gpt-4o",
    )

    private suspend fun sendFirstEvent(dispatcher: CoroutineDispatcher): StreamEvent =
        repository(dispatcher).send().first()

    /** Stubs the start POST with [replies] in order (the last one repeats) and records each body. */
    private fun stubStart(vararg replies: () -> ChatStartResponse): MutableList<JsonObject> {
        val bodies = mutableListOf<JsonObject>()
        coEvery { chatApi.startChat(any(), any<JsonObject>()) } answers {
            bodies += secondArg<JsonObject>()
            replies[minOf(bodies.size, replies.size) - 1]()
        }
        return bodies
    }

    private val List<JsonObject>.requestIds get() = map { it["clientRequestId"]!!.jsonPrimitive.content }

    private val started: () -> ChatStartResponse = { ChatStartResponse(conversationId = "conv-1") }

    @Test
    fun `an uncoded 409 is retried and the send goes through`() = runTest {
        var attempts = 0
        coEvery { chatApi.startChat(any(), any<JsonObject>()) } answers {
            attempts++
            if (attempts == 1) throw preliminaryParent else ChatStartResponse(conversationId = "conv-1")
        }

        val created = sendFirstEvent(UnconfinedTestDispatcher(testScheduler))

        assertThat(attempts).isEqualTo(2)
        assertThat((created as StreamEvent.Created).conversationId).isEqualTo("conv-1")
    }

    @Test
    fun `a coded 409 beside it is reported rather than retried`() = runTest {
        var attempts = 0
        // RESOURCE_RECOVERY_REQUIRED needs the user to reattach files: retrying it would loop
        // against a condition only they can clear.
        coEvery { chatApi.startChat(any(), any<JsonObject>()) } answers {
            attempts++
            throw ApiException(
                statusCode = 409,
                message = "Conflict",
                body = """{"code":"resource_recovery_required","error":"Attached resources must be restored."}""",
            )
        }

        assertFailsWith<ApiException> { sendFirstEvent(UnconfinedTestDispatcher(testScheduler)) }

        assertThat(attempts).isEqualTo(1)
    }

    @Test
    fun `the retry is bounded, so a 409 that never clears still reports`() = runTest {
        var attempts = 0
        coEvery { chatApi.startChat(any(), any<JsonObject>()) } answers {
            attempts++
            throw preliminaryParent
        }

        assertFailsWith<ApiException> { sendFirstEvent(UnconfinedTestDispatcher(testScheduler)) }

        // The initial attempt plus the retry budget — not an unbounded loop against a stuck server.
        assertThat(attempts).isEqualTo(4)
    }

    @Test
    fun `a start whose response is lost is re-sent with the same clientRequestId`() = runTest {
        val lostResponses = listOf<Throwable>(
            IOException("unexpected end of stream"),
            HttpRequestTimeoutException("http://host/api/agents/chat/agents", 30_000),
        )
        for (lost in lostResponses) {
            val bodies = stubStart({ throw lost }, started)

            val created = sendFirstEvent(UnconfinedTestDispatcher(testScheduler))

            assertThat(bodies).hasSize(2)
            assertThat(bodies.requestIds.distinct()).hasSize(1)
            assertThat((created as StreamEvent.Created).conversationId).isEqualTo("conv-1")
        }
    }

    @Test
    fun `a 503 SERVER_NOT_READY at start is retried after Retry-After`() = runTest {
        val bodies = stubStart({ throw notReady(retryAfterSeconds = 1) }, started)

        val created = sendFirstEvent(UnconfinedTestDispatcher(testScheduler))

        assertThat(bodies).hasSize(2)
        assertThat(bodies.requestIds.distinct()).hasSize(1)
        assertThat(currentTime).isEqualTo(1_000)
        assertThat(created).isInstanceOf(StreamEvent.Created::class.java)
    }

    @Test
    fun `a long Retry-After on SERVER_NOT_READY is honoured up to 30 s`() = runTest {
        stubStart({ throw notReady(retryAfterSeconds = 5) }, started)
        sendFirstEvent(UnconfinedTestDispatcher(testScheduler))
        assertThat(currentTime).isEqualTo(5_000)

        stubStart({ throw notReady(retryAfterSeconds = 60) }, started)
        sendFirstEvent(UnconfinedTestDispatcher(testScheduler))
        assertThat(currentTime - 5_000).isEqualTo(30_000)
    }

    @Test
    fun `SERVER_NOT_READY is retried through the readiness window, then reported`() = runTest {
        val bodies = stubStart({ throw notReady(retryAfterSeconds = 1) })

        assertFailsWith<ApiException> { sendFirstEvent(UnconfinedTestDispatcher(testScheduler)) }

        assertThat(bodies.size).isEqualTo(121)
        assertThat(currentTime).isEqualTo(120_000)
    }

    @Test
    fun `a dropped start is retried twice, then reported`() = runTest {
        val bodies = stubStart({ throw IOException("connection reset") })

        assertFailsWith<IOException> { sendFirstEvent(UnconfinedTestDispatcher(testScheduler)) }

        assertThat(bodies.size).isEqualTo(3)
        assertThat(currentTime).isEqualTo(3_000)
    }

    @Test
    fun `a start the server answers resumed opens the stream with resume`() = runTest {
        every { sseClient.connect(any(), any(), any()) } returns emptyFlow()
        stubStart({ ChatStartResponse(conversationId = "conv-1", status = "resumed") })
        repository(UnconfinedTestDispatcher(testScheduler)).send().toList()
        verify { sseClient.connect("api/agents/chat/stream/conv-1", true, any()) }

        stubStart({ ChatStartResponse(conversationId = "conv-2", status = "started") })
        repository(UnconfinedTestDispatcher(testScheduler)).send().toList()
        verify { sseClient.connect("api/agents/chat/stream/conv-2", false, any()) }
    }

    @Test
    fun `a start retry stops when the account changed during the wait`() = runTest {
        // The client resolves server and token per request, so a retry now would post this
        // account's message to the other account's server.
        for (failure in listOf<Exception>(notReady(), IOException("connection reset"))) {
            accounts.set(AccountId("acct-a"))
            coEvery { chatApi.startChat(any(), any<JsonObject>()) } answers {
                accounts.set(AccountId("acct-b"))
                throw failure
            }

            val thrown = runCatching { sendFirstEvent(UnconfinedTestDispatcher(testScheduler)) }.exceptionOrNull()

            assertThat(thrown).isInstanceOf(failure::class.java)
            coVerify(exactly = 1) { chatApi.startChat(any(), any<JsonObject>()) }
            clearMocks(chatApi)
        }
    }

    @Test
    fun `start failures other than a drop, a not-ready 503 or an uncoded 409 are reported at once`() = runTest {
        val failures = listOf<Throwable>(
            ApiException(statusCode = 503, message = "Service Unavailable"),
            ApiException(
                statusCode = 503,
                message = "Service Unavailable",
                body = """{"code":"PARENT_STATE_UNAVAILABLE"}""",
                retryAfterSeconds = 1,
            ),
            ApiException(statusCode = 400, message = "Bad Request"),
            SerializationException("bad body"),
        )
        for (failure in failures) {
            val bodies = stubStart({ throw failure })

            val thrown = runCatching { sendFirstEvent(UnconfinedTestDispatcher(testScheduler)) }.exceptionOrNull()

            // Not the same instance: coroutine stack-trace recovery may copy the exception.
            assertThat(thrown).isInstanceOf(failure::class.java)
            assertThat(thrown?.message).isEqualTo(failure.message)
            assertThat(bodies).hasSize(1)
        }
    }
}
