package com.garfiec.librechat.core.ui.platform

import androidx.compose.runtime.Composable

/**
 * True when the user has asked the system for less motion: Android's "Remove animations"
 * (animator duration scale 0), iOS's Reduce Motion. A decorative animation (a pulse, a morph)
 * should then fall back to a plain state change.
 *
 * Read once per call site; a change to the setting applies the next time that site enters the
 * composition.
 */
@Composable
expect fun rememberReduceMotion(): Boolean
