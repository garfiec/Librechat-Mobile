package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf

/**
 * A browser reports no keyboard inset, so an on-screen keyboard shows as the visual viewport losing
 * height: against the tallest the page has been at this width, so a page laid out above the keyboard
 * (`interactive-widget=resizes-content`) counts too. Only with a touch screen, so a desktop window
 * resized shorter isn't a keyboard.
 *
 * One watcher for the page, started on the first read and never stopped. [rememberComposerOpenness]
 * reads this on its first composition: a watcher started later would take the height under an open
 * keyboard for the full one.
 */
@Composable
internal actual fun keyboardRising(): Boolean {
    if (!watching) {
        watching = true
        watchSoftKeyboard { softKeyboardUp.value = it }
    }
    return softKeyboardUp.value
}

private val softKeyboardUp = mutableStateOf(false)
private var watching = false

@Suppress("UnusedParameter")
private fun watchSoftKeyboard(onChange: (Boolean) -> Unit): Unit = js(
    """{
    const viewport = window.visualViewport;
    if (!viewport || !window.matchMedia('(pointer: coarse)').matches) return;
    const tallest = new Map();
    let up = false;
    const check = () => {
        const width = Math.round(viewport.width * viewport.scale);
        const height = viewport.height * viewport.scale;
        tallest.set(width, Math.max(tallest.get(width) || 0, height));
        const now = tallest.get(width) - height > 120;
        if (now !== up) { up = now; onChange(now); }
    };
    check();
    viewport.addEventListener('resize', check);
    }""",
)
