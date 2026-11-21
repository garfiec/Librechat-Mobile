package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.EmptyState
import com.garfiec.librechat.core.ui.components.LoadingIndicator
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.FavoriteDisplayData
import com.garfiec.librechat.feature.settings.viewmodel.FavoritesUiState
import org.jetbrains.compose.resources.stringResource

/** The pinned models and agents in [uiState], each with Unpin. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesContent(
    uiState: FavoritesUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onUnpin: (itemKey: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AdaptiveScaffold(
        modifier = modifier,
        snackbarHost = { AdaptiveSnackbarHost(snackbarHostState) },
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), onNavigateBack),
                    title = BarTitle(stringResource(Res.string.favorites_title)),
                    actions = emptyList(),
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.isLoading && uiState.favorites.isEmpty() -> LoadingIndicator()
                uiState.favorites.isEmpty() -> EmptyState(
                    title = stringResource(Res.string.favorites_empty_title),
                    description = stringResource(Res.string.favorites_empty_message),
                )
                else -> {
                    Text(
                        text = stringResource(
                            Res.string.favorites_count_of,
                            uiState.favorites.size,
                            uiState.limit,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            items = uiState.favorites,
                            key = FavoriteDisplayData::itemKey,
                        ) { favorite ->
                            FavoriteRow(
                                favorite = favorite,
                                isSaving = uiState.isSaving,
                                onUnpin = { onUnpin(favorite.itemKey) },
                            )
                            AdaptiveDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteRow(
    favorite: FavoriteDisplayData,
    isSaving: Boolean,
    onUnpin: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = favorite.label,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (favorite.subtitle.isNotBlank()) {
                Spacer(Modifier.size(2.dp))
                Text(
                    text = favorite.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onUnpin, enabled = !isSaving) {
            Icon(
                imageVector = Icons.Filled.StarBorder,
                contentDescription = stringResource(Res.string.favorites_unpin_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
