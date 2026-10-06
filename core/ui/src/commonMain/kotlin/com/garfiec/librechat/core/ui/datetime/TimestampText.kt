package com.garfiec.librechat.core.ui.datetime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.garfiec.librechat.core.common.datetime.DateGroup
import com.garfiec.librechat.core.common.datetime.TimestampLabel
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.date_group_previous_30_days
import com.garfiec.librechat.core.ui.resources.date_group_previous_7_days
import com.garfiec.librechat.core.ui.resources.date_group_today
import com.garfiec.librechat.core.ui.resources.date_group_unknown
import com.garfiec.librechat.core.ui.resources.date_group_yesterday
import com.garfiec.librechat.core.ui.resources.timestamp_days_ago
import com.garfiec.librechat.core.ui.resources.timestamp_hours_ago
import com.garfiec.librechat.core.ui.resources.timestamp_just_now
import com.garfiec.librechat.core.ui.resources.timestamp_minutes_ago
import com.garfiec.librechat.core.ui.resources.timestamp_weeks_ago
import com.garfiec.librechat.core.ui.resources.timestamp_yesterday_at
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The localized text for a [TimestampLabel].
 *
 * Compute the label inside `remember` (it is pure) and call this outside it — string resources only
 * resolve in composition.
 */
@Composable
fun TimestampLabel.resolve(): String = when (this) {
    TimestampLabel.JustNow -> stringResource(Res.string.timestamp_just_now)
    is TimestampLabel.MinutesAgo -> pluralStringResource(Res.plurals.timestamp_minutes_ago, count, count)
    is TimestampLabel.HoursAgo -> pluralStringResource(Res.plurals.timestamp_hours_ago, count, count)
    is TimestampLabel.DaysAgo -> pluralStringResource(Res.plurals.timestamp_days_ago, count, count)
    is TimestampLabel.WeeksAgo -> pluralStringResource(Res.plurals.timestamp_weeks_ago, count, count)
    is TimestampLabel.Yesterday -> stringResource(Res.string.timestamp_yesterday_at, time)
    is TimestampLabel.Text -> text
}

/** The localized section header for a conversation-list [DateGroup]. */
@Composable
fun DateGroup.resolve(): String = when (this) {
    DateGroup.Today -> stringResource(Res.string.date_group_today)
    DateGroup.Yesterday -> stringResource(Res.string.date_group_yesterday)
    DateGroup.Previous7Days -> stringResource(Res.string.date_group_previous_7_days)
    DateGroup.Previous30Days -> stringResource(Res.string.date_group_previous_30_days)
    DateGroup.Unknown -> stringResource(Res.string.date_group_unknown)
    is DateGroup.Month -> {
        val format = LocalDateTimeFormat.current
        remember(this, format) { format.formatMonthYear(year, month, TimeZone.currentSystemDefault()) }
    }
}

/**
 * [iso] as a full date and time in the user's format, or [iso] itself when it doesn't parse — a
 * date we can't read is still better shown raw than blanked.
 */
@Composable
fun rememberAbsoluteTimestamp(iso: String): String {
    val format = LocalDateTimeFormat.current
    return remember(iso, format) { format.formatAbsoluteOrNull(iso) ?: iso }
}
