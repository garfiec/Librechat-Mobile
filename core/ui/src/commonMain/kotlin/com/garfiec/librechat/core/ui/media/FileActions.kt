package com.garfiec.librechat.core.ui.media

import androidx.compose.runtime.Composable

/**
 * Returns a handler that offers already-downloaded [bytes] to another app to open: Android's
 * `ACTION_VIEW` chooser through the app's FileProvider, iOS's "Open in…" menu
 * (`UIDocumentInteractionController`). [filename] names the file the other app sees and [mime]
 * picks the apps offered, when known. Unlike [rememberShareFile] this hands the file to an app that
 * views or edits it, not to a share target.
 */
@Composable
expect fun rememberOpenFileIn(): (bytes: ByteArray, filename: String, mime: String?) -> Unit

/**
 * Returns a handler that saves already-downloaded [bytes] where the user picks, under [filename]:
 * the Storage Access Framework save picker on Android, the Files export picker on iOS. The bytes
 * are written as they are, never re-encoded.
 */
@Composable
expect fun rememberSaveFile(): (bytes: ByteArray, filename: String, mime: String?) -> Unit
