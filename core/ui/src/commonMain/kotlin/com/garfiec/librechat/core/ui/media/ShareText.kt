package com.garfiec.librechat.core.ui.media

import androidx.compose.runtime.Composable

/**
 * Returns a handler that hands plain [text] (a link, usually) to the system share sheet: the
 * Android chooser for `ACTION_SEND`, or iOS's `UIActivityViewController`. [subject] fills the
 * subject line where the target takes one (email) on Android; iOS puts it on the text's first line.
 */
@Composable
expect fun rememberShareText(): (text: String, subject: String?) -> Unit

/**
 * Returns a handler that hands [text] and, when given, an [attachment] together to the system share
 * sheet, for when the mail composer can't be opened. [subject] is handled as for [rememberShareText].
 */
@Composable
expect fun rememberShareTextWithFile(): (text: String, subject: String?, attachment: EmailAttachment?) -> Unit
