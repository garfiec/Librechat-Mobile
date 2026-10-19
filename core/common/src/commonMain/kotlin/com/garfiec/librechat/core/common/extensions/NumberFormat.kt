package com.garfiec.librechat.core.common.extensions

/**
 * [value] with the grouping and decimal separators of [localeTag] (`12,346` in en, `12.346` in de),
 * rounded half-up to at most [maxFractionDigits] places, trailing zeros dropped down to [minFractionDigits] places.
 *
 * [localeTag] is a platform locale identifier, as `ResolvedDateTimeFormat.localeTag` carries it.
 */
expect fun formatLocalizedNumber(
    value: Double,
    localeTag: String,
    maxFractionDigits: Int = 0,
    minFractionDigits: Int = 0,
): String
