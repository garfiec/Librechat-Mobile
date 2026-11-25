package com.garfiec.librechat.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.FavoritesRepository
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.UserFavorite
import com.garfiec.librechat.core.model.endpoint.endpointDisplayName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Manages the user's pinned-favorites list (`GET/POST /api/user/settings/favorites`, v0.8.5+).
 *
 * The Settings → Favorites sub-screen uses this to list all pinned items (agents,
 * model+endpoint combos, model specs) so users can review and unpin from one place.
 * Pinning itself happens from the picker surfaces (chat model selector) via the
 * `FavoritesDelegate` in `feature/chat`. Both paths read/write the same
 * [FavoritesRepository.favorites] flow so the two surfaces never drift apart.
 */
class FavoritesViewModel(
    private val favoritesRepository: FavoritesRepository,
    private val agentRepository: AgentRepository,
    private val configRepository: ConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    /** Agent id to name, for pinned agents the server could name. */
    private val agentNames = MutableStateFlow<Map<String, String>>(emptyMap())

    init {
        observeFavorites()
        resolveAgentNames()
        refresh()
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            combine(
                favoritesRepository.favorites,
                agentNames,
                configRepository.startupConfig,
                configRepository.endpointConfigs,
            ) { list, names, config, endpoints ->
                val specLabels = config?.modelSpecs?.list.orEmpty().associate { it.name to (it.label ?: it.name) }
                list.mapNotNull { fav -> fav.toDisplay(names, specLabels, endpoints) }
            }.collect { rows -> _uiState.update { it.copy(favorites = rows) } }
        }
    }

    /** Names pinned agents from the agent list, then by id for any it lacks; an unnamed agent keeps its id. */
    private fun resolveAgentNames() {
        viewModelScope.launch {
            favoritesRepository.favorites.collect { list ->
                val unnamed = list.mapNotNull { it.agentId?.takeIf(String::isNotBlank) }
                    .filterNot { it in agentNames.value }
                if (unnamed.isEmpty()) return@collect
                val listed = (agentRepository.getAgents() as? Result.Success)?.data.orEmpty()
                    .associate { it.id to it.name }
                val named = unnamed.mapNotNull { id ->
                    val name = if (id in listed) listed[id] else (agentRepository.getAgent(id) as? Result.Success)?.data?.name
                    name?.takeIf(String::isNotBlank)?.let { id to it }
                }
                agentNames.update { it + named }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = favoritesRepository.refresh()) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                }
                is Result.Error -> {
                    Logger.w(result.exception) { "Failed to load favorites: ${result.message}" }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.message ?: "Could not load favorites",
                        )
                    }
                }
                is Result.Loading -> Unit
            }
        }
    }

    fun remove(itemKey: String) {
        val current = favoritesRepository.favorites.value
        val updated = current.filter { it.toDisplay()?.itemKey != itemKey }
        if (updated.size == current.size) return
        persist(updated)
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun persist(newList: List<UserFavorite>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            when (val result = favoritesRepository.setFavorites(newList)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                }
                is Result.Error -> {
                    Logger.w(result.exception) { "Failed to save favorites: ${result.message}" }
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            error = result.message ?: "Could not save favorites",
                        )
                    }
                }
                is Result.Loading -> Unit
            }
        }
    }

    /**
     * The server's XOR invariant (agentId vs model+endpoint vs spec) means we can
     * unambiguously derive the kind from which field is populated. Returns null
     * for malformed entries from older clients so the UI just skips them.
     */
    private fun UserFavorite.toDisplay(
        agentNames: Map<String, String> = emptyMap(),
        specLabels: Map<String, String> = emptyMap(),
        endpoints: Map<String, EndpointConfig> = emptyMap(),
    ): FavoriteDisplayData? {
        val a = agentId
        if (!a.isNullOrBlank()) {
            return FavoriteDisplayData(
                label = agentNames[a] ?: a,
                subtitle = "",
                kind = FavoriteKind.AGENT,
                agentId = a,
            )
        }
        val m = model
        val e = endpoint
        if (!m.isNullOrBlank() && !e.isNullOrBlank()) {
            return FavoriteDisplayData(
                label = m,
                subtitle = endpointDisplayName(e, endpoints[e]),
                kind = FavoriteKind.MODEL,
                model = m,
                endpoint = e,
            )
        }
        val s = spec
        if (!s.isNullOrBlank()) {
            return FavoriteDisplayData(
                label = specLabels[s] ?: s,
                subtitle = "",
                kind = FavoriteKind.SPEC,
                spec = s,
            )
        }
        return null
    }
}
