package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.BackendBuildClass
import com.garfiec.librechat.core.common.DetectedBackend
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ConfigCacheDataStore
import com.garfiec.librechat.core.model.request.ContextProjectionRequest
import com.garfiec.librechat.core.model.usage.ModelTokenomics
import com.garfiec.librechat.core.network.api.EndpointTokenApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The context-projection suppression. The endpoint was removed on the 0.8.8 line, and since the
 * v0.8.8-rc1 tag shipped the gate is a plain version compare: servers reporting >= 0.8.8-rc1 skip
 * the POST; anything older (including dev builds that still report 0.8.7) issues it and discards
 * the 404 — the harmless direction.
 */
class EndpointTokenRepositoryImplTest {

    private val api = mockk<EndpointTokenApi>()
    private val configRepository = mockk<ConfigRepository>()
    private val configCache = mockk<ConfigCacheDataStore>(relaxed = true)

    private val request = ContextProjectionRequest(
        conversationId = "convo_1",
        messageId = "msg_1",
        endpoint = "openAI",
    )

    private fun repository(detected: DetectedBackend?): EndpointTokenRepository {
        every { configRepository.detectedBackend } returns MutableStateFlow(detected)
        return EndpointTokenRepositoryImpl(api, configRepository, configCache)
    }

    private fun devBackend(commitDate: String) =
        DetectedBackend("0.8.7", BackendBuildClass.DEV, commitDate)

    @Test
    fun `a dev build still reporting the previous release gets the projection`() = runTest {
        coEvery { api.getContextProjection(any()) } returns null

        val result = repository(devBackend("2026-08-01")).getContextProjection(request)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify(exactly = 1) { api.getContextProjection(request) }
    }

    @Test
    fun `a tagged 0-8-8 server skips the call regardless of date`() = runTest {
        val detected = DetectedBackend("0.8.8-rc1", BackendBuildClass.RC, "2026-08-01")

        val result = repository(detected).getContextProjection(request)

        assertThat((result as Result.Success).data).isNull()
        coVerify(exactly = 0) { api.getContextProjection(any()) }
    }

    @Test
    fun `an unresolved server still issues the call`() = runTest {
        // supportsFeature fails closed on null, so the suppression does not apply. The gauge is
        // separately off in that case (ChatViewModel requires a resolved version), so this path
        // is only reachable if that gate is ever relaxed.
        coEvery { api.getContextProjection(any()) } returns null

        val result = repository(null).getContextProjection(request)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify(exactly = 1) { api.getContextProjection(request) }
    }

    // --- token-config offline fallback ---

    private val liveConfig = mapOf("openAI" to mapOf("gpt-4o" to ModelTokenomics(context = 128_000)))
    private val savedConfig = mapOf("openAI" to mapOf("gpt-4o" to ModelTokenomics(context = 64_000)))

    @Test
    fun `a fetched token-config is saved for this server`() = runTest {
        coEvery { api.getTokenConfig() } returns liveConfig

        val result = repository(null).getTokenConfig()

        assertThat((result as Result.Success).data).isEqualTo(liveConfig)
        coVerify(exactly = 1) { configCache.saveTokenConfig(liveConfig) }
    }

    @Test
    fun `offline, the saved token-config stands in for the network`() = runTest {
        coEvery { api.getTokenConfig() } throws RuntimeException("offline")
        coEvery { configCache.loadTokenConfig() } returns savedConfig

        val result = repository(null).getTokenConfig()

        assertThat((result as Result.Success).data).isEqualTo(savedConfig)
    }

    @Test
    fun `the saved fallback is not memoized, so the network is retried next time`() = runTest {
        coEvery { api.getTokenConfig() } throws RuntimeException("offline") andThen liveConfig
        coEvery { configCache.loadTokenConfig() } returns savedConfig
        val repository = repository(null)

        repository.getTokenConfig()
        val second = repository.getTokenConfig()

        assertThat((second as Result.Success).data).isEqualTo(liveConfig)
        coVerify(exactly = 2) { api.getTokenConfig() }
    }

    @Test
    fun `with neither network nor a saved copy the error comes through`() = runTest {
        coEvery { api.getTokenConfig() } throws RuntimeException("offline")
        coEvery { configCache.loadTokenConfig() } returns null

        assertThat(repository(null).getTokenConfig()).isInstanceOf(Result.Error::class.java)
    }

    // --- instruction overhead cache ---

    @Test
    fun `overheads are kept per config and cleared with the session`() = runTest {
        val repository = repository(null)

        repository.recordContextOverhead(contextOverheadKey("agents", "agent_a", "agent_a"), 4_000)
        repository.recordContextOverhead(contextOverheadKey("openAI", "gpt-4o", null), 2_500)
        repository.recordContextOverhead(contextOverheadKey("openAI", "gpt-4o-mini", null), 0)

        assertThat(repository.contextOverhead("agent:agent_a")).isEqualTo(4_000)
        assertThat(repository.contextOverhead("openAI::gpt-4o")).isEqualTo(2_500)
        assertThat(repository.contextOverhead("openAI::gpt-4o-mini")).isEqualTo(0)

        repository.clear()

        assertThat(repository.contextOverhead("agent:agent_a")).isEqualTo(0)
    }
}
