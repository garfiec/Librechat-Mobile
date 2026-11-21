package com.garfiec.librechat.feature.chat.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMediaViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ConversationMediaScreen(
    conversationId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConversationMediaViewModel = koinViewModel { parametersOf(conversationId) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ConversationMediaContent(
        uiState = uiState,
        onScopeChange = viewModel::setScope,
        onOpenMedia = viewModel::openMedia,
        onCloseMedia = viewModel::closeMedia,
        downloadFileBytes = remember(viewModel) { viewModel::downloadFileBytes },
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}
