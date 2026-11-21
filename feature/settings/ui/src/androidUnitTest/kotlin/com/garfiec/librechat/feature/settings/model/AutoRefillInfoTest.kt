package com.garfiec.librechat.feature.settings.model

import com.garfiec.librechat.core.model.Balance
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.TimeZone
import org.junit.Test
import kotlin.time.Instant

/** Cases ported from upstream `packages/data-provider/src/balance.spec.ts`. */
class AutoRefillInfoTest {

    private val utc = TimeZone.UTC
    private val lastRefill = Instant.parse("2026-05-05T16:00:00.000Z")

    private fun eligibility(value: Double, unit: RefillIntervalUnit, from: Instant = lastRefill) =
        refillEligibility(from, value, unit, utc)

    @Test
    fun `each unit is added to the last refill`() {
        assertThat(eligibility(30.0, RefillIntervalUnit.SECONDS)).isEqualTo(Instant.parse("2026-05-05T16:00:30Z"))
        assertThat(eligibility(15.0, RefillIntervalUnit.MINUTES)).isEqualTo(Instant.parse("2026-05-05T16:15:00Z"))
        assertThat(eligibility(2.0, RefillIntervalUnit.HOURS)).isEqualTo(Instant.parse("2026-05-05T18:00:00Z"))
        assertThat(eligibility(1.0, RefillIntervalUnit.DAYS)).isEqualTo(Instant.parse("2026-05-06T16:00:00Z"))
        assertThat(eligibility(1.0, RefillIntervalUnit.WEEKS)).isEqualTo(Instant.parse("2026-05-12T16:00:00Z"))
        assertThat(eligibility(1.0, RefillIntervalUnit.MONTHS)).isEqualTo(Instant.parse("2026-06-05T16:00:00Z"))
    }

    @Test
    fun `a zero interval is the last refill itself`() {
        assertThat(eligibility(0.0, RefillIntervalUnit.DAYS)).isEqualTo(lastRefill)
    }

    @Test
    fun `a fractional week is truncated after converting to days, as upstream does`() {
        assertThat(eligibility(1.5, RefillIntervalUnit.WEEKS)).isEqualTo(Instant.parse("2026-05-15T16:00:00Z"))
    }

    @Test
    fun `an interval too large for a date is Unavailable rather than a crash`() {
        val huge = Balance(
            autoRefillEnabled = true,
            refillIntervalValue = 1e18,
            refillIntervalUnit = "months",
            lastRefill = "2026-10-12T09:04:11.000Z",
            refillAmount = 1.0,
        )

        assertThat(huge.autoRefillInfo(utc)).isEqualTo(AutoRefillInfo.Unavailable)
        assertThat(huge.copy(refillIntervalUnit = "seconds").autoRefillInfo(utc)).isEqualTo(AutoRefillInfo.Unavailable)
    }

    @Test
    fun `month end clamps where JavaScript rolls over`() {
        // Web shows 2026-03-03 here; see refillEligibility's KDoc.
        assertThat(eligibility(1.0, RefillIntervalUnit.MONTHS, Instant.parse("2026-01-31T12:00:00Z")))
            .isEqualTo(Instant.parse("2026-02-28T12:00:00Z"))
    }

    @Test
    fun `auto-refill off is Off`() {
        assertThat(Balance(tokenCredits = 1.0).autoRefillInfo(utc)).isEqualTo(AutoRefillInfo.Off)
    }

    @Test
    fun `a complete schedule is On with the next refill`() {
        val info = Balance(
            autoRefillEnabled = true,
            refillIntervalValue = 7.0,
            refillIntervalUnit = "days",
            lastRefill = "2026-10-12T09:04:11.000Z",
            refillAmount = 100_000.0,
        ).autoRefillInfo(utc)

        assertThat(info).isEqualTo(
            AutoRefillInfo.On(
                lastRefill = Instant.parse("2026-10-12T09:04:11Z"),
                refillAmount = 100_000.0,
                intervalValue = 7.0,
                intervalUnit = RefillIntervalUnit.DAYS,
                nextRefill = Instant.parse("2026-10-19T09:04:11Z"),
            ),
        )
    }

    @Test
    fun `a missing field or an unknown unit is Unavailable`() {
        val complete = Balance(
            autoRefillEnabled = true,
            refillIntervalValue = 1.0,
            refillIntervalUnit = "days",
            lastRefill = "2026-10-12T09:04:11.000Z",
            refillAmount = 1.0,
        )

        assertThat(complete.copy(lastRefill = null).autoRefillInfo(utc)).isEqualTo(AutoRefillInfo.Unavailable)
        assertThat(complete.copy(lastRefill = "yesterday").autoRefillInfo(utc)).isEqualTo(AutoRefillInfo.Unavailable)
        assertThat(complete.copy(refillAmount = null).autoRefillInfo(utc)).isEqualTo(AutoRefillInfo.Unavailable)
        assertThat(complete.copy(refillIntervalValue = null).autoRefillInfo(utc)).isEqualTo(AutoRefillInfo.Unavailable)
        assertThat(complete.copy(refillIntervalUnit = "years").autoRefillInfo(utc)).isEqualTo(AutoRefillInfo.Unavailable)
    }
}
