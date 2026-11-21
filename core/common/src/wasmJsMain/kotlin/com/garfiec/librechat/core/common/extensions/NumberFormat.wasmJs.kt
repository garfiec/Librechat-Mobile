package com.garfiec.librechat.core.common.extensions

actual fun formatLocalizedNumber(
    value: Double,
    localeTag: String,
    maxFractionDigits: Int,
    minFractionDigits: Int,
): String = intlFormatNumber(value, localeTag, minFractionDigits, maxFractionDigits)

@Suppress("UnusedParameter")
private fun intlFormatNumber(value: Double, localeTag: String, minDigits: Int, maxDigits: Int): String =
    js(
        "new Intl.NumberFormat(localeTag, " +
            "{ minimumFractionDigits: minDigits, maximumFractionDigits: maxDigits }).format(value)",
    )
