package com.garfiec.librechat.feature.settings.model

import com.garfiec.librechat.core.model.Balance
import com.garfiec.librechat.core.model.config.BalanceConfig
import com.garfiec.librechat.core.model.config.CurrencyConfig
import com.garfiec.librechat.core.model.config.InterfaceConfig
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.feature.settings.viewmodel.BalanceUiState
import com.garfiec.librechat.feature.settings.viewmodel.presentedFor
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The balance reading's display mode, refill mode and share spent (upstream `client/src/utils/balance.ts`). */
class BalanceSummaryTest {

    @Test
    fun `an unknown or absent display mode reads as credits`() {
        assertThat(BalanceDisplay.fromWire(null)).isEqualTo(BalanceDisplay.CREDITS)
        assertThat(BalanceDisplay.fromWire("dollars")).isEqualTo(BalanceDisplay.CREDITS)
        assertThat(BalanceDisplay.fromWire("percent")).isEqualTo(BalanceDisplay.PERCENT)
        assertThat(BalanceDisplay.fromWire("currency")).isEqualTo(BalanceDisplay.CURRENCY)
    }

    @Test
    fun `percent used is floored and held under 100 while credits remain`() {
        assertThat(usedPercent(credits = 2_500.0, allotment = 10_000.0)).isEqualTo(75)
        assertThat(usedPercent(credits = 1.0, allotment = 10_000.0)).isEqualTo(99)
        assertThat(usedPercent(credits = 20_000.0, allotment = 10_000.0)).isEqualTo(0)
    }

    @Test
    fun `an empty balance is fully spent whatever it is measured against`() {
        assertThat(usedPercent(credits = 0.0, allotment = null)).isEqualTo(100)
        assertThat(usedPercent(credits = -5.0, allotment = 10_000.0)).isEqualTo(100)
    }

    @Test
    fun `no allotment means no percent`() {
        assertThat(usedPercent(credits = 5.0, allotment = null)).isNull()
        assertThat(usedPercent(credits = 5.0, allotment = 0.0)).isNull()
    }

    @Test
    fun `the allotment is the refill amount under auto-refill, else the start balance`() {
        val refill = Balance(tokenCredits = 1.0, autoRefillEnabled = true, refillAmount = 5_000.0)
        assertThat(refill.allotment(startBalance = 20_000.0)).isEqualTo(5_000.0)
        assertThat(refill.copy(autoRefillEnabled = false).allotment(startBalance = 20_000.0)).isEqualTo(20_000.0)
        assertThat(refill.copy(refillAmount = 0.0).allotment(startBalance = null)).isNull()
    }

    @Test
    fun `the config's display, currency and start balance present a loaded reading`() {
        val config = StartupConfig(
            balance = BalanceConfig(enabled = true, startBalance = 10_000.0, display = "percent"),
            interfaceConfig = InterfaceConfig(currency = CurrencyConfig(code = "EUR", rate = 0.9)),
        )
        val presented = BalanceUiState.Loaded(tokenCredits = 4_000.0, autoRefill = AutoRefillInfo.Off)
            .presentedFor(config) as BalanceUiState.Loaded
        assertThat(presented.display).isEqualTo(BalanceDisplay.PERCENT)
        assertThat(presented.usedPercent).isEqualTo(60)
        assertThat(presented.currency?.code).isEqualTo("EUR")
    }

    @Test
    fun `a reset refill mode reaches the auto-refill row`() {
        val balance = Balance(
            tokenCredits = 1.0,
            autoRefillEnabled = true,
            refillIntervalValue = 1.0,
            refillIntervalUnit = "days",
            lastRefill = "2026-10-01T00:00:00Z",
            refillAmount = 100.0,
            refillMode = "reset",
        )
        assertThat((balance.autoRefillInfo() as AutoRefillInfo.On).reset).isTrue()
        assertThat((balance.copy(refillMode = null).autoRefillInfo() as AutoRefillInfo.On).reset).isFalse()
    }
}
