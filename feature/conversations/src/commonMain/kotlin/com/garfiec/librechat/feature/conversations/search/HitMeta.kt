package com.garfiec.librechat.feature.conversations.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.datetime.listLabel
import com.garfiec.librechat.core.data.search.HitSource
import com.garfiec.librechat.core.data.search.MessageSearchHit
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.core.ui.datetime.LocalRelativeTimeReference
import com.garfiec.librechat.core.ui.datetime.resolve
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.cd_from_server
import com.garfiec.librechat.feature.conversations.resources.search_reasoning_badge
import com.garfiec.librechat.feature.conversations.resources.search_role_assistant
import com.garfiec.librechat.feature.conversations.resources.search_role_you
import com.garfiec.librechat.feature.conversations.resources.search_time_server
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

private val BadgeShape = RoundedCornerShape(50)
private val MetaIconSize = 14.dp

/**
 * A message hit's header line, shared by the drawer and the Search screen: who wrote it, whether
 * the match is in its reasoning, whether it came from the server, and when. [title] leads the line
 * where there's no conversation header above the row.
 */
@Composable
internal fun HitMeta(hit: MessageSearchHit, modifier: Modifier = Modifier, title: String? = null) {
    val variant = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        // The time is measured first and kept whole; the rest of the line shares what it leaves.
        if (title != null) {
            TitleFirstLine(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(" · ", style = MaterialTheme.typography.labelMedium, color = variant)
                HitTags(hit, titled = true)
            }
        } else {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (hit.isCreatedByUser) Icons.Default.Person else Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = variant,
                    modifier = Modifier.size(MetaIconSize),
                )
                Spacer(modifier = Modifier.width(6.dp))
                HitTags(hit, titled = false)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = hitTime(hit),
            style = MaterialTheme.typography.labelSmall,
            color = variant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * [title], then [tags] in a row. Nothing is cut while both fit; when they don't, the tags (whose
 * badge is the part that shrinks) give way until the title has a third of the line.
 */
@Composable
private fun TitleFirstLine(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    tags: @Composable RowScope.() -> Unit,
) {
    Layout(
        content = {
            title()
            Row(verticalAlignment = Alignment.CenterVertically, content = tags)
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val (titleMeasurable, tagsMeasurable) = measurables
        val width = constraints.maxWidth
        val titleFloor = minOf(titleMeasurable.maxIntrinsicWidth(constraints.maxHeight), width / 3)
        val tags = tagsMeasurable.measure(constraints.copy(minWidth = 0, maxWidth = width - titleFloor))
        val titlePlaceable = titleMeasurable.measure(constraints.copy(minWidth = 0, maxWidth = width - tags.width))
        val height = maxOf(titlePlaceable.height, tags.height)
        layout(width, height) {
            titlePlaceable.placeRelative(0, (height - titlePlaceable.height) / 2)
            tags.placeRelative(titlePlaceable.width, (height - tags.height) / 2)
        }
    }
}

/** Who wrote the hit, whether the match is in its reasoning, and whether it came from the server. */
@Composable
private fun RowScope.HitTags(hit: MessageSearchHit, titled: Boolean) {
    val variant = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = stringResource(if (hit.isCreatedByUser) Res.string.search_role_you else Res.string.search_role_assistant),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (titled) null else FontWeight.SemiBold,
        color = if (titled) variant else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
    if (hit.inReasoning) {
        Spacer(modifier = Modifier.width(6.dp))
        ReasoningBadge(modifier = Modifier.weight(1f, fill = false))
    }
    if (hit.source == HitSource.Remote) {
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Outlined.Cloud,
            contentDescription = stringResource(Res.string.cd_from_server),
            tint = variant,
            modifier = Modifier.size(MetaIconSize),
        )
    }
}

/** "Reasoning", with a bulb: the match is in the model's thinking, not its answer. */
@Composable
internal fun ReasoningBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.tertiaryContainer, BadgeShape)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Lightbulb,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(12.dp),
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = stringResource(Res.string.search_reasoning_badge),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** The hit's relative time, or "Server" for a server hit, which carries no timestamp. */
@Composable
private fun hitTime(hit: MessageSearchHit): String {
    val format = LocalDateTimeFormat.current
    val reference = LocalRelativeTimeReference.current
    val label = remember(hit.createdAt, format, reference) {
        hit.createdAt?.let { Instant.fromEpochMilliseconds(it).listLabel(format, reference) }
    }
    return label?.resolve() ?: stringResource(Res.string.search_time_server)
}
