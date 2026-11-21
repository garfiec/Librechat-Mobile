package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.viewmodel.BackgroundTasksViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun rememberBackgroundTasks(
    conversationId: String?,
    isStreaming: Boolean,
    viewModel: BackgroundTasksViewModel = koinViewModel(),
): BackgroundTasksState {
    LaunchedEffect(conversationId, isStreaming) { viewModel.bind(conversationId, isStreaming) }
    LifecycleStartEffect(viewModel) {
        viewModel.setActive(true)
        onStopOrDispose { viewModel.setActive(false) }
    }
    val ui = viewModel.uiState.collectAsStateWithLifecycle()
    val sheet = rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sheet.value) { viewModel.setSheetOpen(sheet.value) }
    return remember(viewModel, conversationId, ui, sheet) {
        BackgroundTasksState(viewModel::stop, viewModel::stopAll, conversationId, ui, sheet)
    }
}
