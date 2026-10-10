package com.garfiec.librechat.feature.chat.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import com.garfiec.librechat.feature.chat.viewmodel.ChatViewModel
import kotlinx.coroutines.CoroutineScope

/**
 * Builds the composer's "start recording" action. Android routes old OS versions through the
 * system recognizer overlay; iOS always defers to [ChatViewModel.startRecording].
 */
@Suppress("ViewModelForwarding") // debt: screen split across files; state not hoisted yet
@Composable
internal expect fun rememberChatStartRecording(
    viewModel: ChatViewModel,
    sttEngine: String,
    sttLanguage: String,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
): () -> Unit
