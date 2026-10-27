package com.garfiec.librechat.core.data.repository

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.common.result.AccessGatewayException
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.FailureKind
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ConfigCacheDataStore
import com.garfiec.librechat.core.logging.LogEnvelope
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.network.api.ConfigApi
import com.garfiec.librechat.core.network.client.PendingRequestIdentity
import com.google.common.truth.Truth.assertThat
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.statement.HttpResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException

/**
 * The server-check and reload seams. Resolving a server must not leak into the live server's config
 * state or cache (it runs for a server being ADDED while another is live, and before the URL is
 * stored at all), it must try the origin only when the typed path answered and wasn't LibreChat, and
 * a switch must reseed the in-memory state from the incoming server's cache.
 */
class ConfigRepositoryImplTest {

    private val configApi = mockk<ConfigApi>()
    private val configCache = mockk<ConfigCacheDataStore>(relaxed = true)

    private fun repository() = ConfigRepositoryImpl(
        configApi = configApi,
        configCache = configCache,
        dispatcher = UnconfinedTestDispatcher(),
        serverDataStore = mockk(relaxed = true),
    )

    private val validConfig = StartupConfig(serverDomain = "https://b.example.com")

    private suspend fun ConfigRepositoryImpl.resolve(
        vararg candidates: String,
        headers: Map<String, String> = emptyMap(),
    ): ServerResolution = resolveServer(candidates.toList(), headers)

    private fun ServerResolution.error(): Result.Error = (this as ServerResolution.Failed).error

    /** What Ktor throws when `text/html` comes back where the config JSON was expected. */
    private fun webPage() =
        NoTransformationFoundException(mockk<HttpResponse>(relaxed = true), String::class, StartupConfig::class)

    /** The base URL the request was issued under, as the SwitchBarrier would read it. */
    private suspend fun probedBase(): String = currentCoroutineContext()[PendingRequestIdentity]!!.identity().baseUrl

    @Test
    fun `resolving validates without publishing in-memory state or writing the cache`() = runTest {
        coEvery { configApi.getStartupConfig() } returns validConfig
        val repo = repository()

        val result = repo.resolve("https://b.example.com")

        assertThat(result).isEqualTo(ServerResolution.Resolved("https://b.example.com", validConfig))
        // The live session's config must be untouched: an add-mode probe runs while another
        // account is active, and the cache key would derive from the LIVE url (poisoning it).
        assertThat(repo.startupConfig.value).isNull()
        coVerify(exactly = 0) { configCache.saveStartupConfig(any()) }
    }

    @Test
    fun `a cancelled probe propagates instead of reporting an unreachable server`() = runTest {
        coEvery { configApi.getStartupConfig() } coAnswers { awaitCancellation() }
        val repo = repository()
        var result: ServerResolution? = null

        val job = launch { result = repo.resolve("https://b.example.com", "https://example.com") }
        runCurrent()
        job.cancel()
        job.join()

        assertThat(result).isNull()
    }

    @Test
    fun `a non-LibreChat response is rejected`() = runTest {
        coEvery { configApi.getStartupConfig() } returns StartupConfig(serverDomain = "")
        val repo = repository()

        val result = repo.resolve("https://b.example.com")

        assertThat(result.error().message).contains("LibreChat")
    }

    /** A web page answering 200 at the path: Ktor has no transformation for text/html into the config. */
    @Test
    fun `a web page where the config should be is not LibreChat, not an unreachable server`() = runTest {
        coEvery { configApi.getStartupConfig() } throws webPage()
        val repo = repository()

        val result = repo.resolve("https://b.example.com")

        assertThat(result.error().message).contains("LibreChat")
    }

    @Test
    fun `publishStartupConfig publishes and caches`() = runTest {
        val repo = repository()

        repo.publishStartupConfig(validConfig)

        assertThat(repo.startupConfig.value).isEqualTo(validConfig)
        coVerify(exactly = 1) { configCache.saveStartupConfig(validConfig) }
    }

    @Test
    fun `reloadForActiveServer replaces in-memory state from the cache without network`() = runTest {
        coEvery { configApi.getStartupConfig() } returns validConfig
        val repo = repository()
        repo.publishStartupConfig(validConfig) // in-memory now holds the outgoing server

        val incoming = StartupConfig(serverDomain = "https://b.example.com", version = "9.9.9")
        val incomingEndpoints = mapOf("openAI" to EndpointConfig())
        coEvery { configCache.loadStartupConfig() } returns incoming
        coEvery { configCache.loadEndpointConfigs() } returns incomingEndpoints
        coEvery { configCache.loadAvailableModels() } returns null

        repo.reloadForActiveServer()

        assertThat(repo.startupConfig.value).isEqualTo(incoming)
        assertThat(repo.endpointConfigs.value).isEqualTo(incomingEndpoints)
        assertThat(repo.availableModels.value).isEmpty()
        assertThat(repo.detectedBackendVersion.value).isNull() // re-detected on the next check
    }

    @Test
    fun `checkBackendVersion publishes a supported version free of build metadata`() = runTest {
        // A partial-sync pin carries a "+dev.<sha>" suffix. It belongs in diagnostics, not in the
        // mismatch dialog this value is interpolated into.
        coEvery { configApi.getStartupConfig() } returns
            StartupConfig(serverDomain = "https://b.example.com", version = "0.8.6")
        val repo = repository()

        val result = repo.checkBackendVersion()

        val supported = (result as Result.Success).data.supportedVersion
        assertThat(supported).doesNotContain("+")
        assertThat(supported)
            .isEqualTo(BackendVersion.parse(BackendVersion.SUPPORTED_BACKEND_VERSION)?.toString())
    }

    @Test
    fun `reloadForActiveServer empties state when the incoming server has no cache`() = runTest {
        coEvery { configApi.getStartupConfig() } returns validConfig
        val repo = repository()
        repo.publishStartupConfig(validConfig)

        coEvery { configCache.loadStartupConfig() } returns null
        coEvery { configCache.loadEndpointConfigs() } returns null
        coEvery { configCache.loadAvailableModels() } returns null

        repo.reloadForActiveServer()

        assertThat(repo.startupConfig.value).isNull()
        assertThat(repo.endpointConfigs.value).isEmpty()
        assertThat(repo.availableModels.value).isEmpty()
    }

    @Test
    fun `fetchModels drops an agents key and duplicate models before publishing and caching`() = runTest {
        coEvery { configApi.getModels() } returns mapOf(
            "agents" to listOf("agent_1", "agent_2"),
            "openAI" to listOf("gpt-4o", "gpt-4o", "o3"),
        )
        coEvery { configCache.loadAvailableModels() } returns null
        val repo = repository()

        val result = repo.fetchModels()

        val expected = mapOf("openAI" to listOf("gpt-4o", "o3"))
        assertThat((result as Result.Success).data).isEqualTo(expected)
        assertThat(repo.availableModels.value).isEqualTo(expected)
        coVerify { configCache.saveAvailableModels(expected) }
    }

    @Test
    fun `fetchModels normalizes a cached map served while the network is down`() = runTest {
        // A cache written by an older build still carries the agents key.
        coEvery { configCache.loadAvailableModels() } returns mapOf("agents" to listOf("agent_1"), "openAI" to listOf("o3"))
        coEvery { configApi.getModels() } throws IOException("offline")
        val repo = repository()

        val result = repo.fetchModels()

        assertThat((result as Result.Success).data).isEqualTo(mapOf("openAI" to listOf("o3")))
        assertThat(repo.availableModels.value).isEqualTo(mapOf("openAI" to listOf("o3")))
    }

    @Test
    fun `reloadForActiveServer normalizes the cached models`() = runTest {
        coEvery { configCache.loadStartupConfig() } returns null
        coEvery { configCache.loadEndpointConfigs() } returns null
        coEvery { configCache.loadAvailableModels() } returns mapOf("agents" to listOf("agent_1"))
        val repo = repository()

        repo.reloadForActiveServer()

        assertThat(repo.availableModels.value).isEmpty()
    }

    /**
     * The config probe is where a gateway-blocked server is met first, before login. The kind matters
     * as much as the wording — it is what a UI layer would localize or branch on.
     */
    @Test
    fun `a gateway rejection is reported as a gateway problem, not an unreachable server`() = runTest {
        coEvery { configApi.getStartupConfig() } throws AccessGatewayException()
        val repo = repository()

        val error = repo.resolve("https://b.example.com").error()

        assertThat(error.kind).isEqualTo(FailureKind.AccessGateway)
        assertThat(error.message).doesNotContain("Could not reach the server")
        // Not the shared post-login wording: it points at Settings, which needs the sign-in the
        // gateway is blocking.
        assertThat(error.message).doesNotContain("Settings")
        assertThat(error.message).contains("Advanced")
    }

    private class CapturingLogWriter : LogWriter() {
        val entries = mutableListOf<Pair<Severity, LogEnvelope.Decoded>>()

        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            if (tag == "ServerProbe") entries += severity to LogEnvelope.decode(message)
        }
    }

    private suspend fun captureProbeLogs(block: suspend () -> Unit): List<Pair<Severity, LogEnvelope.Decoded>> {
        val writer = CapturingLogWriter()
        Logger.setLogWriters(writer)
        try {
            block()
        } finally {
            Logger.setLogWriters(emptyList())
        }
        return writer.entries
    }

    @Test
    fun `an unreachable server is logged with its reason and without the host`() = runTest {
        coEvery { configApi.getStartupConfig() } throws ConnectException("Failed to connect")
        val repo = repository()

        val logged = captureProbeLogs { repo.resolve("https://b.example.com") }

        val (severity, entry) = logged.single()
        assertThat(severity).isEqualTo(Severity.Warn)
        assertThat(entry.attrs).containsExactly(
            "path", "/api/config", "reason", "unreachable", "candidate", "typed", "fell_back", "false",
        )
        assertThat(entry.origin).isEqualTo("network")
    }

    @Test
    fun `an untrusted certificate is reported as a certificate problem, not as unreachable`() = runTest {
        coEvery { configApi.getStartupConfig() } throws
            SSLHandshakeException("java.security.cert.CertPathValidatorException: Trust anchor not found").apply {
                initCause(CertificateException("java.security.cert.CertPathValidatorException: Trust anchor not found"))
            }
        val repo = repository()

        lateinit var result: ServerResolution
        val logged = captureProbeLogs { result = repo.resolve("https://b.example.com") }

        val error = result.error()
        assertThat(error.kind).isEqualTo(FailureKind.Tls)
        assertThat(error.message).contains("certificate")
        assertThat(logged.single().second.attrs).containsEntry("reason", "tls")
    }

    /** LibreChat's default port serves plain HTTP; https:// against it is a URL fix, not a certificate one. */
    @Test
    fun `https against a plain-HTTP port is reported as unreachable, not as a certificate problem`() = runTest {
        coEvery { configApi.getStartupConfig() } throws
            SSLHandshakeException("SSL handshake aborted: Failure in SSL library, usually a protocol error WRONG_VERSION_NUMBER")
        val repo = repository()

        lateinit var result: ServerResolution
        val logged = captureProbeLogs { result = repo.resolve("https://b.example.com") }

        assertThat(result.error().kind).isNotEqualTo(FailureKind.Tls)
        assertThat(logged.single().second.attrs).containsEntry("reason", "unreachable")
    }

    @Test
    fun `a server that answers with an error status is logged with that status`() = runTest {
        coEvery { configApi.getStartupConfig() } throws ApiException(502, "Bad gateway")
        val repo = repository()

        val logged = captureProbeLogs { repo.resolve("https://b.example.com") }

        val (_, entry) = logged.single()
        assertThat(entry.attrs).containsEntry("reason", "http_status")
        assertThat(entry.attrs).containsEntry("status", "502")
        assertThat(entry.origin).isEqualTo("server")
    }

    @Test
    fun `a successful server check logs nothing here`() = runTest {
        coEvery { configApi.getStartupConfig() } returns validConfig
        val repo = repository()

        val logged = captureProbeLogs { repo.resolve("https://b.example.com") }

        assertThat(logged).isEmpty()
    }

    // --- Which candidate wins -------------------------------------------------------------------

    private val typed = "https://example.com/some/page"
    private val origin = "https://example.com"

    /** Answers per candidate, recording the order they were tried in. */
    private fun answering(vararg byBase: Pair<String, () -> StartupConfig>): MutableList<String> {
        val tried = mutableListOf<String>()
        val answers = byBase.toMap()
        coEvery { configApi.getStartupConfig() } coAnswers {
            val base = probedBase()
            tried += base
            answers.getValue(base)()
        }
        return tried
    }

    @Test
    fun `each candidate is probed under its own pending identity, carrying the headers`() = runTest {
        val seen = mutableListOf<Pair<String, Map<String, String>>>()
        coEvery { configApi.getStartupConfig() } coAnswers {
            val identity = currentCoroutineContext()[PendingRequestIdentity]!!.identity()
            seen += identity.baseUrl to identity.customHeaders
            if (identity.baseUrl == typed) throw ApiException(404, "Not Found") else validConfig
        }
        val repo = repository()

        repo.resolve(typed, origin, headers = mapOf("CF-Access-Client-Id" to "id"))

        assertThat(seen).containsExactly(
            typed to mapOf("CF-Access-Client-Id" to "id"),
            origin to mapOf("CF-Access-Client-Id" to "id"),
        ).inOrder()
    }

    @Test
    fun `a typed path that serves LibreChat wins without trying the origin`() = runTest {
        val tried = answering(typed to { validConfig }, origin to { error("must not be tried") })
        val repo = repository()

        val result = repo.resolve(typed, origin)

        assertThat(result).isEqualTo(ServerResolution.Resolved(typed, validConfig))
        assertThat(tried).containsExactly(typed)
    }

    @Test
    fun `a typed path that 404s falls back to the origin`() = runTest {
        val tried = answering(typed to { throw ApiException(404, "Not Found") }, origin to { validConfig })
        val repo = repository()

        val result = repo.resolve(typed, origin)

        assertThat(result).isEqualTo(ServerResolution.Resolved(origin, validConfig))
        assertThat(tried).containsExactly(typed, origin).inOrder()
    }

    @Test
    fun `a typed path serving a web page falls back to the origin`() = runTest {
        answering(
            typed to { throw webPage() },
            origin to { validConfig },
        )
        val repo = repository()

        assertThat(repo.resolve(typed, origin)).isEqualTo(ServerResolution.Resolved(origin, validConfig))
    }

    /** Same host, same outcome: a second wait would only bury the error. */
    @Test
    fun `an unreachable host is not retried at the origin`() = runTest {
        val tried = answering(typed to { throw IOException("Connection refused") }, origin to { validConfig })
        val repo = repository()

        val result = repo.resolve(typed, origin)

        assertThat(result.error().message).contains("Could not reach")
        assertThat(tried).containsExactly(typed)
    }

    @Test
    fun `a gateway at the typed path is not retried at the origin`() = runTest {
        val tried = answering(typed to { throw AccessGatewayException() }, origin to { validConfig })
        val repo = repository()

        val result = repo.resolve(typed, origin)

        assertThat(result.error().kind).isEqualTo(FailureKind.AccessGateway)
        assertThat(tried).containsExactly(typed)
    }

    /** Something is serving that path; it's broken, not absent. */
    @Test
    fun `a server error at the typed path is not retried at the origin`() = runTest {
        val tried = answering(typed to { throw ApiException(502, "Bad gateway") }, origin to { validConfig })
        val repo = repository()

        repo.resolve(typed, origin)

        assertThat(tried).containsExactly(typed)
    }

    @Test
    fun `when both miss, the typed address's error is reported`() = runTest {
        answering(
            typed to { throw ApiException(404, "Not Found") },
            origin to { throw IOException("Connection reset") },
        )
        val repo = repository()

        val logged = captureProbeLogs { assertThat(repo.resolve(typed, origin).error().message).contains("LibreChat") }

        assertThat(logged.map { it.first to it.second.attrs["candidate"] }).containsExactly(
            Severity.Info to "typed",
            Severity.Warn to "origin",
        ).inOrder()
        assertThat(logged.first().second.attrs).containsEntry("fell_back", "true")
    }

    @Test
    fun `when the origin is behind a gateway the path hid, that is the error reported`() = runTest {
        answering(typed to { throw ApiException(404, "Not Found") }, origin to { throw AccessGatewayException() })
        val repo = repository()

        assertThat(repo.resolve(typed, origin).error().kind).isEqualTo(FailureKind.AccessGateway)
    }

    @Test
    fun `resolving never publishes or caches, even when the origin wins`() = runTest {
        answering(typed to { throw ApiException(404, "Not Found") }, origin to { validConfig })
        val repo = repository()

        repo.resolve(typed, origin)

        assertThat(repo.startupConfig.value).isNull()
        coVerify(exactly = 0) { configCache.saveStartupConfig(any()) }
    }
}
