package com.garfiec.librechat.core.ui.contextusage

import com.garfiec.librechat.core.model.config.CurrencyConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** `formatCost` through the Android currency formatter (upstream `formatCost`, v0.8.8). */
class CurrencyFormatTest {

    private fun cost(usd: Double, currency: CurrencyConfig? = null) = formatCost(usd, currency, "en-US")

    @Test
    fun usd_by_default_with_two_digits_and_extra_precision_below_one() {
        assertThat(cost(2.6)).isEqualTo("$2.60")
        assertThat(cost(0.1234)).isEqualTo("$0.1234")
        assertThat(cost(0.0)).isEqualTo("$0.00")
    }

    @Test
    fun an_amount_below_one_cent_reads_as_less_than_the_minor_unit() {
        assertThat(cost(0.001)).isEqualTo("<$0.01")
    }

    @Test
    fun the_currency_rate_and_its_own_minor_unit_apply() {
        assertThat(cost(2.0, CurrencyConfig("JPY", rate = 150.0))).isEqualTo("¥300")
        assertThat(cost(2.0, CurrencyConfig("KWD", rate = 0.5))).isEqualTo("KWD1.000")
    }

    @Test
    fun an_unknown_code_falls_back_to_usd_at_rate_one() {
        assertThat(cost(2.0, CurrencyConfig("EUU", rate = 10.0))).isEqualTo("$2.00")
    }

    @Test
    fun a_pseudo_currency_formats_with_two_digits() {
        assertThat(currencyFractionDigits("XXX")).isEqualTo(2)
        assertThat(currencyFractionDigits("JPY")).isEqualTo(0)
    }
}
