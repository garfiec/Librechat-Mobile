package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.data.datastore.ConfigCacheDataStore
import com.garfiec.librechat.core.network.api.SearchApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

class SearchCapabilityRepositoryTest {

    private val accountA = AccountId("srvA:user")
    private val accountB = AccountId("srvB:user")
    private val api = mockk<SearchApi>()
    private val cache = mockk<ConfigCacheDataStore>(relaxed = true)
    private val accounts = InMemoryActiveAccountProvider(AccountState.Resolved(accountA))
    private val repository = SearchCapabilityRepositoryImpl(api, cache, accounts)

    @Test
    fun `unknown until probed`() = runTest {
        assertThat(repository.serverSearchEnabled.first()).isNull()
    }

    @Test
    fun `a probe publishes and caches the answer`() = runTest {
        coEvery { cache.loadSearchEnabled() } returns null
        coEvery { api.isSearchEnabled() } returns true

        repository.refresh()

        assertThat(repository.serverSearchEnabled.first()).isTrue()
        coVerify { cache.saveSearchEnabled(true) }
    }

    @Test
    fun `a failed probe keeps the cached answer`() = runTest {
        coEvery { cache.loadSearchEnabled() } returns true
        coEvery { api.isSearchEnabled() } throws IOException("offline")

        repository.refresh()

        assertThat(repository.serverSearchEnabled.first()).isTrue()
    }

    @Test
    fun `an unreachable server with nothing cached stays unknown`() = runTest {
        coEvery { cache.loadSearchEnabled() } returns null
        coEvery { api.isSearchEnabled() } throws IOException("offline")

        repository.refresh()

        assertThat(repository.serverSearchEnabled.first()).isNull()
    }

    @Test
    fun `a server that fails the probe with nothing cached reads as disabled`() = runTest {
        coEvery { cache.loadSearchEnabled() } returns null
        coEvery { api.isSearchEnabled() } throws ApiException(statusCode = 500, message = "boom")

        repository.refresh()

        assertThat(repository.serverSearchEnabled.first()).isFalse()
    }

    @Test
    fun `a probe once the connection is back replaces the unknown answer`() = runTest {
        coEvery { cache.loadSearchEnabled() } returns null
        coEvery { api.isSearchEnabled() } throws IOException("offline")
        repository.refresh()

        coEvery { api.isSearchEnabled() } returns true
        repository.refresh()

        assertThat(repository.serverSearchEnabled.first()).isTrue()
    }

    @Test
    fun `an answer for the previous account reads as unknown after a switch`() = runTest {
        coEvery { cache.loadSearchEnabled() } returns null
        coEvery { api.isSearchEnabled() } returns true
        repository.refresh()

        accounts.set(accountB)

        assertThat(repository.serverSearchEnabled.first()).isNull()
    }

    @Test
    fun `a probe that lands after a switch is dropped`() = runTest {
        val gate = CompletableDeferred<Boolean>()
        coEvery { cache.loadSearchEnabled() } returns null
        coEvery { api.isSearchEnabled() } coAnswers { gate.await() }
        val probe = launch { repository.refresh() }
        testScheduler.runCurrent()

        accounts.set(accountB)
        gate.complete(true)
        probe.join()

        assertThat(repository.serverSearchEnabled.first()).isNull()
        coVerify(exactly = 0) { cache.saveSearchEnabled(any()) }
    }
}
