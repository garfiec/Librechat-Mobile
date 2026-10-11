package com.garfiec.librechat.core.ui.components

import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContentDirectionTest {

    @Test
    fun persianArabicAndHebrewAreRightToLeft() {
        assertEquals(LayoutDirection.Rtl, firstStrongDirection("انتگرال رو توضیح بده"))
        assertEquals(LayoutDirection.Rtl, firstStrongDirection("مرحبا"))
        assertEquals(LayoutDirection.Rtl, firstStrongDirection("שלום"))
    }

    @Test
    fun latinIsLeftToRight() {
        assertEquals(LayoutDirection.Ltr, firstStrongDirection("Explain integrals"))
    }

    @Test
    fun markdownSyntaxDigitsAndPunctuationAreSkipped() {
        assertEquals(LayoutDirection.Rtl, firstStrongDirection("## 2. **انتگرال**"))
        assertEquals(LayoutDirection.Ltr, firstStrongDirection("- 1) `GPT` و انتگرال"))
    }

    @Test
    fun textWithNoStrongCharacterHasNoDirection() {
        assertNull(firstStrongDirection("123 — !?"))
        assertNull(firstStrongDirection(""))
    }
}
