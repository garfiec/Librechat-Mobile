package com.garfiec.librechat.core.ui.platform

import androidx.compose.runtime.Composable

/**
 * Composable that saves a text file the user picks a destination for: the diagnostic log (Settings
 * and the sign-in screens) and conversation exports.
 *
 * When [triggerFileName] changes to a non-null value, the platform prompts the user to save
 * (Android: Storage Access Framework) or share (iOS: `UIActivityViewController`) a file with
 * that name containing [content]. [onComplete] reports `(true, null)` on success, `(false, null)`
 * when the user cancelled, and `(false, message)` on a failure — an actual must never report a
 * failure without a message, or callers will read it as a cancel. [onReset] clears the trigger
 * state so a recomposition won't re-launch the picker.
 *
 * [content] is read once, when the prompt opens; callers may drop it after that.
 *
 * [mimeType] is Android-only. Keep `application/octet-stream` for a name whose extension must
 * survive: any other type makes the picker append that type's extension.
 */
@Composable
expect fun TextFileSaver(
    triggerFileName: String?,
    content: String?,
    onComplete: (success: Boolean, message: String?) -> Unit,
    onReset: () -> Unit,
    mimeType: String = "application/octet-stream",
)
