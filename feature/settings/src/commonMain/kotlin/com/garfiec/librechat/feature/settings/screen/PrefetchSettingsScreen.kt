package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.settings.viewmodel.PrefetchActivityViewModel
import com.garfiec.librechat.feature.settings.viewmodel.PrefetchSettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Settings → Data → Background prefetch: the prefetch switches and depth, with a status summary. */
@Composable
fun PrefetchSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPrefetchActivity: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PrefetchSettingsViewModel = koinViewModel(),
    activityViewModel: PrefetchActivityViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity by activityViewModel.uiState.collectAsStateWithLifecycle()

    PrefetchSettingsContent(
        state = state,
        activity = activity,
        onNavigateBack = onNavigateBack,
        onNavigateToPrefetchActivity = onNavigateToPrefetchActivity,
        onEnabledChange = viewModel::setEnabled,
        onOnMeteredChange = viewModel::setOnMeteredEnabled,
        onAttachmentsChange = viewModel::setAttachmentsEnabled,
        onDepthChange = viewModel::setDepth,
        modifier = modifier,
    )
}
