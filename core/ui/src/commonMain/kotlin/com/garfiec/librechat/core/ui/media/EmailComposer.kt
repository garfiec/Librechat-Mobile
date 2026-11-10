package com.garfiec.librechat.core.ui.media

import androidx.compose.runtime.Composable

/** A plain-text email for the user's own mail app to send. */
data class EmailDraft(
    val to: String,
    val subject: String,
    val body: String,
    val attachment: EmailAttachment?,
)

/** A file attached to an [EmailDraft] or shared with text; the platform writes [bytes] off Main. */
class EmailAttachment(val fileName: String, val mimeType: String, val bytes: ByteArray)

/**
 * How handing a draft to the mail app ended. Android can't see what happens in the mail app, so it
 * reports only [Opened] or [NoMailApp]; iOS reports [Sent] or [Cancelled] (a saved draft or a failed
 * send counts as cancelled) when the composer closes.
 */
enum class EmailComposeResult { Opened, Sent, Cancelled, NoMailApp }

/**
 * Returns a handler that opens [EmailDraft] in a mail app: on Android a send limited to apps that
 * take `mailto:` (the system asks which when there are several), on iOS Apple's mail composer.
 * Nothing is sent until the user sends it there.
 */
@Composable
expect fun rememberEmailComposer(): (draft: EmailDraft, onResult: (EmailComposeResult) -> Unit) -> Unit
