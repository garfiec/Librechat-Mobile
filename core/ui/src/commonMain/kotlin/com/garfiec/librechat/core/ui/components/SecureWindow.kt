package com.garfiec.librechat.core.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

/**
 * While composed with [secure] true, keeps the window this composition draws in out of screenshots,
 * screen recording and the recents snapshot (Android `FLAG_SECURE` on the host activity's window).
 * For content drawn in the activity window itself, like the glass sheet in `GlassSheetHost`; a
 * dialog window gets its own flag instead ([secureSheetProperties]). A no-op on iOS, which has no
 * equivalent: callers re-mask secrets on ON_PAUSE instead, which iOS sends while it still draws
 * (ON_STOP comes too late for the app-switcher snapshot).
 */
@Composable
internal expect fun SecureWindowEffect(secure: Boolean)

/** The M3 sheet's window properties, secure (Android `SecureFlagPolicy.SecureOn`) when [secure]. */
@OptIn(ExperimentalMaterial3Api::class)
internal expect fun secureSheetProperties(secure: Boolean): ModalBottomSheetProperties

/**
 * [secure], held on for [SECURE_LINGER_FRAMES] frames after it turns off. The state change that
 * hides a secret (a re-mask on ON_PAUSE) is also the one that turns [secure] off, and dropping the
 * window flag at once would leave the last revealed frame on screen, in a window that is no longer
 * secure, until the masked one is drawn and shown. A screen recording or the recents snapshot
 * would catch it there. Frames that never come (the app is in the background) keep it on.
 */
@Composable
internal fun rememberLingeringSecure(secure: Boolean): Boolean {
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(secure) {
        if (secure) {
            held = true
        } else if (held) {
            repeat(SECURE_LINGER_FRAMES) { withFrameNanos { } }
            held = false
        }
    }
    return secure || held
}

/** The frame that draws the masked content, and the one that puts it on screen. */
internal const val SECURE_LINGER_FRAMES = 2

/** A window flag [SecureFlagRegistry] turns on and off. */
internal interface SecureFlagTarget {
    var isSecure: Boolean
}

/**
 * Ref-counts the secure flag per window: several holders can want one window secure at once (two
 * secure sheets during a hand-off). A window's first [acquire] sets the flag, its last [release]
 * clears it, and only if that round was the one that set it: a flag someone else had already set
 * stays on. An entry lives only while held, since it refers to its window and a kept one would keep
 * the window (and its activity) alive after the activity is gone. Main-thread only.
 */
internal class SecureFlagRegistry<K : Any>(private val targetFor: (K) -> SecureFlagTarget) {
    private class Hold(val target: SecureFlagTarget, val setByUs: Boolean) {
        var holders = 1
    }

    private val holds = mutableMapOf<K, Hold>()

    /** How many windows are held now. */
    val size: Int get() = holds.size

    fun acquire(key: K) {
        holds[key]?.let {
            it.holders++
            return
        }
        val target = targetFor(key)
        val setByUs = !target.isSecure
        if (setByUs) target.isSecure = true
        holds[key] = Hold(target, setByUs)
    }

    fun release(key: K) {
        val hold = holds[key] ?: return
        if (--hold.holders > 0) return
        holds.remove(key)
        if (hold.setByUs) hold.target.isSecure = false
    }
}
