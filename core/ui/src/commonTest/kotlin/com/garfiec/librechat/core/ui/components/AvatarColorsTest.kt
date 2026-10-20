package com.garfiec.librechat.core.ui.components

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class AvatarColorsTest {

    @Test
    fun every_palette_colour_takes_white_text() {
        AvatarPalette.forEach { assertEquals(Color.White, readableOn(it), "for $it") }
    }

    @Test
    fun light_backgrounds_take_black_text() {
        assertEquals(Color.Black, readableOn(Color.White))
        assertEquals(Color.Black, readableOn(Color(0xFFFFDCAE)))
    }
}
