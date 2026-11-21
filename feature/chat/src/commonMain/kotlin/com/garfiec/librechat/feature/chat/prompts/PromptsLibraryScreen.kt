package com.garfiec.librechat.feature.chat.prompts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.chat.resources.*
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PromptsLibraryScreen(
    onNavigateBack: () -> Unit,
    onUseInChat: (String) -> Unit,
    onNavigateToEditor: (groupId: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PromptsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actions: PromptsLibraryActions = viewModel

    // Keyed on the revision from inside the composition, so the reload happens when this list is on
    // screen: a user who saves three prompts in a row pays one reload on return, and a screen that
    // stays composed still reloads as soon as one lands.
    val promptLibraryRevision by viewModel.promptLibraryRevision.collectAsStateWithLifecycle()
    LaunchedEffect(promptLibraryRevision) { viewModel.refreshIfStale() }

    PromptsLibraryContent(
        uiState = uiState,
        actions = actions,
        onNavigateBack = onNavigateBack,
        onUseInChat = onUseInChat,
        onNavigateToEditor = onNavigateToEditor,
        modifier = modifier,
    )
}
