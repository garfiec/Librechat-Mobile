package com.garfiec.librechat.core.model.usage

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/*
 * Token formatting for the context gauge and its breakdown. Ported from upstream web at v0.8.8
 * (`formatTokens` in client/src/utils/tokens.ts).
 */

/**
 * A token count in compact notation with at most one decimal and no trailing `.0`: 946, 82.1K,
 * 787K, 1.3M. Web uses the locale's compact notation; this is the English form everywhere.
 */
fun formatTokens(count: Int): String {
    val value = count.coerceAtLeast(0).toDouble()
    if (value < THOUSAND) return value.toInt().toString()
    var unit = COMPACT_UNITS.indexOfLast { value >= it.first }
    var tenths = (value / COMPACT_UNITS[unit].first * TENTHS).roundToLong()
    // 999,950 rounds to 1000.0K; it reads as 1M.
    if (tenths >= THOUSAND * TENTHS && unit < COMPACT_UNITS.lastIndex) {
        unit++
        tenths = (value / COMPACT_UNITS[unit].first * TENTHS).roundToLong()
    }
    val whole = tenths / TENTHS.toLong()
    val decimal = tenths % TENTHS.toLong()
    val number = if (decimal == 0L) "$whole" else "$whole.$decimal"
    return number + COMPACT_UNITS[unit].second
}

/** [used] as a whole percentage of [max], rounded as web rounds it; 0 without a window. */
fun percentOf(used: Int, max: Int): Int =
    if (max > 0) (used.toFloat() / max * PERCENT).coerceIn(0f, PERCENT).roundToInt() else 0

private const val THOUSAND = 1_000
private const val TENTHS = 10
private const val PERCENT = 100f
private val COMPACT_UNITS = listOf(1e3 to "K", 1e6 to "M", 1e9 to "B")
