package com.garfiec.librechat.feature.settings.screen.sections

import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties

/**
 * The 2FA setup dialog's window. On Android it must not pan for the keyboard, or content that grows
 * above the focused code field lifts the title under the status bar; it pads for the system bars
 * and the keyboard itself instead ([twoFactorDialogInsets]).
 */
internal expect fun twoFactorDialogProperties(): DialogProperties

/** The insets the 2FA setup dialog's root pads for: the keyboard, and on Android the system bars. */
internal expect fun Modifier.twoFactorDialogInsets(): Modifier
