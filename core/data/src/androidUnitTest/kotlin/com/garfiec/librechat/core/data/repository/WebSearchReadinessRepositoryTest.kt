package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.ToolAuthResult
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

class WebSearchReadinessRepositoryTest {

    private val accountA = AccountId("a")
    private val accountB = AccountId("b")
    private val config = buildJsonObject { put("searchProvider", "serper") }
    private val provider = InMemoryActiveAccountProvider(AccountState.Resolved(accountA))
    private val tools = mockk<AgentToolsRepository>()
    private val repository = WebSearchReadinessRepository(tools, provider)

    @Test
    fun `an answer is remembered for the account that asked`() = runTest {
        coEvery { tools.verifyToolAuth(ToolConstants.WEB_SEARCH) } returns Result.Success(ToolAuthResult(authenticated = true))

        assertThat(repository.check(config)?.authenticated).isTrue()

        assertThat(repository.cached(config)?.authenticated).isTrue()
        provider.set(accountB)
        assertThat(repository.cached(config)).isNull()
    }

    @Test
    fun `an answer that came back after an account switch is neither returned nor remembered`() = runTest {
        // The request went out with B's token once the switch landed, so it says nothing about A.
        coEvery { tools.verifyToolAuth(ToolConstants.WEB_SEARCH) } answers {
            provider.set(accountB)
            Result.Success(ToolAuthResult(authenticated = false))
        }

        assertThat(repository.check(config)).isNull()

        provider.set(accountA)
        assertThat(repository.cached(config)).isNull()
        provider.set(accountB)
        assertThat(repository.cached(config)).isNull()
    }

    @Test
    fun `a failed request remembers nothing`() = runTest {
        coEvery { tools.verifyToolAuth(ToolConstants.WEB_SEARCH) } returns Result.Error(message = "offline")

        assertThat(repository.check(config)).isNull()
        assertThat(repository.cached(config)).isNull()
    }

    @Test
    fun `the remembered answer says which keys the user enters`() = runTest {
        val answer = ToolAuthResult(
            authenticated = false,
            authTypes = listOf(listOf("providers", "user_provided"), listOf("rerankers", "system_defined")),
        )
        coEvery { tools.verifyToolAuth(ToolConstants.WEB_SEARCH) } returns Result.Success(answer)

        repository.check(null)

        assertThat(repository.cached(null)?.userProvidedCategories).containsExactly("providers")
    }

    @Test
    fun `after invalidate the next check asks the server again`() = runTest {
        coEvery { tools.verifyToolAuth(ToolConstants.WEB_SEARCH) } returnsMany listOf(
            Result.Success(ToolAuthResult(authenticated = false)),
            Result.Success(ToolAuthResult(authenticated = true)),
        )
        repository.check(config)

        repository.invalidate()

        assertThat(repository.cached(config)).isNull()
        assertThat(repository.check(config)?.authenticated).isTrue()
        coVerify(exactly = 2) { tools.verifyToolAuth(ToolConstants.WEB_SEARCH) }
    }

    @Test
    fun `saving keys sends them as web_search's and forgets the old answer`() = runTest {
        coEvery { tools.verifyToolAuth(ToolConstants.WEB_SEARCH) } returns Result.Success(ToolAuthResult(authenticated = false))
        coEvery { tools.installToolKey(any(), any()) } returns Result.Success(Unit)
        repository.check(config)

        val result = repository.saveKeys(mapOf("selectedProvider" to "serper", "serperApiKey" to "k"))

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify { tools.installToolKey(ToolConstants.WEB_SEARCH, mapOf("selectedProvider" to "serper", "serperApiKey" to "k")) }
        assertThat(repository.cached(config)).isNull()
    }

    @Test
    fun `removing keys uninstalls every web_search field and forgets the old answer`() = runTest {
        coEvery { tools.verifyToolAuth(ToolConstants.WEB_SEARCH) } returns Result.Success(ToolAuthResult(authenticated = true))
        coEvery { tools.removeToolKey(any(), any()) } returns Result.Success(Unit)
        repository.check(config)

        repository.removeKeys()

        coVerify { tools.removeToolKey(ToolConstants.WEB_SEARCH, emptyList()) }
        assertThat(repository.cached(config)).isNull()
    }
}
