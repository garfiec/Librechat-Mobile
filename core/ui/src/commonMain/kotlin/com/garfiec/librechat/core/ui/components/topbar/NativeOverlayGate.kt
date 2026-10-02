package com.garfiec.librechat.core.ui.components.topbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.garfiec.librechat.core.model.ui.GlassCapability
import com.garfiec.librechat.core.ui.theme.LocalGlassLevel

/**
 * Native glass bars draw *above* the whole Compose canvas, so a Compose surface meant to cover them
 * (drawer, sheet or dialog scrim) lands underneath. Such surfaces register here and the bars hide.
 */
@Stable
class NativeOverlayGate {
    private var covering by mutableIntStateOf(0)

    val suppressed: Boolean get() = covering > 0

    internal fun acquire() {
        covering++
    }

    internal fun release() {
        covering--
    }
}

val LocalNativeOverlayGate = staticCompositionLocalOf { NativeOverlayGate() }

/**
 * Hides native bars while [active]. The adaptive sheet and dialogs call this themselves; only a
 * surface drawn some other way (the drawer, a full-screen overlay) needs to.
 */
@Composable
fun CoversNativeBars(active: Boolean = true) {
    if (LocalGlassLevel.current != GlassCapability.NATIVE) return
    val gate = LocalNativeOverlayGate.current
    DisposableEffect(gate, active) {
        if (active) gate.acquire()
        onDispose { if (active) gate.release() }
    }
}
