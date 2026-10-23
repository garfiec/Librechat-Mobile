package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_change_compared_models
import org.jetbrains.compose.resources.stringResource

/**
 * The composer's view of an active comparison: both models, as the A·B badge pair inside the field.
 * [compact] (a phone, one pane at a time) draws [ComposerComparisonMorph] instead of
 * [ComposerComparisonBadges]: smaller badges that shrink into two dots once a draft is typed.
 */
@Immutable
data class ComposerComparison(
    val primaryLabel: String,
    val secondaryLabel: String,
    val onChangePrimary: () -> Unit,
    val onChangeSecondary: () -> Unit,
    val compact: Boolean,
) {
    fun collapsedFor(draft: String): Boolean = compact && draft.isNotEmpty()
}

/** What the composer's placeholder says about Compare. */
enum class ComposerComparisonHint {
    /** Not comparing: "Message <model>". */
    NONE,

    /** Setup is choosing model B; sends wait for it. */
    SETTING_UP,

    /** Comparing: one message goes to both. */
    BOTH,
}

/**
 * The A·B badge pair at the start of the composer's field while comparing side by side. A tap opens a
 * two-row menu, one row per model; a row opens that model's selector (which then shows the same A/B
 * pair as every other selector while comparing).
 */
@Composable
internal fun ComposerComparisonBadges(comparison: ComposerComparison, modifier: Modifier = Modifier) {
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
        LaneMenu(comparison, expanded = menuOpen, onDismiss = { menuOpen = false })
    }
}

/** 0 = badges, 1 = dots. Read it in a lambda, so a frame of the morph re-lays out but never recomposes. */
@Composable
internal fun animateComposerComparisonCollapse(collapsed: Boolean): State<Float> =
    animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "ComposerComparisonCollapse",
    )

/**
 * The field's `prefix` on a phone: room for the badges, shrinking to nothing as they become dots, so
 * the draft slides over rather than jumping. A prefix, not the leading icon, because M3 holds the
 * icon slot at 48dp or more and the prefix can reach zero.
 */
@Composable
internal fun ComposerComparisonReserve(collapse: () -> Float) {
    Spacer(
        Modifier.layout { measurable, constraints ->
            val width = constraints.constrainWidth((CompactReserve * (1f - collapse())).roundToPx())
            val placeable = measurable.measure(Constraints.fixed(width, 0))
            layout(width, 0) { placeable.place(0, 0) }
        },
    )
}

/**
 * The A·B pair on a phone, drawn over the field's start. With the field empty it is the badge pill,
 * whose tap opens the same two-row menu as [ComposerComparisonBadges]. As a draft is typed each badge
 * slides into the field's start padding and shrinks to a dot while the letters and pill fade; clearing
 * the field runs it back. The dots are decorative: the tab bar names both models, and the + sheet's
 * model page changes either.
 *
 * Centred on the draft's first line rather than the field, so a long draft doesn't carry the pair
 * down to its middle.
 */
@Composable
internal fun ComposerComparisonMorph(
    comparison: ComposerComparison,
    collapsed: Boolean,
    collapse: () -> Float,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val description = stringResource(Res.string.cd_change_compared_models)
    val pill = MaterialTheme.colorScheme.surfaceContainerHighest
    val firstLineCentre = FieldPadding + with(LocalDensity.current) {
        MaterialTheme.typography.bodyLarge.lineHeight.toDp() / 2
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(modifier.padding(start = PillStart, top = firstLineCentre - PillHeight / 2)) {
        Box(
            Modifier
                .size(PillWidth, PillHeight)
                .graphicsLayer { alpha = 1f - collapse() }
                .background(pill, CircleShape)
                .then(
                    if (collapsed) {
                        Modifier
                    } else {
                        Modifier
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = { menuOpen = true })
                            .semantics { contentDescription = description }
                    },
                ),
        )
        ComparisonLane.entries.forEachIndexed { index, lane ->
            MorphingLaneBadge(
                lane = lane,
                ring = pill,
                collapse = collapse,
                modifier = Modifier
                    .offset(x = PillInset + BadgeStep * index, y = PillInset)
                    .graphicsLayer {
                        val progress = collapse()
                        // From the badge's centre to its dot's, in the field's start padding.
                        val badgeCentre = PillStart + PillInset + CompactBadge / 2 + BadgeStep * index
                        val dx = (DotCentreX - badgeCentre).toPx() * progress
                        translationX = if (rtl) -dx else dx
                        translationY = (if (index == 0) -DotSpread else DotSpread).toPx() * progress
                        val scale = 1f - (1f - DotSize / CompactBadge) * progress
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }
        LaneMenu(comparison, expanded = menuOpen, onDismiss = { menuOpen = false })
    }
}

/**
 * A lane badge whose letter and pill-coloured ring fade as it shrinks, leaving a plain dot. The ring
 * is what makes B's overlap read as a cut-out in A, as in [ComposerComparisonBadges].
 */
@Composable
private fun MorphingLaneBadge(
    lane: ComparisonLane,
    ring: Color,
    collapse: () -> Float,
    modifier: Modifier = Modifier,
) {
    val accent = lane.accent
    Box(
        modifier = modifier
            .size(CompactBadge)
            .drawWithContent {
                drawCircle(accent)
                drawContent()
                val ringWidth = 2.dp.toPx()
                drawCircle(
                    color = ring,
                    radius = size.minDimension / 2 - ringWidth / 2,
                    alpha = 1f - collapse(),
                    style = Stroke(ringWidth),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Fixed-size circle, so the letter ignores font scale (see ComparisonLaneBadge).
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
            Text(
                text = lane.letter,
                color = lane.onAccent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer { alpha = (1f - 2f * collapse()).coerceAtLeast(0f) },
            )
        }
    }
}

@Composable
private fun LaneMenu(comparison: ComposerComparison, expanded: Boolean, onDismiss: () -> Unit) {
    AdaptiveDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        LaneMenuItem(ComparisonLane.A, comparison.primaryLabel) {
            onDismiss()
            comparison.onChangePrimary()
        }
        LaneMenuItem(ComparisonLane.B, comparison.secondaryLabel) {
            onDismiss()
            comparison.onChangeSecondary()
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

// Expanded: 24dp badges on a pill 8dp into the field, level with the wide badges' 8dp start.
// Collapsed: two 6dp dots stacked in the field's 16dp start padding.
private val FieldPadding = 16.dp
private val PillStart = 8.dp
private val PillInset = 4.dp
private val CompactBadge = 24.dp
private val BadgeStep = 18.dp
private val PillWidth = PillInset * 2 + CompactBadge + BadgeStep
private val PillHeight = PillInset * 2 + CompactBadge
private val DotSize = 6.dp
private val DotCentreX = 9.dp
private val DotSpread = 4.dp

// The text starts 4dp past the pill. The prefix already sits after the field's start padding and
// before M3's 2dp prefix gap, so the reserve is what's left.
private val CompactReserve = PillStart + PillWidth + 4.dp - FieldPadding - 2.dp
