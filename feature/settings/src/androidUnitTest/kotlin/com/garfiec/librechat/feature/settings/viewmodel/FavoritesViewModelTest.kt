package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.FavoritesRepository
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.UserFavorite
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.config.StartupConfig
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val pinned = listOf(
        UserFavorite(agentId = "a1"),
        UserFavorite(agentId = "gone_agent"),
        UserFavorite(endpoint = "anthropic", model = "claude-sonnet-5-5"),
        UserFavorite(spec = "writer"),
        UserFavorite(spec = "removed"),
    )
    private val favorites = MutableStateFlow(pinned)
    private val favoritesRepository = mockk<FavoritesRepository> {
        every { favorites } returns this@FavoritesViewModelTest.favorites
        coEvery { refresh() } returns Result.Success(pinned)
        coEvery { setFavorites(any()) } answers { Result.Success(firstArg()) }
    }
    private val agentRepository = mockk<AgentRepository> {
        coEvery { getAgents(any()) } returns Result.Success(listOf(Agent(id = "a1", name = "Research Bot")))
        coEvery { getAgent("gone_agent") } returns Result.Error(message = "not found")
    }
    private val configRepository = mockk<ConfigRepository> {
        every { startupConfig } returns MutableStateFlow(
            StartupConfig(modelSpecs = ModelSpecs(list = listOf(ModelSpec(name = "writer", label = "Writer Pro")))),
        )
        every { endpointConfigs } returns MutableStateFlow(mapOf("anthropic" to EndpointConfig()))
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = FavoritesViewModel(favoritesRepository, agentRepository, configRepository)

    @Test
    fun `rows show names the user knows, falling back to the stored id`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.favorites.map { Triple(it.label, it.subtitle, it.kind) })
            .containsExactly(
                Triple("Research Bot", "", FavoriteKind.AGENT),
                Triple("gone_agent", "", FavoriteKind.AGENT),
                Triple("claude-sonnet-5-5", "Anthropic", FavoriteKind.MODEL),
                Triple("Writer Pro", "", FavoriteKind.SPEC),
                Triple("removed", "", FavoriteKind.SPEC),
            )
            .inOrder()
    }

    @Test
    fun `removing a named agent row writes the list without it`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.remove("agent::a1")
        advanceUntilIdle()

        coVerify { favoritesRepository.setFavorites(pinned.drop(1)) }
    }
}
