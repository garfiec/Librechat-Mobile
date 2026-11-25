package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.settings.viewmodel.ContextUsageSettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Settings → Chat → Context usage: where the context gauge shows, how much of its breakdown it
 * shows (with a live preview drawn by the same card the chat uses), and when compacting is
 * suggested.
 */
@Composable
fun ContextUsageSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContextUsageSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ContextUsageSettingsContent(
        state = state,
        actions = remember(viewModel) {
            ContextUsageSettingsActions(
                onPlacement = viewModel::setPlacement,
                onPreset = viewModel::setPreset,
                onAdvanced = viewModel::setAdvanced,
                onSections = viewModel::updateSections,
                onCompactNudgeThreshold = viewModel::setCompactNudgeThreshold,
            )
        },
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}
