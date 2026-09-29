package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.model.UserFavorite
import com.garfiec.librechat.core.network.api.FavoritesApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The favorites list is a process-lifetime singleton holding one server's pinned models. The
 * incoming account's refresh replaces it only on success, so without a reset on the switch a flaky
 * incoming server kept showing the outgoing account's pins.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesRepositoryAccountSwitchTest {

    private val api = mockk<FavoritesApi>()
    private val accounts = InMemoryActiveAccountProvider().apply { set(AccountId("srv:user-a")) }
    private val pinnedOnA = listOf(UserFavorite(endpoint = "anthropic", model = "claude-haiku-4-5"))

    @Test
    fun `an account switch drops the outgoing account's pins`() = runTest {
        val repo = FavoritesRepositoryImpl(api, accounts, CoroutineScope(StandardTestDispatcher(testScheduler)))
        advanceUntilIdle()
        coEvery { api.getFavorites() } returns pinnedOnA
        repo.refresh()
        assertThat(repo.favorites.value).isEqualTo(pinnedOnA)

        accounts.set(AccountId("srv:user-b"))
        advanceUntilIdle()
        // B's refresh fails: nothing of A's may remain.
        coEvery { api.getFavorites() } throws IllegalStateException("offline")
        repo.refresh()

        assertThat(repo.favorites.value).isEmpty()
    }

    /** The first resolution at cold start is not a switch: a list already loaded stays. */
    @Test
    fun `the first account resolution keeps the list`() = runTest {
        val cold = InMemoryActiveAccountProvider()
        val repo = FavoritesRepositoryImpl(api, cold, CoroutineScope(StandardTestDispatcher(testScheduler)))
        coEvery { api.getFavorites() } returns pinnedOnA
        repo.refresh()

        cold.set(AccountId("srv:user-a"))
        advanceUntilIdle()

        assertThat(repo.favorites.value).isEqualTo(pinnedOnA)
    }
}
