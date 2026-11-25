package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.settings.viewmodel.AppearanceSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.ChatLayoutSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.GeneralSettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Settings › Appearance: theme, interface style, the server's colours or Switchboard's, accent and
 * wallpaper colours, how chats look, and the tablet sidebar.
 */
@Composable
fun AppearanceSettingsPage(
    onNavigateToChatLayout: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: AppearanceSettingsViewModel = koinViewModel(),
    generalViewModel: GeneralSettingsViewModel = koinViewModel(),
    chatLayoutViewModel: ChatLayoutSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val general by generalViewModel.uiState.collectAsStateWithLifecycle()
    val chatLayout by chatLayoutViewModel.uiState.collectAsStateWithLifecycle()

    AppearanceSettingsContent(
        uiState = uiState,
        general = general,
        chatLayout = chatLayout,
        actions = remember(viewModel, generalViewModel) {
            AppearanceSettingsActions(
                onThemeMode = viewModel::setThemeMode,
                onUiStyle = viewModel::setUiStyle,
                onUseServerTheme = viewModel::setUseServerTheme,
                onUseDynamicColor = viewModel::setUseDynamicColor,
                onAccentColor = viewModel::setAccentColor,
                onTabletSidebarGestureEnabled = generalViewModel::setTabletSidebarGestureEnabled,
            )
        },
        onNavigateToChatLayout = onNavigateToChatLayout,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

// A brand name: the same in every language.
