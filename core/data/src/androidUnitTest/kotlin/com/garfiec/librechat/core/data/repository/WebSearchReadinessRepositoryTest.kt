package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.ToolAuthResult
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
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

        assertThat(repository.check(config)).isTrue()

        assertThat(repository.cached(config)).isTrue()
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
}
