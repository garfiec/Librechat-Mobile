package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.FavoritesLimits

@Immutable
data class FavoriteDisplayData(
    val label: String,
    val subtitle: String,
    val kind: FavoriteKind,
    val agentId: String? = null,
    val model: String? = null,
    val endpoint: String? = null,
    val spec: String? = null,
) {
    /** Stable identity for LazyColumn `key` — each variant uses a different identifier. */
    val itemKey: String = when (kind) {
        FavoriteKind.AGENT -> "agent::${agentId.orEmpty()}"
        FavoriteKind.MODEL -> "model::${endpoint.orEmpty()}::${model.orEmpty()}"
        FavoriteKind.SPEC -> "spec::${spec.orEmpty()}"
    }
}

enum class FavoriteKind { AGENT, MODEL, SPEC }

@Immutable
data class FavoritesUiState(
    val favorites: List<FavoriteDisplayData> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val limit: Int = FavoritesLimits.MAX_FAVORITES,
)
