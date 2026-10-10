package com.garfiec.librechat.core.ui.components.topbar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Android has no system glass; GlassCapability.NATIVE is never produced here, so this only guards
// against a mis-mapped tier.
@Composable
internal actual fun NativeGlassTopBar(spec: AdaptiveTopBarSpec, modifier: Modifier) {
    SimulatedGlassTopBar(spec = spec, modifier = modifier)
}
