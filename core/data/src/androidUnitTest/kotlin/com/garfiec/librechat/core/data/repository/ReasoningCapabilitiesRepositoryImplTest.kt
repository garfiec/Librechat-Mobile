package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.reasoning.ModelReasoning
import com.garfiec.librechat.core.model.reasoning.ReasoningCapabilitiesResponse
import com.garfiec.librechat.core.network.api.EndpointTokenApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ReasoningCapabilitiesRepositoryImplTest {

    private val api = mockk<EndpointTokenApi>()
    private val configRepository = mockk<ConfigRepository>()
    private val accounts = InMemoryActiveAccountProvider().apply { set(AccountId("a1")) }
    private var now = 1_000L

    private val response = ReasoningCapabilitiesResponse(
        capabilities = mapOf("OpenRouter" to mapOf("openai/gpt-5" to ModelReasoning(listOf("low", "high")))),
        expiresInMs = 60_000,
    )

    private fun repository(version: String?): ReasoningCapabilitiesRepository {
        every { configRepository.detectedBackendVersion } returns MutableStateFlow(version)
        return ReasoningCapabilitiesRepositoryImpl(api, configRepository, accounts) { now }
    }

    @Test
    fun `an older or unknown server is never asked`() = runTest {
        assertThat(repository("0.8.8").catalog("OpenRouter")).isNull()
        assertThat(repository(null).catalog("OpenRouter")).isNull()
        coVerify(exactly = 0) { api.getReasoningCapabilities(any()) }
    }

    @Test
    fun `a catalog is reused until the server's lifetime runs out`() = runTest {
        coEvery { api.getReasoningCapabilities("OpenRouter") } returns response
        val repository = repository("0.8.9-rc1")

        val first = repository.catalog("OpenRouter") as Result.Success
        assertThat(first.data.models.keys).containsExactly("openai/gpt-5")
        assertThat(first.data.expiresAtMs).isEqualTo(61_000L)
        now = 60_999
        repository.catalog("OpenRouter")
        coVerify(exactly = 1) { api.getReasoningCapabilities("OpenRouter") }

        now = 61_000
        repository.catalog("OpenRouter")
        coVerify(exactly = 2) { api.getReasoningCapabilities("OpenRouter") }
    }

    @Test
    fun `a failure is not remembered`() = runTest {
        coEvery { api.getReasoningCapabilities("OpenRouter") } throws IllegalStateException("503") andThen response
        val repository = repository("0.8.9")

        assertThat(repository.catalog("OpenRouter")).isInstanceOf(Result.Error::class.java)
        assertThat(repository.catalog("OpenRouter")).isInstanceOf(Result.Success::class.java)
        coVerify(exactly = 2) { api.getReasoningCapabilities("OpenRouter") }
    }

    @Test
    fun `another account asks again`() = runTest {
        coEvery { api.getReasoningCapabilities("OpenRouter") } returns response
        val repository = repository("0.8.9-rc1")

        repository.catalog("OpenRouter")
        accounts.set(AccountId("a2"))
        repository.catalog("OpenRouter")

        coVerify(exactly = 2) { api.getReasoningCapabilities("OpenRouter") }
    }
}
