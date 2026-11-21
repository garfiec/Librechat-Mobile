package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The two compared models' display labels. */
@Immutable
data class ComparisonModelLabels(val primary: String, val secondary: String)

/** "[A] model  [B] model" on one line, each name giving way before the other's badge does. */
@Composable
internal fun ComparisonModelsLine(
    labels: ComparisonModelLabels,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    badgeSize: Dp = 18.dp,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ComparisonLaneBadge(lane = ComparisonLane.A, size = badgeSize)
        LaneName(labels.primary, style, Modifier.weight(1f, fill = false))
        ComparisonLaneBadge(lane = ComparisonLane.B, size = badgeSize)
        LaneName(labels.secondary, style, Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun LaneName(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.MiddleEllipsis,
        modifier = modifier,
    )
}
