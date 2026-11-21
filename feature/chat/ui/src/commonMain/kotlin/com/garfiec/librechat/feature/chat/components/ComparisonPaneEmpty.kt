package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.compare_empty_lane
import com.garfiec.librechat.feature.chat.resources.compare_empty_pick_right
import com.garfiec.librechat.feature.chat.resources.compare_empty_ready
import org.jetbrains.compose.resources.stringResource

/**
 * A comparison pane with no messages yet: the lane's badge and model, and what happens next.
 * [bottomPadding] keeps it centred in the part of the pane the composer doesn't cover.
 */
@Composable
internal fun ComparisonPaneEmpty(lane: ComparisonLane, label: String, bottomPadding: Dp, modifier: Modifier = Modifier) {
    EmptyPaneColumn(bottomPadding, modifier) {
        ComparisonLaneBadge(lane = lane, size = EmptyBadgeSize, letterStyle = MaterialTheme.typography.titleLarge)
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        EmptyPaneMessage(stringResource(Res.string.compare_empty_lane))
    }
}

/**
 * Pane A during a wide Compare setup on an empty chat. Before B is picked ([labels] null) it points
 * at the picker beside it; once both are chosen it shows the pair and says what Start does.
 */
@Composable
internal fun CompareSetupPaneEmpty(
    primaryLabel: String,
    labels: ComparisonModelLabels?,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    EmptyPaneColumn(bottomPadding, modifier) {
        if (labels == null) {
            ComparisonLaneBadge(lane = ComparisonLane.A, size = EmptyBadgeSize, letterStyle = MaterialTheme.typography.titleLarge)
            Text(
                text = primaryLabel,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            EmptyPaneMessage(stringResource(Res.string.compare_empty_pick_right))
        } else {
            ComparisonModelsLine(labels, style = MaterialTheme.typography.titleSmall, badgeSize = 24.dp)
            EmptyPaneMessage(stringResource(Res.string.compare_empty_ready))
        }
    }
}

@Composable
private fun EmptyPaneColumn(bottomPadding: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // Centred while it fits; scrolls when it doesn't (keyboard up, short window).
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = bottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun EmptyPaneMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

private val EmptyBadgeSize = 48.dp
