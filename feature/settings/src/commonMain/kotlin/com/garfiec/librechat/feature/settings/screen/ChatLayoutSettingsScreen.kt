package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.ChatLayoutSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Settings › Appearance › Chat appearance, in its own bar. */
@Composable
fun ChatLayoutSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatLayoutSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsPageScaffold(
        title = stringResource(Res.string.appearance_chat_appearance),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) { contentPadding ->
        ChatAppearanceContent(
            state = state,
            actions = ChatAppearanceActions(
                onLayoutStyle = viewModel::setLayoutStyle,
                onFontSize = viewModel::setFontSize,
                onHeaderContent = viewModel::setHeaderContent,
                onHeaderAlignment = viewModel::setHeaderAlignment,
                onShowBubbles = viewModel::setShowBubbles,
                onShowAvatars = viewModel::setShowAvatars,
            ),
            contentPadding = contentPadding,
        )
    }
}
