package com.garfiec.librechat.core.ui.components.share

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.UriHandler

/**
 * Opens one of the active account's shared links in the in-app viewer, by its `shareId`. Provided
 * by the app shell, which owns navigation; feature screens (the share sheet's Preview, the Shared
 * chats page) call it without knowing the viewer's route. Null outside the shell (previews), where
 * the entry points that need it hide.
 */
val LocalOpenSharedChat = staticCompositionLocalOf<((shareId: String) -> Unit)?> { null }

/**
 * The platform's own [UriHandler]: always the browser, never the in-app viewer.
 *
 * The app shell routes `LocalUriHandler` taps on a signed-in server's `/share/` links to the viewer.
 * The viewer's own "Open in browser" must not loop back into itself, so it opens through this one.
 * Null outside the shell, where `LocalUriHandler` is the platform's anyway.
 */
val LocalExternalUriHandler = staticCompositionLocalOf<UriHandler?> { null }
