package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.FavoritesRepository
import com.garfiec.librechat.core.model.FavoritesLimits
import com.garfiec.librechat.core.model.UserFavorite
import com.garfiec.librechat.feature.chat.viewmodel.ChatNotice
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.FavoritesHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FavoritesDelegateTest {

    /** Writes land at once, as the real repository's optimistic publish does. */
    private class FakeFavoritesRepository(initial: List<UserFavorite> = emptyList()) : FavoritesRepository {
        override val favorites = MutableStateFlow(initial)
        val writes = mutableListOf<List<UserFavorite>>()
        override suspend fun refresh(): Result<List<UserFavorite>> = Result.Success(favorites.value)
        override suspend fun setFavorites(list: List<UserFavorite>): Result<List<UserFavorite>> {
            writes += list
            favorites.value = list
            return Result.Success(list)
        }
    }

    // Unconfined runs each launch eagerly, so a toggle has published by the time it returns.
    private val stateFlow = MutableStateFlow(ChatUiState())
    private val handle = FavoritesHandle(ChatStateHandle(stateFlow, CoroutineScope(Dispatchers.Unconfined)))
    private val state: StateFlow<ChatUiState> get() = stateFlow

    private fun delegate(repository: FavoritesRepository) = FavoritesDelegate(handle, repository).also { it.load() }

    @Test
    fun toggleSpecPinsAndUnpinsByName() {
        val repository = FakeFavoritesRepository()
        val delegate = delegate(repository)

        delegate.toggleSpec("writer")
        assertEquals(listOf(UserFavorite(spec = "writer")), repository.favorites.value)
        assertEquals(setOf("writer"), state.value.favoriteSpecNames)

        delegate.toggleSpec("writer")
        assertEquals(emptyList(), repository.favorites.value)
        assertEquals(emptySet(), state.value.favoriteSpecNames)
    }

    @Test
    fun aSpecEntryCarriesOnlyTheName() {
        val repository = FakeFavoritesRepository()

        delegate(repository).toggleSpec("writer")

        val entry = repository.writes.single().single()
        assertEquals("writer", entry.spec)
        assertNull(entry.endpoint)
        assertNull(entry.model)
        assertNull(entry.agentId)
    }

    @Test
    fun unpinningASpecLeavesOtherKindsAlone() {
        val repository = FakeFavoritesRepository(
            listOf(UserFavorite(agentId = "writer"), UserFavorite(spec = "writer"), UserFavorite(endpoint = "openAI", model = "writer")),
        )

        delegate(repository).toggleSpec("writer")

        assertEquals(
            listOf(UserFavorite(agentId = "writer"), UserFavorite(endpoint = "openAI", model = "writer")),
            repository.favorites.value,
        )
    }

    @Test
    fun theLimitCountsEveryKind() {
        val full = List(FavoritesLimits.MAX_FAVORITES) { i ->
            when (i % 3) {
                0 -> UserFavorite(agentId = "agent_$i")
                1 -> UserFavorite(endpoint = "openAI", model = "model_$i")
                else -> UserFavorite(spec = "spec_$i")
            }
        }
        val repository = FakeFavoritesRepository(full)

        delegate(repository).toggleSpec("one-more")

        assertEquals(emptyList(), repository.writes)
        assertEquals(ChatNotice.FAVORITES_LIMIT.marker(FavoritesLimits.MAX_FAVORITES), state.value.error)
    }

    @Test
    fun unpinningAtTheLimitIsAllowed() {
        val full = List(FavoritesLimits.MAX_FAVORITES) { UserFavorite(spec = "spec_$it") }
        val repository = FakeFavoritesRepository(full)

        delegate(repository).toggleSpec("spec_0")

        assertEquals(FavoritesLimits.MAX_FAVORITES - 1, repository.favorites.value.size)
        assertNull(state.value.error)
    }

    @Test
    fun publishKeepsPinOrderPerKind() {
        val repository = FakeFavoritesRepository(
            listOf(
                UserFavorite(spec = "zeta"),
                UserFavorite(agentId = "agent_b"),
                UserFavorite(endpoint = "openAI", model = "gpt-5"),
                UserFavorite(spec = "alpha"),
                UserFavorite(agentId = "agent_a"),
                UserFavorite(spec = "mid"),
            ),
        )

        delegate(repository)

        assertEquals(listOf("zeta", "alpha", "mid"), state.value.favoriteSpecNames.toList())
        assertEquals(listOf("agent_b", "agent_a"), state.value.favoriteAgentIds.toList())
        assertEquals(listOf(FavoritesDelegate.favoriteModelKey("openAI", "gpt-5")), state.value.favoriteModelKeys.toList())
    }

    @Test
    fun aNewSpecPinGoesLast() {
        val repository = FakeFavoritesRepository(listOf(UserFavorite(spec = "first")))

        delegate(repository).toggleSpec("second")

        assertEquals(listOf("first", "second"), state.value.favoriteSpecNames.toList())
    }
}
