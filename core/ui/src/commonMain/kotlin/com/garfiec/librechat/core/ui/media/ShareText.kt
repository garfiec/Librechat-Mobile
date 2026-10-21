package com.garfiec.librechat.core.ui.media

import androidx.compose.runtime.Composable

/**
 * Returns a handler that hands plain [text] (a link, usually) to the system share sheet: the
 * Android chooser for `ACTION_SEND`, or iOS's `UIActivityViewController`. [subject] fills the
 * subject line where the target takes one (email); it is Android only, since UIKit has no plain
 * way to set it for a string item.
 *
 * Composable so the Android implementation can resolve a `Context` from composition.
 */
@Composable
expect fun rememberShareText(): (text: String, subject: String?) -> Unit
