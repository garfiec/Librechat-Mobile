package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.extensions.formatLocalizedNumber
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveSectionScope
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.contextusage.formatCost
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.feature.settings.model.AutoRefillInfo
import com.garfiec.librechat.feature.settings.model.BalanceDisplay
import com.garfiec.librechat.feature.settings.model.CREDITS_PER_USD
import com.garfiec.librechat.feature.settings.model.RefillIntervalUnit
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.BalanceUiState
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Balance section's rows, after web's Settings → Account → Billing: credits, then auto-refill.
 * Credits round to a whole number with locale grouping, as web's account menu shows them; a credit
 * is a millionth of a dollar, so decimals carry nothing.
 */
internal fun AdaptiveSectionScope.balanceRows(state: BalanceUiState, onRetry: () -> Unit) {
    when (state) {
        BalanceUiState.Loading -> row(key = "balance_credits") { CreditsRow(tokenCredits = null) }
        BalanceUiState.Error -> row(key = "balance_error") { BalanceErrorRow(onRetry) }
        is BalanceUiState.Loaded -> {
            row(key = "balance_credits") { CreditsRow(tokenCredits = state.tokenCredits, reading = state) }
            row(key = "balance_auto_refill") { AutoRefillRow(state.autoRefill, state) }
        }
    }
}

/**
 * [tokenCredits] null while loading. [reading] carries the deployment's display mode (web's
 * `Balance/Summary.tsx`): credits as a number, `currency` as money, `percent` as the share used
 * with no credit figure anywhere.
 */
@Composable
private fun CreditsRow(tokenCredits: Double?, modifier: Modifier = Modifier, reading: BalanceUiState.Loaded? = null) {
    val localeTag = LocalDateTimeFormat.current.localeTag
    val display = reading?.display ?: BalanceDisplay.CREDITS
    val usedPercent = reading?.usedPercent
    val currency = reading?.currency
    val percentText = usedPercent?.let { stringResource(Res.string.balance_used, "$it%") }
    val unavailable = stringResource(Res.string.balance_usage_unavailable)
    val formatted = remember(tokenCredits, localeTag, display, percentText, currency, unavailable) {
        tokenCredits?.let {
            when (display) {
                BalanceDisplay.CREDITS -> formatLocalizedNumber(it, localeTag)
                BalanceDisplay.CURRENCY -> formatCost(it / CREDITS_PER_USD, currency, localeTag)
                BalanceDisplay.PERCENT -> percentText ?: unavailable
            }
        }
    }
    val title =
        stringResource(if (display == BalanceDisplay.PERCENT) Res.string.balance_usage_limit else Res.string.token_credits)
    // The share spent sits under the figure when the figure is not itself the share.
    val status = when {
        tokenCredits == null -> null
        tokenCredits <= 0 -> stringResource(Res.string.balance_out_of_credits)
        display != BalanceDisplay.PERCENT -> percentText
        else -> null
    }
    val balanceCd = formatted?.let {
        if (display == BalanceDisplay.CREDITS) {
            stringResource(Res.string.cd_token_balance, it)
        } else {
            stringResource(Res.string.cd_balance_reading, title, it)
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .then(if (balanceCd != null) Modifier.clearAndSetSemantics { contentDescription = balanceCd } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.AccountBalanceWallet,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (display == BalanceDisplay.CREDITS) {
                Text(
                    text = stringResource(Res.string.balance_credit_conversion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            status?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (tokenCredits != null && tokenCredits <= 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        if (formatted == null) {
            AdaptiveCircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(
                text = formatted,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    AdaptiveDivider()
}

@Composable
private fun BalanceErrorRow(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = stringResource(Res.string.balance_load_error),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        AdaptiveTextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
    }
    AdaptiveDivider()
}

@Composable
private fun AutoRefillRow(info: AutoRefillInfo, reading: BalanceUiState.Loaded, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val title = stringResource(Res.string.auto_refill_title)
        when (info) {
            AutoRefillInfo.Off -> LabelValue(title, stringResource(Res.string.auto_refill_off), titleStyle = true)
            AutoRefillInfo.Unavailable ->
                LabelValue(title, stringResource(Res.string.auto_refill_unavailable), titleStyle = true)
            is AutoRefillInfo.On -> AutoRefillDetails(info, reading)
        }
    }
    AdaptiveDivider()
}

/**
 * Web's `AutoRefillSettings`: a `reset` allowance swaps the refill wording for reset wording, and a
 * percent-only deployment shows no credit figure, so the amount row is left out.
 */
@Composable
private fun ColumnScope.AutoRefillDetails(info: AutoRefillInfo.On, reading: BalanceUiState.Loaded) {
    val dateFormat = LocalDateTimeFormat.current
    val text = remember(info, dateFormat, reading.display, reading.currency) {
        AutoRefillText(
            lastRefill = dateFormat.formatAbsolute(info.lastRefill),
            amount = when (reading.display) {
                BalanceDisplay.CURRENCY ->
                    formatCost(info.refillAmount / CREDITS_PER_USD, reading.currency, dateFormat.localeTag)
                else -> formatLocalizedNumber(info.refillAmount, dateFormat.localeTag)
            },
            intervalValue = formatLocalizedNumber(info.intervalValue, dateFormat.localeTag, maxFractionDigits = 2),
            nextRefill = dateFormat.formatAbsolute(info.nextRefill),
        )
    }
    val reset = info.reset
    Text(
        text = stringResource(if (reset) Res.string.auto_reset_title else Res.string.auto_refill_title),
        style = MaterialTheme.typography.bodyLarge,
    )
    LabelValue(stringResource(if (reset) Res.string.auto_reset_last else Res.string.auto_refill_last), text.lastRefill)
    if (reading.display != BalanceDisplay.PERCENT) {
        LabelValue(stringResource(if (reset) Res.string.auto_reset_amount else Res.string.auto_refill_amount), text.amount)
    }
    LabelValue(
        stringResource(Res.string.auto_refill_interval),
        pluralStringResource(info.intervalUnit.everyPlural, info.intervalValue.toInt(), text.intervalValue),
    )
    LabelValue(stringResource(if (reset) Res.string.auto_reset_next else Res.string.auto_refill_next), text.nextRefill)
    Text(
        text = stringResource(if (reset) Res.string.auto_reset_next_info else Res.string.auto_refill_next_info),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private class AutoRefillText(
    val lastRefill: String,
    val amount: String,
    val intervalValue: String,
    val nextRefill: String,
)

/** A label on the leading edge and its value on the trailing edge; the value wraps rather than overlapping. */
@Composable
private fun LabelValue(label: String, value: String, titleStyle: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = if (titleStyle) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

private val RefillIntervalUnit.everyPlural: PluralStringResource
    get() = when (this) {
        RefillIntervalUnit.SECONDS -> Res.plurals.auto_refill_every_seconds
        RefillIntervalUnit.MINUTES -> Res.plurals.auto_refill_every_minutes
        RefillIntervalUnit.HOURS -> Res.plurals.auto_refill_every_hours
        RefillIntervalUnit.DAYS -> Res.plurals.auto_refill_every_days
        RefillIntervalUnit.WEEKS -> Res.plurals.auto_refill_every_weeks
        RefillIntervalUnit.MONTHS -> Res.plurals.auto_refill_every_months
    }
