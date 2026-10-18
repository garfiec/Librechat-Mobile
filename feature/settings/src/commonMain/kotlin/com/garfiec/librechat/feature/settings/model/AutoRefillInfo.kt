package com.garfiec.librechat.feature.settings.model

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.common.datetime.parseIsoInstantOrNull
import com.garfiec.librechat.core.model.Balance
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Upstream `REFILL_INTERVAL_UNITS` (`packages/data-provider/src/balance.ts`), in upstream order. */
enum class RefillIntervalUnit(val wire: String, val dateTimeUnit: DateTimeUnit) {
    SECONDS("seconds", DateTimeUnit.SECOND),
    MINUTES("minutes", DateTimeUnit.MINUTE),
    HOURS("hours", DateTimeUnit.HOUR),
    DAYS("days", DateTimeUnit.DAY),
    WEEKS("weeks", DateTimeUnit.WEEK),
    MONTHS("months", DateTimeUnit.MONTH),
    ;

    companion object {
        fun fromWire(value: String?): RefillIntervalUnit? = entries.firstOrNull { it.wire == value }
    }
}

/** What the Balance section shows for auto-refill, mirroring web's `AutoRefill` in `BillingControls.tsx`. */
@Immutable
sealed interface AutoRefillInfo {
    data object Off : AutoRefillInfo

    /** Enabled, but a field web requires is missing or unreadable. Web shows an error here. */
    data object Unavailable : AutoRefillInfo

    data class On(
        val lastRefill: Instant,
        val refillAmount: Double,
        val intervalValue: Double,
        val intervalUnit: RefillIntervalUnit,
        val nextRefill: Instant,
    ) : AutoRefillInfo
}

fun Balance.autoRefillInfo(timeZone: TimeZone = TimeZone.currentSystemDefault()): AutoRefillInfo {
    if (!autoRefillEnabled) return AutoRefillInfo.Off
    val last = lastRefill?.let(::parseIsoInstantOrNull)
    val amount = refillAmount
    val value = refillIntervalValue
    // An unknown unit means a newer server than this mirror; web would label it "seconds" and
    // show the last refill as the next one, so nothing is shown rather than something wrong.
    val unit = RefillIntervalUnit.fromWire(refillIntervalUnit)
    if (last == null || amount == null || value == null || unit == null) return AutoRefillInfo.Unavailable
    // An interval too large for a calendar date throws, here or when the screen formats it.
    val next = runCatching { refillEligibility(last, value, unit, timeZone).also { it.toLocalDateTime(timeZone) } }
        .getOrNull() ?: return AutoRefillInfo.Unavailable
    return AutoRefillInfo.On(
        lastRefill = last,
        refillAmount = amount,
        intervalValue = value,
        intervalUnit = unit,
        nextRefill = next,
    )
}

/**
 * Port of upstream `getRefillEligibilityDate`: the earliest the server will refill. It refills only
 * once this has passed *and* a prompt would take the balance below zero.
 *
 * Date units are added in the device zone, as web's local `Date` setters do. Two divergences:
 * month-end clamps (Jan 31 + 1 month = Feb 28) where JS rolls over to Mar 3, and a fractional
 * value is truncated, as JS's setters truncate it. Weeks are added as `value * 7` days, so 1.5 weeks
 * is 10 days, as upstream's `setDate(getDate() + value * 7)` gives.
 */
fun refillEligibility(
    lastRefill: Instant,
    value: Double,
    unit: RefillIntervalUnit,
    timeZone: TimeZone,
): Instant = when (unit) {
    RefillIntervalUnit.WEEKS -> lastRefill.plus((value * DAYS_PER_WEEK).toLong(), DateTimeUnit.DAY, timeZone)
    else -> lastRefill.plus(value.toLong(), unit.dateTimeUnit, timeZone)
}

private const val DAYS_PER_WEEK = 7
