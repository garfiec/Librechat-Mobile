package com.garfiec.librechat.core.common.extensions

import kotlin.test.Test
import kotlin.test.assertEquals

class NumberFormatTest {

    @Test
    fun roundsToWholeNumberByDefault() {
        assertEquals("0", formatLocalizedNumber(0.0, "en-US"))
        assertEquals("12,346", formatLocalizedNumber(12345.678, "en-US"))
    }

    @Test
    fun roundsHalfUpLikeMathRound() {
        assertEquals("1,000", formatLocalizedNumber(999.5, "en-US"))
        assertEquals("13", formatLocalizedNumber(12.5, "en-US"))
    }

    @Test
    fun usesTheLocaleGroupingSeparator() {
        assertEquals("1,234,567", formatLocalizedNumber(1234567.4, "en-US"))
        assertEquals("1.234.567", formatLocalizedNumber(1234567.4, "de-DE"))
    }

    @Test
    fun keepsFractionDigitsUpToTheLimitAndDropsTrailingZeros() {
        assertEquals("1.5", formatLocalizedNumber(1.5, "en-US", maxFractionDigits = 2))
        assertEquals("7", formatLocalizedNumber(7.0, "en-US", maxFractionDigits = 2))
        assertEquals("1,5", formatLocalizedNumber(1.5, "de-DE", maxFractionDigits = 2))
    }

    @Test
    fun keepsTrailingZerosUpToTheMinimum() {
        assertEquals("1.0", formatLocalizedNumber(1.0, "en-US", maxFractionDigits = 1, minFractionDigits = 1))
        assertEquals("0,5", formatLocalizedNumber(0.5, "de-DE", maxFractionDigits = 1, minFractionDigits = 1))
    }
}
