package com.garfiec.librechat.core.ui.contextusage

internal actual fun currencyFractionDigits(code: String): Int? =
    if (code.length == 3 && code.all { it in 'A'..'Z' }) intlCurrencyDigits(code) else null

internal actual fun formatCurrency(
    amount: Double,
    code: String,
    minFractionDigits: Int,
    maxFractionDigits: Int,
    localeTag: String,
): String = intlFormatCurrency(amount, code, minFractionDigits, maxFractionDigits, localeTag)

@Suppress("UnusedParameter")
private fun intlCurrencyDigits(code: String): Int =
    js("new Intl.NumberFormat('en', { style: 'currency', currency: code }).resolvedOptions().maximumFractionDigits")

@Suppress("UnusedParameter", "LongParameterList")
private fun intlFormatCurrency(amount: Double, code: String, minDigits: Int, maxDigits: Int, localeTag: String): String =
    js(
        "new Intl.NumberFormat(localeTag, { style: 'currency', currency: code, " +
            "minimumFractionDigits: minDigits, maximumFractionDigits: maxDigits }).format(amount)",
    )
