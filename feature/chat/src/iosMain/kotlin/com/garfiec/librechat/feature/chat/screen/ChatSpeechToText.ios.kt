package com.garfiec.librechat.feature.chat.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import com.garfiec.librechat.feature.chat.viewmodel.ChatViewModel
import kotlinx.coroutines.CoroutineScope

@Suppress("UnusedParameter", "ViewModelForwarding")
@Composable
internal actual fun rememberChatStartRecording(
    viewModel: ChatViewModel,
    sttEngine: String,
    sttLanguage: String,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
): () -> Unit = viewModel::startRecording
