package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity

/**
 * iOS has no back swipe on the keyboard, so the inset alone says up or down. The inset moves every
 * frame of a keyboard animation; derived, only the up/down flip recomposes the composer.
 */
@Composable
internal actual fun keyboardRising(): Boolean {
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    val up by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
    return up
}
