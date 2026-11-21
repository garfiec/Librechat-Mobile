package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.settings.navigation.AdvancedSection
import com.garfiec.librechat.feature.settings.viewmodel.ChatSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.ContextUsageSettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Settings › Chats: how your own chats behave. Power settings sit behind the Advanced link. */
@Composable
fun ChatsSettingsPage(
    onNavigateToContextUsage: () -> Unit,
    onNavigateToArtifactShortcuts: () -> Unit,
    onOpenAdvancedSettings: (AdvancedSection) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: ChatSettingsViewModel = koinViewModel(),
    contextUsageViewModel: ContextUsageSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val gates by viewModel.gates.collectAsStateWithLifecycle()
    val contextUsage by contextUsageViewModel.uiState.collectAsStateWithLifecycle()
    val shortcutsSupported = rememberHomeScreenShortcutsSupported()

    ChatsSettingsContent(
        uiState = uiState,
        gates = gates,
        contextUsage = contextUsage,
        shortcutsSupported = shortcutsSupported,
        actions = remember(viewModel) {
            ChatsSettingsActions(
                onThinkingOpenByDefault = viewModel::setThinkingOpenByDefault,
                onCollapseLongUserMessages = viewModel::setCollapseLongUserMessages,
                onShowImageDescriptions = viewModel::setShowImageDescriptions,
                onDismissKeyboardOnSend = viewModel::setDismissKeyboardOnSend,
                onArtifactDisplayMode = viewModel::setArtifactDisplayMode,
            )
        },
        onNavigateToContextUsage = onNavigateToContextUsage,
        onNavigateToArtifactShortcuts = onNavigateToArtifactShortcuts,
        onOpenAdvancedSettings = onOpenAdvancedSettings,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}
