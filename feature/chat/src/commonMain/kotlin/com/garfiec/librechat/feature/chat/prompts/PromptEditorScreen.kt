package com.garfiec.librechat.feature.chat.prompts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.chat.resources.*
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun PromptEditorScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    groupId: String? = null,
    viewModel: PromptEditorViewModel = koinViewModel { parametersOf(groupId) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actions: PromptEditorActions = viewModel

    PromptEditorContent(
        uiState = uiState,
        commandState = viewModel.commandState,
        actions = actions,
        onBack = onBack,
        modifier = modifier,
    )
}
