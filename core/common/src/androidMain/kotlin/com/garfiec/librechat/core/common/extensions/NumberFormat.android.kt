package com.garfiec.librechat.core.common.extensions

import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

actual fun formatLocalizedNumber(
    value: Double,
    localeTag: String,
    maxFractionDigits: Int,
    minFractionDigits: Int,
): String =
    NumberFormat.getNumberInstance(Locale.forLanguageTag(localeTag)).apply {
        // HALF_EVEN by default, which prints 12.5 as "12" where web's Math.round gives 13.
        roundingMode = RoundingMode.HALF_UP
        minimumFractionDigits = minFractionDigits
        maximumFractionDigits = maxFractionDigits
    }.format(value)
