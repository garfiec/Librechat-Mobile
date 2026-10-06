package com.garfiec.librechat.core.ui.contextusage

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

internal actual fun currencyFractionDigits(code: String): Int? {
    val currency = try {
        Currency.getInstance(code)
    } catch (_: IllegalArgumentException) {
        return null
    }
    // Pseudo-currencies (XXX, XAU) report -1; web formats them with two digits.
    return currency.defaultFractionDigits.takeIf { it >= 0 } ?: 2
}

internal actual fun formatCurrency(
    amount: Double,
    code: String,
    minFractionDigits: Int,
    maxFractionDigits: Int,
    localeTag: String,
): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag(localeTag)).apply {
    currency = Currency.getInstance(code)
    minimumFractionDigits = minFractionDigits
    maximumFractionDigits = maxFractionDigits
}.format(amount)
