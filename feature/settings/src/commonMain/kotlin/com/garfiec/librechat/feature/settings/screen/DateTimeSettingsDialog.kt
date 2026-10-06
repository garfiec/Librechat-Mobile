package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.datetime.ClockFormat
import com.garfiec.librechat.core.common.datetime.DateFormatStyle
import com.garfiec.librechat.core.common.datetime.DateTimeFormatPrefs
import com.garfiec.librechat.core.common.datetime.ResolvedDateTimeFormat
import com.garfiec.librechat.core.common.datetime.TimestampStyle
import com.garfiec.librechat.core.common.datetime.messageLabel
import com.garfiec.librechat.core.common.extensions.RelativeTimeReference
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveSegmentedChoice
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.core.ui.datetime.LocalRelativeTimeReference
import com.garfiec.librechat.core.ui.datetime.rememberDateLocaleTag
import com.garfiec.librechat.core.ui.datetime.rememberSystemIs24Hour
import com.garfiec.librechat.core.ui.datetime.resolve
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.action_cancel
import com.garfiec.librechat.feature.settings.resources.action_save
import com.garfiec.librechat.feature.settings.resources.date_format_dmy_dash
import com.garfiec.librechat.feature.settings.resources.date_format_dmy_dot
import com.garfiec.librechat.feature.settings.resources.date_format_dmy_slash
import com.garfiec.librechat.feature.settings.resources.date_format_long
import com.garfiec.librechat.feature.settings.resources.date_format_mdy_slash
import com.garfiec.librechat.feature.settings.resources.date_format_system
import com.garfiec.librechat.feature.settings.resources.date_format_system_numeric
import com.garfiec.librechat.feature.settings.resources.date_format_ymd_dash
import com.garfiec.librechat.feature.settings.resources.date_format_ymd_slash
import com.garfiec.librechat.feature.settings.resources.date_time_clock
import com.garfiec.librechat.feature.settings.resources.date_time_clock_12h
import com.garfiec.librechat.feature.settings.resources.date_time_clock_24h
import com.garfiec.librechat.feature.settings.resources.date_time_clock_system
import com.garfiec.librechat.feature.settings.resources.date_time_date_format
import com.garfiec.librechat.feature.settings.resources.date_time_preview
import com.garfiec.librechat.feature.settings.resources.date_time_style_absolute
import com.garfiec.librechat.feature.settings.resources.date_time_style_relative
import com.garfiec.librechat.feature.settings.resources.date_time_style_smart
import com.garfiec.librechat.feature.settings.resources.date_time_system_is_12h
import com.garfiec.librechat.feature.settings.resources.date_time_system_is_24h
import com.garfiec.librechat.feature.settings.resources.date_time_timestamps
import com.garfiec.librechat.feature.settings.resources.date_time_title
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Date & time: timestamp style, clock and date format in one dialog, with a live preview of
 * the *pending* choices. Nothing is written until Save; Cancel discards.
 *
 * The clock stays enabled under Relative: relative timestamps still show a clock time from 30 days
 * on, and when tapped.
 */
@Composable
internal fun DateTimeSettingsDialog(
    prefs: DateTimeFormatPrefs,
    onSave: (DateTimeFormatPrefs) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pending by remember { mutableStateOf(prefs) }
    val systemIs24Hour = rememberSystemIs24Hour()
    val localeTag = rememberDateLocaleTag()
    val pendingFormat = remember(pending, systemIs24Hour, localeTag) {
        ResolvedDateTimeFormat.resolve(pending, systemIs24Hour, localeTag)
    }

    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(Res.string.date_time_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                DateTimePreview(pendingFormat)

                SectionLabel(stringResource(Res.string.date_time_timestamps))
                val styles = TimestampStyle.entries
                FittingChoice(
                    options = styles,
                    selected = pending.style,
                    onSelect = { pending = pending.copy(style = it) },
                    optionLabel = { timestampStyleLabel(it) },
                )

                SectionLabel(stringResource(Res.string.date_time_clock))
                val clocks = ClockFormat.entries
                FittingChoice(
                    options = clocks,
                    selected = pending.clock,
                    onSelect = { pending = pending.copy(clock = it) },
                    optionLabel = { clockFormatLabel(it) },
                )
                Text(
                    text = stringResource(
                        if (systemIs24Hour) Res.string.date_time_system_is_24h else Res.string.date_time_system_is_12h,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )

                SectionLabel(stringResource(Res.string.date_time_date_format))
                DateFormatOptions(
                    selected = pending.date,
                    pending = pending,
                    systemIs24Hour = systemIs24Hour,
                    localeTag = localeTag,
                    onSelect = { pending = pending.copy(date = it) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(pending) }) {
                Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

/**
 * Three message timestamps rendered with the pending choices: one recent, one from earlier in the
 * week, one from two months back — between them every style shows what it does differently.
 */
@Composable
private fun DateTimePreview(format: ResolvedDateTimeFormat) {
    val reference = LocalRelativeTimeReference.current
    val labels = remember(format, reference) {
        listOf(3.hours, 3.days, 61.days).map { ago -> (reference.now - ago).messageLabel(format, reference) }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(Res.string.date_time_preview),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        labels.forEach { Text(text = it.resolve(), style = MaterialTheme.typography.bodyMedium) }
    }
}

/** Each option is captioned with today's date in that format, so the choice is made by sight. */
@Composable
private fun DateFormatOptions(
    selected: DateFormatStyle,
    pending: DateTimeFormatPrefs,
    systemIs24Hour: Boolean,
    localeTag: String,
    onSelect: (DateFormatStyle) -> Unit,
) {
    val reference: RelativeTimeReference = LocalRelativeTimeReference.current
    val samples = remember(pending.clock, systemIs24Hour, localeTag, reference.today) {
        DateFormatStyle.entries.associateWith { style ->
            val format = ResolvedDateTimeFormat.resolve(pending.copy(date = style), systemIs24Hour, localeTag)
            format.format(reference.now, format.patterns.dateWithYear, reference.timeZone)
        }
    }
    RadioGroup(
        options = DateFormatStyle.entries,
        selected = selected,
        onSelect = onSelect,
        optionLabel = { dateFormatStyleLabel(it) },
        optionDescription = { samples[it] },
    )
}

/**
 * A segmented row when every label fits its segment on one line, otherwise the same choice stacked
 * as radio rows. Segmented buttons wrap a long label mid-word ("Относите/льное" in ru), so the fit is
 * measured rather than guessed per locale. M3 measures the label against the segment's full inner
 * width and lets the selected check icon overhang it, so the icon is not part of the budget — and
 * selecting an option can't flip the layout.
 */
@Composable
private fun <T> FittingChoice(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    optionLabel: @Composable (T) -> String,
) {
    val labels = options.map { optionLabel(it) }
    val measurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val widestLabel = remember(labels, textStyle, density) {
            labels.maxOf { measurer.measure(it, textStyle, softWrap = false, maxLines = 1).size.width }
        }
        val segmentBudget = with(density) { (maxWidth / options.size - SegmentChrome).roundToPx() }
        if (widestLabel <= segmentBudget) {
            AdaptiveSegmentedChoice(
                options = labels,
                selectedIndex = options.indexOf(selected),
                onSelect = { onSelect(options[it]) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Column {
                RadioGroup(
                    options = options,
                    selected = selected,
                    onSelect = onSelect,
                    optionLabel = optionLabel,
                )
            }
        }
    }
}

/** A segment's non-text width: 12dp content padding each side plus borders. */
private val SegmentChrome = 12.dp + 12.dp + 2.dp

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
internal fun timestampStyleLabel(style: TimestampStyle): String = when (style) {
    TimestampStyle.RELATIVE -> stringResource(Res.string.date_time_style_relative)
    TimestampStyle.SMART -> stringResource(Res.string.date_time_style_smart)
    TimestampStyle.ABSOLUTE -> stringResource(Res.string.date_time_style_absolute)
}

@Composable
private fun clockFormatLabel(clock: ClockFormat): String = when (clock) {
    ClockFormat.SYSTEM -> stringResource(Res.string.date_time_clock_system)
    ClockFormat.H12 -> stringResource(Res.string.date_time_clock_12h)
    ClockFormat.H24 -> stringResource(Res.string.date_time_clock_24h)
}

@Composable
private fun dateFormatStyleLabel(style: DateFormatStyle): String = when (style) {
    DateFormatStyle.SYSTEM -> stringResource(Res.string.date_format_system)
    DateFormatStyle.SYSTEM_NUMERIC -> stringResource(Res.string.date_format_system_numeric)
    DateFormatStyle.DMY_DOT -> stringResource(Res.string.date_format_dmy_dot)
    DateFormatStyle.DMY_SLASH -> stringResource(Res.string.date_format_dmy_slash)
    DateFormatStyle.DMY_DASH -> stringResource(Res.string.date_format_dmy_dash)
    DateFormatStyle.MDY_SLASH -> stringResource(Res.string.date_format_mdy_slash)
    DateFormatStyle.YMD_DASH -> stringResource(Res.string.date_format_ymd_dash)
    DateFormatStyle.YMD_SLASH -> stringResource(Res.string.date_format_ymd_slash)
    DateFormatStyle.LONG -> stringResource(Res.string.date_format_long)
}

/** The settings-row subtitle: a live absolute sample plus the style name, e.g. "05.08.2026 20:58 · Relative". */
@Composable
internal fun dateTimeSummary(): String {
    val format = LocalDateTimeFormat.current
    val reference = LocalRelativeTimeReference.current
    val sample = remember(format, reference) { format.formatAbsolute(reference.now, reference.timeZone) }
    return "$sample · ${timestampStyleLabel(format.prefs.style)}"
}
