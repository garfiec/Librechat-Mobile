package com.garfiec.librechat.feature.settings.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.garfiec.librechat.core.ui.platform.TextFileSaver
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.logs_exported
import com.garfiec.librechat.feature.settings.viewmodel.LogsExportPayload
import org.jetbrains.compose.resources.stringResource

/**
 * Hands an exported diagnostic log to the platform file saver once the ViewModel has read it, and
 * reports how the save went in [snackbarHostState].
 */
@Composable
internal fun DiagnosticLogsSaver(
    payload: LogsExportPayload?,
    onConsume: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val currentOnConsume by rememberUpdatedState(onConsume)
    var pendingFileName by remember { mutableStateOf<String?>(null) }
    var pendingContent by remember { mutableStateOf<String?>(null) }
    var saverResult by remember { mutableStateOf<String?>(null) }
    val exportedMsg = stringResource(Res.string.logs_exported)

    LaunchedEffect(payload) {
        payload ?: return@LaunchedEffect
        pendingContent = payload.content
        pendingFileName = payload.fileName
        currentOnConsume()
    }
    TextFileSaver(
        triggerFileName = pendingFileName,
        content = pendingContent,
        onComplete = { success, errorMessage ->
            if (success) {
                saverResult = exportedMsg
            } else if (errorMessage != null) {
                saverResult = errorMessage
            }
        },
        onReset = {
            pendingFileName = null
            pendingContent = null
        },
    )
    LaunchedEffect(saverResult) {
        val message = saverResult ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = message)
        saverResult = null
    }
}
