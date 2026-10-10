package com.garfiec.librechat.core.ui.components.topbar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Structural checks on the SF Symbol names. Whether each name exists in the system symbol set can
 * only be answered on iOS; `BarIconsSymbolTest` in `:shared` iosTest does that.
 */
class BarIconsTest {

    @Test
    fun everySymbolIsANonBlankSingleToken() {
        BarIcons.all.forEach { icon ->
            assertTrue(icon.sfSymbol.isNotBlank(), "blank symbol for ${icon.vector.name}")
            assertTrue(icon.sfSymbol.none { it.isWhitespace() }, "whitespace in '${icon.sfSymbol}'")
        }
    }

    @Test
    fun noTwoIconsShareASymbol() {
        val symbols = BarIcons.all.map { it.sfSymbol }
        assertEquals(symbols.size, symbols.toSet().size, "duplicate symbols: ${symbols.groupBy { it }.filterValues { it.size > 1 }.keys}")
    }
}
