package com.garfiec.librechat.feature.conversations.drawer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.garfiec.librechat.core.ui.glass.GlassControlColors
import com.garfiec.librechat.core.ui.theme.isLiquidGlass
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.chats
import com.garfiec.librechat.feature.conversations.resources.projects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Where the Library section is between its two panels: [progress] is 0 on Chats and 1 on Projects,
 * and in between while the toggle is dragged or settling. The toggle's thumb and both panels read it.
 */
@Stable
internal class LibrarySwitch(private val scope: CoroutineScope, tab: DrawerTab) {
    // Bounded, so neither a drag nor a flick's spring carries the thumb past the track's ends.
    val progress = Animatable(tab.position).apply { updateBounds(0f, 1f) }

    /** The tab the switch is resting on or settling toward. */
    var target by mutableStateOf(tab)
        private set

    /** The tab the current swap leaves; its panel fades out first. */
    var from by mutableStateOf(tab)
        private set

    var dragging = false
        private set

    val chatsShown by derivedStateOf { progress.value < 1f }
    val projectsShown by derivedStateOf { progress.value > 0f }

    fun startDrag() {
        dragging = true
        leaveRest()
    }

    fun drag(to: Float) {
        scope.launch { progress.snapTo(to) }
    }

    /** Springs to [tab]; [velocity] is in panels per second. */
    fun settle(tab: DrawerTab, velocity: Float = 0f) {
        leaveRest()
        dragging = false
        target = tab
        scope.launch { progress.animateTo(tab.position, SettleSpring, velocity) }
    }

    /** Jumps straight to [tab], for a change that didn't come from the toggle. */
    fun jumpTo(tab: DrawerTab) {
        target = tab
        from = tab
        scope.launch { progress.snapTo(tab.position) }
    }

    // A swap reversed midway keeps its outgoing panel, so neither panel's alpha jumps.
    private fun leaveRest() {
        when (progress.value) {
            0f -> from = DrawerTab.Chats
            1f -> from = DrawerTab.Projects
        }
    }
}

private val DrawerTab.position: Float get() = if (this == DrawerTab.Chats) 0f else 1f

private val SettleSpring =
    spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/**
 * Follows [selectedTab] when it changes from outside the toggle (restored from storage, say),
 * without animating: the restore lands after first composition, and a slide there reads as a swap.
 */
@Composable
internal fun rememberLibrarySwitch(selectedTab: DrawerTab): LibrarySwitch {
    val scope = rememberCoroutineScope()
    val switch = remember(scope) { LibrarySwitch(scope, selectedTab) }
    LaunchedEffect(switch, selectedTab) {
        if (!switch.dragging && switch.target != selectedTab) switch.jumpTo(selectedTab)
    }
    return switch
}

/**
 * Shared axis X for one panel: it slides [PanelShift] toward the side it leaves by and crossfades,
 * the outgoing panel fading over the first part of the swap and the incoming one after it.
 */
internal fun Modifier.libraryPanel(switch: LibrarySwitch, tab: DrawerTab, rtl: Boolean): Modifier =
    zIndex(if (tab == switch.from) 0f else 1f).graphicsLayer {
        val p = switch.progress.value
        val travel = if (switch.from == DrawerTab.Chats) p else 1f - p
        alpha = if (tab == switch.from) {
            1f - (travel / OUTGOING_FADE).coerceIn(0f, 1f)
        } else {
            ((travel - INCOMING_FADE_START) / (1f - INCOMING_FADE_START)).coerceIn(0f, 1f)
        }
        val away = if (tab == DrawerTab.Chats) -p else 1f - p
        translationX = away * PanelShift.toPx() * if (rtl) -1f else 1f
    }

private val PanelShift = 32.dp
private const val OUTGOING_FADE = 0.35f
private const val INCOMING_FADE_START = 0.2f

// Sliding pill toggle: the rounded track, its slightly-tighter moving thumb, and each icon+label
// cell's fixed size (equal widths so the thumb offset is a whole-cell step).
private val PillTrackShape = RoundedCornerShape(12.dp)
private val PillThumbShape = RoundedCornerShape(8.dp)
private val DrawerTabCellWidth = 88.dp
private val DrawerTabCellHeight = 34.dp

// A release this fast picks the side it's heading to, however far the thumb got.
private val FlingVelocity = 400.dp

/**
 * Sliding-pill toggle for the drawer's two list modes: a rounded track holding two equal-width
 * icon+label cells (chat / workspaces) with a highlighted thumb between them. Tapping a cell selects
 * it; dragging along the track scrubs the thumb and the panels below, and the release settles on the
 * nearer side (or the flung one). The drag is claimed at the touch slop, ahead of the drawer's swipe.
 */
@Composable
internal fun DrawerTabToggle(
    switch: LibrarySwitch,
    selectedTab: DrawerTab,
    onSelect: (DrawerTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = isLiquidGlass
    val thumbColor = if (glass) GlassControlColors.segmentThumb else MaterialTheme.colorScheme.secondaryContainer
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val cellPx = with(density) { DrawerTabCellWidth.toPx() }
    val flingPx = with(density) { FlingVelocity.toPx() }
    val currentSelected by rememberUpdatedState(selectedTab)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val latestRtl by rememberUpdatedState(rtl)
    val select = { tab: DrawerTab ->
        switch.settle(tab)
        if (tab != selectedTab) onSelect(tab)
    }
    Box(
        modifier = modifier
            .clip(PillTrackShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .pointerInput(switch) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var start = 0f
                    var slop = 0f
                    var at = 0f
                    val direction = if (latestRtl) -1f else 1f
                    val tracker = VelocityTracker()
                    tracker.addPointerInputChange(down)
                    var dragging = false
                    var settled = false
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPointerInputChange(change)
                            if (!change.pressed) break
                            val dx = (change.position.x - down.position.x) * direction
                            if (!dragging) {
                                if (abs(dx) <= viewConfiguration.touchSlop) continue
                                dragging = true
                                // Taken here, not at touch-down: a tap's settle may still be moving it.
                                start = switch.progress.value
                                // Fixed at the claim: re-signing it as dx crosses zero would jump the thumb.
                                slop = sign(dx) * viewConfiguration.touchSlop
                                switch.startDrag()
                            }
                            // Consumed here, before the drawer's swipe sees it; the cell's tap cancels too.
                            change.consume()
                            at = start + (dx - slop) / cellPx
                            switch.drag(at)
                        }
                        if (dragging) {
                            val velocity = tracker.calculateVelocity().x * direction
                            val tab = when {
                                velocity > flingPx -> DrawerTab.Projects
                                velocity < -flingPx -> DrawerTab.Chats
                                at >= 0.5f -> DrawerTab.Projects
                                else -> DrawerTab.Chats
                            }
                            switch.settle(tab, velocity / cellPx)
                            settled = true
                            if (tab != currentSelected) currentOnSelect(tab)
                        }
                    } finally {
                        // Cancelled mid-drag: never leave the thumb parked between the cells.
                        if (dragging && !settled) switch.settle(currentSelected)
                    }
                }
            }
            .padding(3.dp)
            .height(DrawerTabCellHeight),
    ) {
        Box(
            modifier = Modifier
                .width(DrawerTabCellWidth)
                .fillMaxHeight()
                // offset {} already mirrors x in RTL; negating it here would flip it twice.
                .offset { IntOffset((cellPx * switch.progress.value).roundToInt(), 0) }
                .then(if (glass) Modifier.shadow(2.dp, PillThumbShape) else Modifier)
                .clip(PillThumbShape)
                .background(thumbColor),
        )
        val onThumb = switch.progress.value.coerceIn(0f, 1f)
        Row {
            DrawerTabToggleCell(
                icon = Icons.AutoMirrored.Filled.Chat,
                label = stringResource(Res.string.chats),
                onThumb = 1f - onThumb,
                onClick = { select(DrawerTab.Chats) },
            )
            DrawerTabToggleCell(
                icon = Icons.Default.Workspaces,
                label = stringResource(Res.string.projects),
                onThumb = onThumb,
                onClick = { select(DrawerTab.Projects) },
            )
        }
    }
}

/** One icon+label cell of [DrawerTabToggle]; its tint follows how much of the thumb is under it. */
@Composable
private fun DrawerTabToggleCell(
    icon: ImageVector,
    label: String,
    onThumb: Float,
    onClick: () -> Unit,
) {
    val selectedColor =
        if (isLiquidGlass) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSecondaryContainer
    val contentColor = lerp(MaterialTheme.colorScheme.onSurfaceVariant, selectedColor, onThumb)
    Row(
        modifier = Modifier
            .width(DrawerTabCellWidth)
            .fillMaxHeight()
            .clip(PillThumbShape)
            // No ripple: the thumb is the feedback, and a ripple would flash under a drag's start.
            .clickable(interactionSource = null, indication = null, role = Role.Tab, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = contentColor,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
        )
    }
}
