package com.garfiec.librechat.core.common.extensions

import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSNumberFormatterRoundHalfUp

actual fun formatLocalizedNumber(
    value: Double,
    localeTag: String,
    maxFractionDigits: Int,
    minFractionDigits: Int,
): String {
    val formatter = NSNumberFormatter().apply {
        numberStyle = NSNumberFormatterDecimalStyle
        locale = NSLocale(localeIdentifier = localeTag)
        // Half-even by default, which prints 12.5 as "12" where web's Math.round gives 13.
        roundingMode = NSNumberFormatterRoundHalfUp
        minimumFractionDigits = minFractionDigits.toULong()
        maximumFractionDigits = maxFractionDigits.toULong()
    }
    return formatter.stringFromNumber(NSNumber(double = value)) ?: value.toString()
}
