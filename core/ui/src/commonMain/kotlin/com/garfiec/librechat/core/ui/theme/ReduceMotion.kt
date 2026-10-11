package com.garfiec.librechat.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * TCHAT: whether the system asks for reduced motion (Android: animator duration scale 0; iOS:
 * Reduce Motion). The design turns off the press bounce and the glass highlight tilt then; movement
 * that explains where something went stays, as short cross-fades.
 */
@Composable
expect fun rememberSystemReduceMotion(): Boolean

/** [rememberSystemReduceMotion], read once by [LibreChatTheme]. */
val LocalReduceMotion = staticCompositionLocalOf { false }
