package com.garfiec.librechat.core.ui.media

import androidx.compose.runtime.Composable

// The browser build shares, saves and mails nothing: each action is a no-op.

@Composable
actual fun rememberOpenFileIn(): (bytes: ByteArray, filename: String, mime: String?) -> Unit = { _, _, _ -> }

@Composable
actual fun rememberSaveFile(): (bytes: ByteArray, filename: String, mime: String?) -> Unit = { _, _, _ -> }

@Composable
actual fun rememberEmailComposer(): (draft: EmailDraft, onResult: (EmailComposeResult) -> Unit) -> Unit =
    { _, onResult -> onResult(EmailComposeResult.NoMailApp) }

internal actual fun preparePlatformAvatarImage(bytes: ByteArray): PreparedImage? = null

@Composable
actual fun rememberShareText(): (text: String, subject: String?) -> Unit = { _, _ -> }

@Composable
actual fun rememberShareTextWithFile(): (text: String, subject: String?, attachment: EmailAttachment?) -> Unit =
    { _, _, _ -> }

@Composable
actual fun rememberSaveImageToGallery(): ((url: String) -> Unit)? = null

@Composable
actual fun rememberShareImage(): (url: String) -> Unit = { }

@Composable
actual fun rememberShareFile(): (bytes: ByteArray, filename: String, mime: String?) -> Unit = { _, _, _ -> }
