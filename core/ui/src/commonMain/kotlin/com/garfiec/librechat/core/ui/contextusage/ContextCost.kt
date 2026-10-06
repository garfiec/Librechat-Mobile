package com.garfiec.librechat.core.ui.contextusage

import com.garfiec.librechat.core.model.config.CurrencyConfig
import kotlin.math.pow

/*
 * Cost formatting for the context breakdown. Ported from upstream web at v0.8.8 (`formatCost` in
 * client/src/utils/tokens.ts). Platform formatting lives in the android/ios actuals.
 */

/**
 * A USD cost in the server's display currency (`interface.currency`), formatted for
 * [localeTag]. An unknown currency code falls back to USD and rate 1, so a converted amount is
 * never shown under the wrong symbol. Amounts below one minor unit read as `<$0.01`; amounts
 * below one major unit get two extra decimals.
 */
fun formatCost(usd: Double, currency: CurrencyConfig?, localeTag: String): String {
    var code = DEFAULT_CURRENCY
    var rate = 1.0
    val requested = currency?.code?.uppercase()
    val requestedDigits = requested?.let(::currencyFractionDigits)
    if (requested != null && requestedDigits != null) {
        code = requested
        rate = currency.rate.takeIf { it.isFinite() && it > 0 } ?: 1.0
    }
    val base = requestedDigits?.takeIf { code == requested } ?: currencyFractionDigits(code) ?: 2
    val amount = (if (usd.isFinite() && usd > 0) usd else 0.0) * rate
    val smallest = 10.0.pow(-base)
    return when {
        amount <= 0 -> formatCurrency(0.0, code, base, base, localeTag)
        amount < smallest -> "<" + formatCurrency(smallest, code, base, base, localeTag)
        amount < 1 && base > 0 -> formatCurrency(amount, code, base, base + 2, localeTag)
        else -> formatCurrency(amount, code, base, base, localeTag)
    }
}

/** The currency's minor-unit digits (USD 2, JPY 0, KWD 3), or null when [code] isn't ISO-4217. */
internal expect fun currencyFractionDigits(code: String): Int?

/** [amount] in currency [code] (already validated) for [localeTag], with the given fraction digits. */
internal expect fun formatCurrency(
    amount: Double,
    code: String,
    minFractionDigits: Int,
    maxFractionDigits: Int,
    localeTag: String,
): String

private const val DEFAULT_CURRENCY = "USD"
