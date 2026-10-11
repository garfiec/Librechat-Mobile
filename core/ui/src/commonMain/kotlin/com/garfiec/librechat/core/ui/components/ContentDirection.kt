package com.garfiec.librechat.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/*
 * TCHAT: direction follows content, as web's `dir="auto"` does. A Persian or Arabic message in an
 * English UI lays out right-to-left (list markers, quote bars, alignment); an English message in a
 * Persian UI lays out left-to-right. Paragraph direction is handled separately by the type scale
 * (`TextDirection.Content`); this covers the block layout around the text.
 */

/**
 * The direction of [text]'s first strong character, or null when it has none (digits,
 * punctuation, emoji, markdown syntax only). Right-to-left means the Hebrew and Arabic-script
 * blocks (Persian, Arabic, Urdu, Hebrew, Syriac, Thaana, N'Ko and their presentation forms); any
 * other letter is left-to-right.
 */
fun firstStrongDirection(text: CharSequence): LayoutDirection? {
    for (ch in text) {
        if (ch.isRightToLeftLetter()) return LayoutDirection.Rtl
        if (ch.isLetter()) return LayoutDirection.Ltr
    }
    return null
}

private fun Char.isRightToLeftLetter(): Boolean {
    val c = code
    return c in 0x0590..0x08FF || c in 0xFB1D..0xFDFF || c in 0xFE70..0xFEFF
}

/** Lays out [content] in [text]'s own direction, falling back to the surrounding one. */
@Composable
fun ProvideContentDirection(text: CharSequence, content: @Composable () -> Unit) {
    val surrounding = LocalLayoutDirection.current
    val direction = remember(text, surrounding) { firstStrongDirection(text) ?: surrounding }
    CompositionLocalProvider(LocalLayoutDirection provides direction, content = content)
}

/** Code, math and other inherently left-to-right content: always LTR, whatever surrounds it. */
@Composable
fun ProvideLtr(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
}
