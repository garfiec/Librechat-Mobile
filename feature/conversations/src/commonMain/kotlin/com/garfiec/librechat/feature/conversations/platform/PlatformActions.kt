package com.garfiec.librechat.feature.conversations.platform

import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.ui.platform.TextFileSaver

/**
 * Copy text to the system clipboard.
 */
expect fun copyToClipboard(text: String, label: String = "")

/**
 * Show a short toast/notification to the user.
 */
expect fun showToast(message: String)

/**
 * Saves an exported conversation through the shared [TextFileSaver]; see it for the callback
 * contract.
 *
 * Also the reason [copyToClipboard] and [showToast] work on Android: they need a Context and are
 * called from effects that have none, so composing this registers one.
 */
@Composable
fun FileSaver(
    triggerFileName: String?,
    content: String?,
    onComplete: (success: Boolean, message: String?) -> Unit,
    onReset: () -> Unit,
) {
    RegisterPlatformActions()
    TextFileSaver(triggerFileName, content, onComplete, onReset, mimeType = "*/*")
}

/** Android: hands [copyToClipboard] and [showToast] the application Context. iOS: nothing to do. */
@Composable
internal expect fun RegisterPlatformActions()
