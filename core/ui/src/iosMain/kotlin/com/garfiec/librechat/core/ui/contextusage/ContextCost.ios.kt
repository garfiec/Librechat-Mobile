package com.garfiec.librechat.core.ui.contextusage

import platform.Foundation.ISOCurrencyCodes
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle

internal actual fun currencyFractionDigits(code: String): Int? {
    // NSNumberFormatter accepts any code and prints it, so ISO-4217 membership is checked here.
    if (NSLocale.ISOCurrencyCodes.none { it == code }) return null
    val formatter = NSNumberFormatter().apply {
        numberStyle = NSNumberFormatterCurrencyStyle
        currencyCode = code
    }
    return formatter.maximumFractionDigits.toInt()
}

internal actual fun formatCurrency(
    amount: Double,
    code: String,
    minFractionDigits: Int,
    maxFractionDigits: Int,
    localeTag: String,
): String {
    val formatter = NSNumberFormatter().apply {
        numberStyle = NSNumberFormatterCurrencyStyle
        locale = NSLocale(localeIdentifier = localeTag)
        currencyCode = code
        minimumFractionDigits = minFractionDigits.toULong()
        maximumFractionDigits = maxFractionDigits.toULong()
    }
    return formatter.stringFromNumber(NSNumber(double = amount)) ?: "$code $amount"
}
