package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.common.datetime.ClockFormat
import com.garfiec.librechat.core.common.datetime.DateFormatStyle
import com.garfiec.librechat.core.common.datetime.DateTimeFormatPrefs
import com.garfiec.librechat.core.common.datetime.ResolvedDateTimeFormat
import com.garfiec.librechat.core.common.datetime.TimestampStyle
import com.garfiec.librechat.core.common.datetime.messageLabel
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveRadioButton
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.core.ui.datetime.LocalRelativeTimeReference
import com.garfiec.librechat.core.ui.datetime.rememberDateLocaleTag
import com.garfiec.librechat.core.ui.datetime.rememberSystemIs24Hour
import com.garfiec.librechat.core.ui.datetime.resolve
import com.garfiec.librechat.core.ui.theme.themeShapes
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.chat_appearance_sample_answer
import com.garfiec.librechat.feature.settings.resources.chat_appearance_sample_question
import com.garfiec.librechat.feature.settings.resources.chat_appearance_sample_thanks
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
import com.garfiec.librechat.feature.settings.resources.date_time_date_format_note
import com.garfiec.librechat.feature.settings.resources.date_time_style_absolute
import com.garfiec.librechat.feature.settings.resources.date_time_style_absolute_desc
import com.garfiec.librechat.feature.settings.resources.date_time_style_relative
import com.garfiec.librechat.feature.settings.resources.date_time_style_relative_desc
import com.garfiec.librechat.feature.settings.resources.date_time_style_smart
import com.garfiec.librechat.feature.settings.resources.date_time_style_smart_desc
import com.garfiec.librechat.feature.settings.resources.date_time_system_is_12h
import com.garfiec.librechat.feature.settings.resources.date_time_system_is_24h
import com.garfiec.librechat.feature.settings.resources.date_time_timestamps
import com.garfiec.librechat.feature.settings.resources.date_time_title
import com.garfiec.librechat.feature.settings.viewmodel.GeneralSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@Composable
fun DateTimeSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GeneralSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsPageScaffold(
        title = stringResource(Res.string.date_time_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) { contentPadding ->
        DateTimeSettingsContent(
            prefs = uiState.dateTimePrefs,
            onChange = viewModel::updateDateTimePrefs,
            contentPadding = contentPadding,
        )
    }
}

@Composable
internal fun DateTimeSettingsContent(
    prefs: DateTimeFormatPrefs,
    onChange: ((DateTimeFormatPrefs) -> DateTimeFormatPrefs) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val systemIs24Hour = rememberSystemIs24Hour()
    val localeTag = rememberDateLocaleTag()
    val format = remember(prefs, systemIs24Hour, localeTag) {
        ResolvedDateTimeFormat.resolve(prefs, systemIs24Hour, localeTag)
    }

    SettingsListPage(contentPadding = contentPadding, modifier = modifier) {
        item(key = "top_gap") { Spacer(modifier = Modifier.height(16.dp)) }
        adaptiveSection {
            row(key = "preview") { DateTimePreview(format) }
        }
        item(key = "style") {
            PillGroup(
                label = stringResource(Res.string.date_time_timestamps),
                options = TimestampStyle.entries.map { timestampStyleLabel(it) },
                selectedIndex = prefs.style.ordinal,
                onSelect = { index -> onChange { it.copy(style = TimestampStyle.entries[index]) } },
                description = timestampStyleDescription(prefs.style),
            )
        }
        item(key = "clock") {
            PillGroup(
                label = stringResource(Res.string.date_time_clock),
                options = ClockFormat.entries.map { clockFormatLabel(it) },
                selectedIndex = prefs.clock.ordinal,
                onSelect = { index -> onChange { it.copy(clock = ClockFormat.entries[index]) } },
                description = stringResource(
                    if (systemIs24Hour) Res.string.date_time_system_is_24h else Res.string.date_time_system_is_12h,
                ),
            )
        }
        item(key = "date_header") {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                AdaptiveSectionHeader(stringResource(Res.string.date_time_date_format))
            }
        }
        adaptiveSection {
            row(key = "date_formats") {
                DateFormatOptions(
                    prefs = prefs,
                    systemIs24Hour = systemIs24Hour,
                    localeTag = localeTag,
                    onSelect = { style -> onChange { it.copy(date = style) } },
                )
            }
        }
        item(key = "date_note") {
            Text(
                text = stringResource(Res.string.date_time_date_format_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp),
            )
        }
        item(key = "bottom_gap") { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

/**
 * Three messages from the Lisbon sample chat, sent two months, three days and three hours ago, each with
 * its timestamp as the chat shows it. Between them every style shows what it does differently.
 */
@Composable
private fun DateTimePreview(format: ResolvedDateTimeFormat) {
    val reference = LocalRelativeTimeReference.current
    val labels = remember(format, reference) {
        PreviewAges.map { ago -> (reference.now - ago).messageLabel(format, reference) }
    }
    val texts = listOf(
        stringResource(Res.string.chat_appearance_sample_question),
        stringResource(Res.string.chat_appearance_sample_answer),
        stringResource(Res.string.chat_appearance_sample_thanks),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 8.dp),
    ) {
        texts.forEachIndexed { index, text ->
            PreviewMessage(isUser = index != 1, text = text, timestamp = labels[index].resolve())
        }
    }
}

private val PreviewAges = listOf(61.days, 3.days, 3.hours)

/** A message at `MessageBubble`'s colours and shape, with its timestamp under it in `MessageTimestamp`'s style. */
@Composable
private fun PreviewMessage(isUser: Boolean, text: String, timestamp: String) {
    val alignment = if (isUser) Alignment.End else Alignment.Start
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = alignment,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            // The words don't matter here, the timestamps do: two lines keep the choices in view at large text.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth(BubbleWidthFraction)
                .wrapContentWidth(alignment)
                .background(
                    if (isUser) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    themeShapes().bubble,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Text(
            text = timestamp,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }
}

private const val BubbleWidthFraction = 0.85f

/** Each format led by today's date in it, so the choice is made by sight; the pattern's name is the second line. */
@Composable
private fun DateFormatOptions(
    prefs: DateTimeFormatPrefs,
    systemIs24Hour: Boolean,
    localeTag: String,
    onSelect: (DateFormatStyle) -> Unit,
) {
    val reference = LocalRelativeTimeReference.current
    val samples = remember(prefs.clock, systemIs24Hour, localeTag, reference.today) {
        DateFormatStyle.entries.associateWith { style ->
            val format = ResolvedDateTimeFormat.resolve(prefs.copy(date = style), systemIs24Hour, localeTag)
            format.format(reference.now, format.patterns.dateWithYear, reference.timeZone)
        }
    }
    Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
        DateFormatStyle.entries.forEachIndexed { index, style ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .selectable(selected = style == prefs.date, role = Role.RadioButton, onClick = { onSelect(style) })
                    .padding(SettingsRowPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AdaptiveRadioButton(selected = style == prefs.date, onClick = null)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = samples.getValue(style),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = dateFormatStyleLabel(style),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (index < DateFormatStyle.entries.lastIndex) AdaptiveDivider(modifier = Modifier.padding(start = 56.dp))
        }
    }
}

@Composable
private fun timestampStyleLabel(style: TimestampStyle): String = when (style) {
    TimestampStyle.RELATIVE -> stringResource(Res.string.date_time_style_relative)
    TimestampStyle.SMART -> stringResource(Res.string.date_time_style_smart)
    TimestampStyle.ABSOLUTE -> stringResource(Res.string.date_time_style_absolute)
}

@Composable
private fun timestampStyleDescription(style: TimestampStyle): String = when (style) {
    TimestampStyle.RELATIVE -> stringResource(Res.string.date_time_style_relative_desc)
    TimestampStyle.SMART -> stringResource(Res.string.date_time_style_smart_desc)
    TimestampStyle.ABSOLUTE -> stringResource(Res.string.date_time_style_absolute_desc)
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
internal fun dateTimeSummary(): String =
    "${dateTimeSample()} · ${timestampStyleLabel(LocalDateTimeFormat.current.prefs.style)}"

/** The current time in the chosen absolute format, e.g. "05.08.2026 20:58". */
@Composable
internal fun dateTimeSample(): String {
    val format = LocalDateTimeFormat.current
    val reference = LocalRelativeTimeReference.current
    return remember(format, reference) { format.formatAbsolute(reference.now, reference.timeZone) }
}
