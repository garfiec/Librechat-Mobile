package com.garfiec.librechat.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.FavoritesRepository
import com.garfiec.librechat.core.model.UserFavorite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        observeFavorites()
        refresh()
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            favoritesRepository.favorites.collect { list ->
                _uiState.update { it.copy(favorites = list.mapNotNull { fav -> fav.toDisplay() }) }
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
    private fun UserFavorite.toDisplay(): FavoriteDisplayData? {
        val a = agentId
        if (!a.isNullOrBlank()) {
            return FavoriteDisplayData(
                label = "Agent",
                subtitle = a,
                kind = FavoriteKind.AGENT,
                agentId = a,
            )
        }
        val m = model
        val e = endpoint
        if (!m.isNullOrBlank() && !e.isNullOrBlank()) {
            return FavoriteDisplayData(
                label = m,
                subtitle = e,
                kind = FavoriteKind.MODEL,
                model = m,
                endpoint = e,
            )
        }
        val s = spec
        if (!s.isNullOrBlank()) {
            return FavoriteDisplayData(
                label = "Spec",
                subtitle = s,
                kind = FavoriteKind.SPEC,
                spec = s,
            )
        }
        return null
    }
}
