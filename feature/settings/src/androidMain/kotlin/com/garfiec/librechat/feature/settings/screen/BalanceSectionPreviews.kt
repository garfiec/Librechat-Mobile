package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.common.datetime.DateTimeFormatPrefs
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.components.AdaptiveGroupedPage
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.datetime.ProvideDateTimeFormat
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.settings.model.AutoRefillInfo
import com.garfiec.librechat.feature.settings.model.RefillIntervalUnit
import com.garfiec.librechat.feature.settings.viewmodel.BalanceUiState
import kotlin.time.Instant

private val autoRefillOn = AutoRefillInfo.On(
    lastRefill = Instant.parse("2026-10-12T09:04:11Z"),
    refillAmount = 100_000.0,
    intervalValue = 7.0,
    intervalUnit = RefillIntervalUnit.DAYS,
    nextRefill = Instant.parse("2026-10-19T09:04:11Z"),
)

@PreviewNarrowScreens
@Composable
private fun BalanceAutoRefillOnPreview() =
    BalancePreview(UiStyle.MATERIAL, BalanceUiState.Loaded(1_234_567.4, autoRefillOn))

@PreviewNarrowScreens
@Composable
private fun BalanceAutoRefillOnGlassPreview() =
    BalancePreview(UiStyle.LIQUID_GLASS, BalanceUiState.Loaded(1_234_567.4, autoRefillOn))

@PreviewNarrowScreens
@Composable
private fun BalanceAutoRefillOffPreview() =
    BalancePreview(UiStyle.MATERIAL, BalanceUiState.Loaded(12_345.678, AutoRefillInfo.Off))

@PreviewNarrowScreens
@Composable
private fun BalanceAutoRefillUnavailablePreview() =
    BalancePreview(UiStyle.MATERIAL, BalanceUiState.Loaded(0.0, AutoRefillInfo.Unavailable))

@PreviewNarrowScreens
@Composable
private fun BalanceLoadingPreview() = BalancePreview(UiStyle.MATERIAL, BalanceUiState.Loading)

@PreviewNarrowScreens
@Composable
private fun BalanceErrorPreview() = BalancePreview(UiStyle.MATERIAL, BalanceUiState.Error)

@PreviewNarrowScreens
@Composable
private fun BalanceErrorGlassPreview() = BalancePreview(UiStyle.LIQUID_GLASS, BalanceUiState.Error)

@Composable
private fun BalancePreview(style: UiStyle, state: BalanceUiState) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        // Provided so the "de" preview groups numbers and formats dates as German, as the app does.
        ProvideDateTimeFormat(DateTimeFormatPrefs()) {
            AdaptiveGroupedPage {
                LazyColumn {
                    item { AdaptiveSectionHeader("Balance") }
                    adaptiveSection { balanceRows(state, onRetry = {}) }
                }
            }
        }
    }
}
