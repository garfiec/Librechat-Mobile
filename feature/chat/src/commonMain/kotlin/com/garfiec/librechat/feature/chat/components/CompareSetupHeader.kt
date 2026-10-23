package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cancel
import com.garfiec.librechat.feature.chat.resources.compare_lane_a
import com.garfiec.librechat.feature.chat.resources.compare_lane_b
import com.garfiec.librechat.feature.chat.resources.compare_pick_below
import com.garfiec.librechat.feature.chat.resources.compare_same_model
import com.garfiec.librechat.feature.chat.resources.compare_setup_hint
import com.garfiec.librechat.feature.chat.resources.compare_start
import com.garfiec.librechat.feature.chat.resources.compare_with
import org.jetbrains.compose.resources.stringResource

/**
 * The two sides of a comparison. Colours come from the colour scheme, so the user's accent,
 * dynamic colour and dark mode carry through in both interface styles.
 */
enum class ComparisonLane {
    A,
    B,
    ;

    val accent: Color
        @Composable get() = when (this) {
            A -> MaterialTheme.colorScheme.primary
            B -> MaterialTheme.colorScheme.tertiary
        }

    val onAccent: Color
        @Composable get() = when (this) {
            A -> MaterialTheme.colorScheme.onPrimary
            B -> MaterialTheme.colorScheme.onTertiary
        }

    val container: Color
        @Composable get() = when (this) {
            A -> MaterialTheme.colorScheme.primaryContainer
            B -> MaterialTheme.colorScheme.tertiaryContainer
        }

    val onContainer: Color
        @Composable get() = when (this) {
            A -> MaterialTheme.colorScheme.onPrimaryContainer
            B -> MaterialTheme.colorScheme.onTertiaryContainer
        }

    val letter: String
        @Composable get() = stringResource(if (this == A) Res.string.compare_lane_a else Res.string.compare_lane_b)
}

/** The lane's letter in a circle: filled once the lane has a model, outlined while it is empty. */
@Composable
fun ComparisonLaneBadge(
    lane: ComparisonLane,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    size: Dp = 24.dp,
    letterStyle: TextStyle = MaterialTheme.typography.labelMedium,
) {
    val badgeModifier = modifier
        .size(size)
        .clip(CircleShape)
        .then(
            if (filled) {
                Modifier.background(lane.accent)
            } else {
                Modifier.border(1.5.dp, lane.accent, CircleShape)
            },
        )
    Box(modifier = badgeModifier, contentAlignment = Alignment.Center) {
        // Fixed-size circle, so the letter ignores font scale (see InitialTile).
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
            Text(
                text = lane.letter,
                color = if (filled) lane.onAccent else lane.accent,
                style = letterStyle,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Top of the Compare setup: Cancel / title / Start, then the A-vs-B pairing. Shared by the phone
 * sheet and the wide-screen inline picker. Actions sit at the top in both interface styles, so
 * nothing here branches on style; the slots are flat fills, never a glass card, so a sheet that is
 * already glass stays one glass layer.
 *
 * The A and B slots are also the picker's targets: tapping one makes the list below edit that lane,
 * and [target] is drawn with a ring while the other dims.
 *
 * [pickLabel] is null until the user picks the second model; Start is disabled until then.
 */
@Suppress("LongParameterList")
@Composable
fun CompareSetupHeader(
    primaryLabel: String?,
    pickLabel: String?,
    isSameModel: Boolean,
    target: ComparisonLane,
    onSelectTarget: (ComparisonLane) -> Unit,
    onCancel: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    /** False holds Start off even once B is picked (a reply is still streaming). */
    canStart: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) }
            Text(
                text = stringResource(Res.string.compare_with),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            AdaptiveButton(onClick = onStart, enabled = pickLabel != null && canStart) {
                Text(stringResource(Res.string.compare_start))
            }
        }
        ComparisonPairContent(primaryLabel, pickLabel, isSameModel, target, onSelectTarget)
    }
}

/**
 * The A/B pair above any model list while comparing (the options sheet's Model page, the A and B
 * selector sheets), so changing a model looks the same everywhere as it does in setup. [target] is
 * the lane the list edits; it starts on the lane the selector was opened for.
 */
@Composable
fun ComparisonPairHeader(
    primaryLabel: String?,
    secondaryLabel: String?,
    isSameModel: Boolean,
    target: ComparisonLane,
    onSelectTarget: (ComparisonLane) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        ComparisonPairContent(primaryLabel, secondaryLabel, isSameModel, target, onSelectTarget)
    }
}

@Composable
private fun ComparisonPairContent(
    primaryLabel: String?,
    pickLabel: String?,
    isSameModel: Boolean,
    target: ComparisonLane,
    onSelectTarget: (ComparisonLane) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(Res.string.compare_setup_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Both slots take the taller one's height: B carries a caption when it repeats A's model.
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val slotModifier = Modifier
                .weight(1f)
                .fillMaxHeight()
            LaneSlot(
                lane = ComparisonLane.A,
                title = primaryLabel.orEmpty(),
                caption = null,
                selected = target == ComparisonLane.A,
                onSelect = { onSelectTarget(ComparisonLane.A) },
                modifier = slotModifier,
            )
            if (pickLabel != null) {
                LaneSlot(
                    lane = ComparisonLane.B,
                    title = pickLabel,
                    caption = if (isSameModel) stringResource(Res.string.compare_same_model) else null,
                    selected = target == ComparisonLane.B,
                    onSelect = { onSelectTarget(ComparisonLane.B) },
                    modifier = slotModifier,
                )
            } else {
                EmptyLaneSlot(
                    lane = ComparisonLane.B,
                    selected = target == ComparisonLane.B,
                    onSelect = { onSelectTarget(ComparisonLane.B) },
                    modifier = slotModifier,
                )
            }
        }
    }
}

private val SlotShape = RoundedCornerShape(16.dp)
private val SelectedRingWidth = 2.dp
private const val UNSELECTED_SLOT_ALPHA = 0.55f

/**
 * A slot as a picker target: tappable, announced as a tab, ringed in its lane colour when it is the
 * lane being edited and dimmed when it isn't.
 */
private fun Modifier.laneTarget(lane: Color, selected: Boolean, onSelect: () -> Unit): Modifier = this
    .alpha(if (selected) 1f else UNSELECTED_SLOT_ALPHA)
    .clip(SlotShape)
    .selectable(selected = selected, role = Role.Tab, onClick = onSelect)
    .then(if (selected) Modifier.border(SelectedRingWidth, lane, SlotShape) else Modifier)

@Composable
private fun LaneSlot(
    lane: ComparisonLane,
    title: String,
    caption: String?,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .laneTarget(lane.accent, selected, onSelect)
            .background(lane.container)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ComparisonLaneBadge(lane = lane, size = 28.dp)
        Column {
            Text(
                text = title,
                color = lane.onContainer,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            if (caption != null) {
                Text(
                    text = caption,
                    color = lane.onContainer,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyLaneSlot(lane: ComparisonLane, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .laneTarget(lane.accent, selected, onSelect)
            // Selected, the ring already outlines it.
            .then(if (selected) Modifier else Modifier.border(1.dp, lane.accent, SlotShape))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ComparisonLaneBadge(lane = lane, filled = false, size = 28.dp)
        Text(
            text = stringResource(Res.string.compare_pick_below),
            color = lane.accent,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
