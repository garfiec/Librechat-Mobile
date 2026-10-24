package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.BackendBuildClass
import com.garfiec.librechat.core.common.DetectedBackend
import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.logging.redact.LogRedactor
import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.core.model.error.ServerErrorCode
import com.garfiec.librechat.core.network.api.MemoriesApi
import com.garfiec.librechat.core.network.api.UserApi
import com.garfiec.librechat.core.network.client.LibreChatHttpClient
import com.garfiec.librechat.core.network.client.ServerUrlProvider
import com.garfiec.librechat.core.network.di.librechatJson
import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The by-id 404 rules, through the real [LibreChatHttpClient] rather than a hand-built
 * [ApiException]. Both `isRouteMissing` (the key-route fallback) and the screen's "already gone"
 * reading decide by the error body, which only the client's response validator fills in. If it
 * stopped carrying a 404's body, every by-id 404 would read as a missing route: an edit of a memory
 * deleted elsewhere would fall back to the key route and overwrite whatever row now holds that key,
 * while every mocked test still passed.
 */
class MemoryRepositoryNotFoundThroughClientTest {

    private companion object {
        const val SERVER = "https://chat.example.com"
        const val BY_ID_PATH = "/api/memories/id/mem-1"
        const val KEY_PATH = "/api/memories/likes_tea"
        const val MEMORY_NOT_FOUND_BODY = """{"error":"Memory not found."}"""
        const val AGENT_NOT_FOUND_BODY = """{"error":"Agent not found."}"""
        const val ROUTE_MISSING_BODY =
            "<!DOCTYPE html><html><body><pre>Cannot PATCH /api/memories/id/mem-1</pre></body></html>"
        const val KEY_ROUTE_ANSWER =
            """{"updated":true,"memory":{"_id":"mem-9","key":"likes_tea","value":"no"}}"""
    }

    private class FixedUrlProvider : ServerUrlProvider {
        override fun getBaseUrl(): String = SERVER
    }

    /** What reached the engine, as "METHOD path". */
    private class Wire(val calls: MutableList<String> = mutableListOf())

    /**
     * A repository over a [MemoriesApi] on the production client, whose by-id PATCH answers 404
     * with [byIdBody] and whose key PATCH answers 200.
     */
    private fun TestScope.repository(wire: Wire, byIdBody: String, json: Boolean): MemoryRepository {
        val client = LibreChatHttpClient.create(
            engineFactory = object : HttpClientEngineFactory<MockEngineConfig> {
                override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine =
                    MockEngine(
                        MockEngineConfig().apply(block).apply {
                            dispatcher = Dispatchers.Unconfined
                            addHandler { request ->
                                val path = request.url.encodedPath
                                wire.calls += "${request.method.value} $path"
                                when {
                                    request.method == HttpMethod.Patch && path == BY_ID_PATH -> respond(
                                        content = byIdBody,
                                        status = HttpStatusCode.NotFound,
                                        headers = if (json) {
                                            headersOf("Content-Type", ContentType.Application.Json.toString())
                                        } else {
                                            headersOf("Content-Type", ContentType.Text.Html.toString())
                                        },
                                    )
                                    request.method == HttpMethod.Patch && path == KEY_PATH -> respond(
                                        content = KEY_ROUTE_ANSWER,
                                        status = HttpStatusCode.OK,
                                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                                    )
                                    else -> respond("", HttpStatusCode.InternalServerError)
                                }
                            }
                        },
                    )
            },
            json = librechatJson,
            tokenManager = RecordingTokenManager(),
            serverUrlProvider = FixedUrlProvider(),
            redactor = LogRedactor(),
            refreshCookieCredentials = { null },
        )
        val configRepository = mockk<ConfigRepository>()
        every { configRepository.detectedBackend } returns
            MutableStateFlow(DetectedBackend("0.8.8", BackendBuildClass.OFFICIAL, "2026-10-01"))
        return MemoryRepositoryImpl(
            memoriesApi = MemoriesApi(client),
            userApi = mockk<UserApi>(),
            configRepository = configRepository,
            activeAccountProvider = InMemoryActiveAccountProvider().apply { set(AccountId("srv:user-a")) },
            applicationScope = CoroutineScope(StandardTestDispatcher(testScheduler)),
        )
    }

    private val tea = Memory(key = "likes_tea", value = "yes", id = "mem-1")

    private fun Result<*>.apiException(): ApiException {
        assertThat(this).isInstanceOf(Result.Error::class.java)
        val exception = (this as Result.Error).exception
        assertThat(exception).isInstanceOf(ApiException::class.java)
        return exception as ApiException
    }

    @Test
    fun `a by-id 'Memory not found' keeps its body and never reaches the key route`() = runTest {
        val wire = Wire()

        val result = repository(wire, MEMORY_NOT_FOUND_BODY, json = true).updateMemory(tea, value = "no")

        val exception = result.apiException()
        assertThat(exception.statusCode).isEqualTo(404)
        assertThat(exception.body).isEqualTo(MEMORY_NOT_FOUND_BODY)
        // What the screen's "already gone" reading (MemoryFailureMapping.isNotFound) matches on.
        assertThat(ServerErrorCode.from(exception.body)).isEqualTo(MEMORY_NOT_FOUND)
        assertThat(wire.calls).containsExactly("PATCH $BY_ID_PATH")
    }

    @Test
    fun `a by-id 'Agent not found' keeps its body and never reaches the key route`() = runTest {
        val wire = Wire()
        val scoped = tea.copy(agentId = "agent_7")

        val result = repository(wire, AGENT_NOT_FOUND_BODY, json = true).updateMemory(scoped, value = "no")

        val exception = result.apiException()
        assertThat(exception.statusCode).isEqualTo(404)
        assertThat(exception.body).isEqualTo(AGENT_NOT_FOUND_BODY)
        assertThat(ServerErrorCode.from(exception.body)).isEqualTo(MEMORY_AGENT_NOT_FOUND)
        assertThat(wire.calls).containsExactly("PATCH $BY_ID_PATH")
    }

    /** The positive control: without it the two above could pass because the fallback is broken. */
    @Test
    fun `Express's HTML 404 for a missing route falls back to the key route`() = runTest {
        val wire = Wire()

        val result = repository(wire, ROUTE_MISSING_BODY, json = false).updateMemory(tea, value = "no")

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat((result as Result.Success).data?.key).isEqualTo("likes_tea")
        assertThat(wire.calls).containsExactly("PATCH $BY_ID_PATH", "PATCH $KEY_PATH").inOrder()
    }
}
