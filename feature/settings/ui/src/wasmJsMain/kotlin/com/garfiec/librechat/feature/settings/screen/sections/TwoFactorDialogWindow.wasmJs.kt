package com.garfiec.librechat.feature.settings.screen.sections

import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties

internal actual fun twoFactorDialogProperties(): DialogProperties = DialogProperties()

internal actual fun Modifier.twoFactorDialogInsets(): Modifier = imePadding()
