package com.garfiec.librechat.core.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Where Liquid Glass overlays are drawn, as a portal. They can't use a window (it can't sample the
 * content behind it), and composed in place they'd be clipped to their parent.
 *
 * Null outside a host, where overlays fall back to their opaque M3 window; see also
 * `LocalInSeparateWindow`.
 */
internal val LocalGlassSheetHost = staticCompositionLocalOf<GlassSheetHostState?> { null }

@Stable
internal class GlassSheetHostState {
    internal val overlays = mutableStateListOf<GlassOverlayEntry>()
}

@Stable
internal class GlassOverlayEntry {
    var locals: CompositionLocalContext? by mutableStateOf(null)
    var overlay: @Composable () -> Unit by mutableStateOf({})
}

/** Records [content] as the overlays' backdrop only while one is open, so it's free the rest of the time. */
@Composable
fun GlassSheetHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = remember { GlassSheetHostState() }
    val backdrop = rememberGlassBackdrop()
    CompositionLocalProvider(LocalGlassSheetHost provides state) {
        Box(modifier, propagateMinConstraints = true) {
            GlassBackdropRecorder(backdrop, recording = state.overlays.isNotEmpty(), content = content)
            // In opening order, so a menu opened from a sheet stacks above it.
            state.overlays.forEach { entry ->
                key(entry) {
                    // A null check, not `?: return@key`: a labelled return out of the inline key()
                    // compiles to a method name D8 cannot dex.
                    val locals = entry.locals
                    if (locals != null) {
                        CompositionLocalProvider(locals) {
                            // Inside the opener's locals, so the app-wide backdrop wins over any the
                            // opener's screen provides for its own chrome.
                            CompositionLocalProvider(LocalGlassBackdrop provides backdrop, content = entry.overlay)
                        }
                    }
                }
            }
        }
    }
}

/** Draws [overlay] in [host] while this call is composed, under the locals captured here. */
@Composable
internal fun GlassPortal(host: GlassSheetHostState, overlay: @Composable () -> Unit) {
    val entry = remember { GlassOverlayEntry() }
    // Updated on every recomposition: the locals object changes whenever any local does. The host
    // draws its overlays after the content, so these writes always precede its read in a frame.
    entry.locals = currentCompositionLocalContext
    entry.overlay = overlay
    DisposableEffect(host, entry) {
        host.overlays.add(entry)
        onDispose { host.overlays.remove(entry) }
    }
}
