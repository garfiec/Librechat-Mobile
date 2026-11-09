package com.garfiec.librechat.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler

/**
 * KMP wrapper over Compose UI's [BackHandler]. When [enabled], intercepts the platform back gesture
 * (system back on Android, the edge swipe on iOS) and invokes [onBack]. Lives in :core:ui because the
 * `androidx.compose.ui.backhandler` package is only on that module's classpath, not exposed to
 * downstream feature/shared modules.
 *
 * While enabled it also holds a claim on [LocalBackClaims], so the shell keeps its drawer swipe off.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackClaim(enabled)
    BackHandler(enabled = enabled, onBack = onBack)
}

/** Holds a claim on [LocalBackClaims] while [enabled]; every back handler under the shell needs one. */
@Composable
internal fun BackClaim(enabled: Boolean) {
    val claims = LocalBackClaims.current
    if (enabled && claims != null) {
        DisposableEffect(claims) {
            claims.claim()
            onDispose { claims.release() }
        }
    }
}

/**
 * How many back handlers ([PlatformBackHandler], [rememberSheetPredictiveBack]) under the shell are
 * enabled. The phone shell turns the drawer's swipe off while any is: on iOS the drawer's drag
 * otherwise takes the edge swipe that means back, so a dirty editor would open the drawer (and could
 * be left through it) instead of asking to discard.
 */
@Stable
class BackClaims {
    private var count by mutableIntStateOf(0)

    /** Some screen or overlay under the shell is taking back right now. */
    val isClaimed: Boolean get() = count > 0

    internal fun claim() {
        count++
    }

    internal fun release() {
        count--
    }
}

/** The shell's [BackClaims]; null outside one, where [PlatformBackHandler] claims nothing. */
val LocalBackClaims = staticCompositionLocalOf<BackClaims?> { null }
