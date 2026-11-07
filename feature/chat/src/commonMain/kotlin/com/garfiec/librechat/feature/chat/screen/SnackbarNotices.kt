package com.garfiec.librechat.feature.chat.screen

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.action_undo
import org.jetbrains.compose.resources.getString

/** Shows [message] in place of whatever snackbar is up, so a newer notice never waits behind an older one. */
internal suspend fun SnackbarHostState.showNotice(message: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(message)
}

/** Shows [message] with an Undo, in place of whatever snackbar is up. True when Undo was tapped. */
internal suspend fun SnackbarHostState.showUndo(message: String): Boolean {
    currentSnackbarData?.dismiss()
    val result = showSnackbar(
        message = message,
        actionLabel = getString(Res.string.action_undo),
        duration = SnackbarDuration.Short,
    )
    return result == SnackbarResult.ActionPerformed
}
