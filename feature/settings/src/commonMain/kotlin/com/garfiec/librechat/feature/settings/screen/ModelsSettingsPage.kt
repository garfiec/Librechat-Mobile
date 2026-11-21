package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.settings.viewmodel.AccountSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.ChatSettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Settings › Models & keys: favorites, your provider keys, and how the model picker starts and sorts. */
@Composable
fun ModelsSettingsPage(
    onNavigateToFavorites: () -> Unit,
    onNavigateToProviderKeys: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: ChatSettingsViewModel = koinViewModel(),
    accountViewModel: AccountSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val account by accountViewModel.uiState.collectAsStateWithLifecycle()

    // The Provider Keys row's summary loads the first time this page is composed resumed (a
    // recomposed page replays ON_RESUME to its new observer), and re-reads on each resume the entry
    // itself goes through: back from Provider Keys, or from another app.
    OnResume(
        onFirst = accountViewModel::loadProviderKeysSummary,
        onReturn = accountViewModel::refreshProviderKeysSummary,
    )

    ModelsSettingsContent(
        uiState = uiState,
        account = account,
        onNavigateToFavorites = onNavigateToFavorites,
        onNavigateToProviderKeys = onNavigateToProviderKeys,
        onStarredModelsDisplayChange = viewModel::setStarredModelsDisplay,
        onPreferLastUsedModelChange = viewModel::setPreferLastUsedModel,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}
