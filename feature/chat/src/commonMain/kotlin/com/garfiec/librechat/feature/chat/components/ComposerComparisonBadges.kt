package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_change_compared_models
import org.jetbrains.compose.resources.stringResource

/**
 * The A·B badge pair at the start of the composer's field when both panes are side by side. A tap
 * opens a two-row menu, one row per model; a row opens that model's selector (which then shows the
 * same A/B pair as every other selector while comparing).
 */
@Composable
internal fun ComposerComparisonBadges(comparison: ComposerComparison.BothModels, modifier: Modifier = Modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    val description = stringResource(Res.string.cd_change_compared_models)
    // The badges sit on a small pill; each badge's ring is the pill's colour, so the overlap reads as
    // a cut-out rather than one badge drawn over the other's letter.
    val pill = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier = modifier.padding(start = 8.dp, end = 4.dp)) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(pill)
                .clickable(role = Role.Button, onClick = { menuOpen = true })
                .semantics { contentDescription = description }
                .padding(start = 4.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((-6).dp),
        ) {
            ComparisonLaneBadge(
                lane = ComparisonLane.A,
                size = 28.dp,
                modifier = Modifier.border(2.dp, pill, CircleShape),
            )
            ComparisonLaneBadge(
                lane = ComparisonLane.B,
                size = 28.dp,
                modifier = Modifier.border(2.dp, pill, CircleShape),
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                // Clear of the overlap the arrangement applies between every child.
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(20.dp),
            )
        }
        AdaptiveDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            LaneMenuItem(ComparisonLane.A, comparison.primaryLabel) {
                menuOpen = false
                comparison.onChangePrimary()
            }
            LaneMenuItem(ComparisonLane.B, comparison.secondaryLabel) {
                menuOpen = false
                comparison.onChangeSecondary()
            }
        }
    }
}

@Composable
private fun LaneMenuItem(lane: ComparisonLane, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text = label, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
        leadingIcon = { ComparisonLaneBadge(lane = lane) },
        onClick = onClick,
    )
}
