package com.garfiec.librechat.core.ui.components.topbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.LocalInSeparateWindow
import com.garfiec.librechat.core.ui.components.consumeUnhandledTouches
import com.garfiec.librechat.core.ui.glass.GlassBackdrop
import com.garfiec.librechat.core.ui.glass.GlassStyle
import com.garfiec.librechat.core.ui.glass.LocalGlassBackdrop
import com.garfiec.librechat.core.ui.glass.glassSurface
import com.garfiec.librechat.core.ui.glass.rememberGlassStyle
import com.garfiec.librechat.core.ui.platform.BarSide
import com.garfiec.librechat.core.ui.platform.LocalWindowFeatures
import com.garfiec.librechat.core.ui.platform.VerticalBars
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.topbar_more_options
import org.jetbrains.compose.resources.stringResource

private val RailItemSize = 44.dp
private val RailEndPadding = 8.dp
private val MaterialItemGap = 8.dp
private val GlassItemGap = 0.dp
private val RailGroupGap = 12.dp
private val ProminentGap = 8.dp
private val HeadingMinHeight = 56.dp

internal const val RAIL_OVERFLOW_ID = "rail_overflow"

/**
 * A top bar drawn down the window's vertical bar column (iPhone Duo): the navigation control, then
 * [AdaptiveTopBarSpec.actions] in order, a gap, and [AdaptiveTopBarSpec.bottomActions] at the bottom.
 * Text actions show their compact icons. When the column is too short, actions move into the ⋯ menu
 * (the spec's trailing menu, or a new one) from the last one up; the navigation control and the first
 * action always stay, and the bottom group gives way from its last item up when even they can't fit
 * beside it ([railGroups]). The first action is the primary one and is drawn apart and filled
 * ([railProminentAction]).
 *
 * Fills the column's height and keeps below the system zone at its top and above the home indicator,
 * wherever it is placed. Place it on [VerticalBars.side] with absolute alignment; the column ignores RTL.
 * [onBottomDrop] hears the ids of the bottom actions the rail leaves out, so the screen can show them itself.
 */
@Composable
fun AdaptiveSideRail(
    spec: AdaptiveTopBarSpec,
    bars: VerticalBars,
    modifier: Modifier = Modifier,
    onBottomDrop: (Set<String>) -> Unit = {},
) {
    val glass = rememberGlassStyle()
    val backdrop = LocalGlassBackdrop.current
    val overflowLabel = stringResource(Res.string.topbar_more_options)
    val density = LocalDensity.current
    // The zone is in window coordinates; a rail that starts lower clears less of it.
    var railTop by remember { mutableStateOf(0.dp) }
    val compact = spec.withCompactTextActions()
    val gap = if (glass != null) GlassItemGap else MaterialItemGap
    val onSurface = MaterialTheme.colorScheme.onSurface
    CompositionLocalProvider(LocalContentColor provides onSurface) {
        BoxWithConstraints(
            modifier
                // Read before the screen's body, as a bar's controls are; the screen's box scopes the order.
                .semantics {
                    isTraversalGroup = true
                    traversalIndex = -1f
                }
                .width(bars.columnWidth)
                .fillMaxHeight()
                .onPlaced { railTop = with(density) { it.positionInRoot().y.toDp() } }
                .padding(top = (bars.systemZoneHeight - railTop).coerceAtLeast(0.dp))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(bottom = RailEndPadding)
                .consumeUnhandledTouches(),
        ) {
            val prominent = railProminentAction(compact.actions)
            // Glass sets the primary action apart from the group's capsule; Material's items are already spaced.
            val prominentExtra = if (prominent != null) (ProminentGap - gap).coerceAtLeast(0.dp) else 0.dp
            val groups = railGroups(compact, maxHeight - prominentExtra, gap, overflowLabel)
            val top = groups.top
            val dropped = groups.dropped
            SideEffect { onBottomDrop(dropped) }
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                compact.navigation?.let { navigation ->
                    RailNavigation(navigation, glass, backdrop)
                    if (top.isNotEmpty()) Spacer(Modifier.height(RailGroupGap))
                }
                val rest = if (prominent != null) {
                    RailProminentAction(prominent, glass, backdrop)
                    top.drop(1)
                } else {
                    top
                }
                if (prominent != null && rest.isNotEmpty()) Spacer(Modifier.height(ProminentGap))
                RailGroup(rest, glass, backdrop, gap)
                Spacer(Modifier.weight(1f))
                RailGroup(groups.bottom, glass, backdrop, gap)
            }
        }
    }
}

@Composable
private fun RailNavigation(navigation: BarNavigation, glass: GlassStyle?, backdrop: GlassBackdrop?) {
    Box(
        modifier = Modifier.size(RailItemSize).then(
            if (glass != null) {
                Modifier.glassSurface(glass, CircleShape, backdrop)
            } else {
                Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = navigation.onClick) {
            Icon(navigation.icon.vector, contentDescription = navigation.contentDescription)
        }
    }
}

/**
 * The primary action: a circle filled in the accent while it can be tapped, apart from the group's
 * capsule in Glass. Disabled or busy, it takes the plain circle, so its spinner stays visible.
 */
@Composable
private fun RailProminentAction(action: BarAction.Icon, glass: GlassStyle?, backdrop: GlassBackdrop?) {
    val scheme = MaterialTheme.colorScheme
    val filled = action.enabled && !action.busy
    Box(
        modifier = Modifier.size(RailItemSize).then(
            when {
                filled -> Modifier.background(glass?.accent ?: scheme.primary, CircleShape)
                glass != null -> Modifier.glassSurface(glass, CircleShape, backdrop)
                else -> Modifier.background(scheme.surfaceContainerHigh, CircleShape)
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        val colors = if (filled) {
            BarActionColors(default = scheme.onPrimary, accent = scheme.onPrimary, glass = glass != null)
        } else {
            railActionColors(glass)
        }
        BarActionButton(action, colors)
    }
}

/**
 * The rail's primary action: the first action, which never overflows, when it is a plain button that
 * isn't destructive. A menu, toggle or live control first in line keeps the group's look.
 */
internal fun railProminentAction(actions: List<BarAction>): BarAction.Icon? =
    (actions.firstOrNull() as? BarAction.Icon)?.takeIf { it.tint != BarTint.DESTRUCTIVE }

@Composable
private fun railActionColors(glass: GlassStyle?): BarActionColors {
    val scheme = MaterialTheme.colorScheme
    return if (glass != null) {
        BarActionColors(default = scheme.onSurface, accent = scheme.primary, glass = true)
    } else {
        BarActionColors(default = Color.Unspecified, accent = scheme.primary)
    }
}

/** Glass groups the controls in one capsule, as the system's vertical bars do; Material gives each a tonal circle. */
@Composable
private fun RailGroup(actions: List<BarAction>, glass: GlassStyle?, backdrop: GlassBackdrop?, gap: Dp) {
    if (actions.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val colors = railActionColors(glass)
    Column(
        modifier = if (glass != null) Modifier.glassSurface(glass, CircleShape, backdrop) else Modifier,
        verticalArrangement = Arrangement.spacedBy(gap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        actions.forEach { action ->
            key(action.id) {
                Box(
                    modifier = Modifier.size(RailItemSize).then(
                        if (glass == null) Modifier.background(scheme.surfaceContainerHigh, CircleShape) else Modifier,
                    ),
                    contentAlignment = Alignment.Center,
                ) { BarActionButton(action, colors) }
            }
        }
    }
}

/**
 * How many top-group items (actions, or ⋯) fit in [available] once the navigation control and the
 * [bottomCount] bottom items have their room.
 */
@Suppress("LongParameterList") // Measures, kept apart so the arithmetic is testable.
internal fun railSlots(
    available: Dp,
    itemSize: Dp,
    itemGap: Dp,
    groupGap: Dp,
    hasNavigation: Boolean,
    bottomCount: Int,
): Int {
    var room = available
    if (hasNavigation) room -= itemSize + groupGap
    if (bottomCount > 0) room -= groupGap + itemSize * bottomCount + itemGap * (bottomCount - 1)
    if (room < itemSize) return 0
    return ((room + itemGap) / (itemSize + itemGap)).toInt()
}

/** What [AdaptiveSideRail] draws: the top group's actions and the bottom group's, and the ids of the bottom ones left out. */
internal class RailGroups(val top: List<BarAction>, val bottom: List<BarAction>, val dropped: Set<String>)

/**
 * The rail's groups in [available] height with [itemGap] between items. The bottom group keeps as many
 * items as fit beside the navigation control and the top group's least (the first action and ⋯); past
 * that its items drop from the last one up, and come back when the rail grows (as when the keyboard
 * closes): a live control there has no menu form to move into.
 */
internal fun railGroups(spec: AdaptiveTopBarSpec, available: Dp, itemGap: Dp, overflowLabel: String): RailGroups {
    val hasNavigation = spec.navigation != null
    var bottom = spec.bottomActions
    while (true) {
        val slots = railSlots(available, RailItemSize, itemGap, RailGroupGap, hasNavigation, bottom.size)
        val top = railTopActions(spec.actions, slots, overflowLabel)
        if (bottom.isEmpty() || railHeight(hasNavigation, top.size, bottom.size, itemGap) <= available) {
            return RailGroups(top, bottom, spec.bottomActions.drop(bottom.size).mapTo(HashSet()) { it.id })
        }
        bottom = bottom.dropLast(1)
    }
}

private fun railHeight(hasNavigation: Boolean, topCount: Int, bottomCount: Int, itemGap: Dp): Dp {
    fun stack(count: Int) = if (count == 0) 0.dp else RailItemSize * count + itemGap * (count - 1)
    var height = stack(topCount)
    if (hasNavigation) height += RailItemSize + if (topCount > 0) RailGroupGap else 0.dp
    if (bottomCount > 0) height += RailGroupGap + stack(bottomCount)
    return height
}

/**
 * The top group for [slots] places. Past that, actions move into a ⋯ menu from the last one up, the
 * ⋯ taking a place itself: the spec's trailing menu gains them as a first section, or a new menu
 * labelled [overflowLabel] holds them. The first action never moves, and a new menu that would hold a
 * single action isn't made, since it would save no room.
 */
internal fun railTopActions(actions: List<BarAction>, slots: Int, overflowLabel: String): List<BarAction> {
    if (actions.size <= slots) return actions
    val carrier = actions.last() as? BarAction.Menu
    val others = if (carrier != null) actions.dropLast(1) else actions
    val keep = (slots - 1).coerceIn(minOf(1, others.size), others.size)
    val overflow = others.drop(keep)
    if (overflow.isEmpty() || (carrier == null && overflow.size == 1)) return actions
    val sections = overflow.asMenuSections()
    val menu = carrier?.copy(sections = sections + carrier.sections)
        ?: BarAction.Menu(id = RAIL_OVERFLOW_ID, icon = BarIcons.More, label = overflowLabel, sections = sections)
    return others.take(keep) + menu
}

/** Plain actions become menu items in order; a menu among them brings its own sections. */
private fun List<BarAction>.asMenuSections(): List<BarMenuSection> {
    val sections = mutableListOf<BarMenuSection>()
    var items = mutableListOf<BarMenuItem>()
    fun flush() {
        if (items.isNotEmpty()) sections += BarMenuSection(items)
        items = mutableListOf()
    }
    forEach { action ->
        when (action) {
            is BarAction.Menu -> {
                flush()
                sections += action.sections
            }
            is BarAction.Icon -> items += BarMenuItem(
                id = action.id,
                label = action.label,
                icon = action.icon,
                onClick = action.onClick,
                destructive = action.tint == BarTint.DESTRUCTIVE,
                enabled = action.enabled && !action.busy,
            )
            is BarAction.Text -> items += BarMenuItem(
                id = action.id,
                label = action.accessibilityLabel ?: action.label,
                icon = action.compactIcon,
                onClick = action.onClick,
                enabled = action.enabled,
            )
            is BarAction.Toggle -> items += BarMenuItem(
                id = action.id,
                label = action.label,
                icon = if (action.checked) action.iconOn else action.iconOff,
                onClick = { action.onCheckedChange(!action.checked) },
                checked = action.checked,
            )
            // A live control has no menu form.
            is BarAction.Custom -> Unit
        }
    }
    flush()
    return sections
}

/**
 * Whether a layout [width] wide (its own, untransformed) whose left edge sits at [left] (px, window
 * coordinates, after any transition's offset or scale) reaches the window's [side]. A full-width
 * layout always does, so a screen scaled by a pop transition keeps its rail; otherwise only one lying
 * wholly inside the window short of that side is off it, so a screen sliding across an edge does too.
 */
internal fun reachesBarSide(side: BarSide, left: Float, width: Float, windowWidth: Float): Boolean {
    if (width >= windowWidth - EDGE_SLOP) return true
    val right = left + width
    return when (side) {
        BarSide.RIGHT -> left < -EDGE_SLOP || right >= windowWidth - EDGE_SLOP
        BarSide.LEFT -> right > windowWidth + EDGE_SLOP || left <= EDGE_SLOP
    }
}

private const val EDGE_SLOP = 1f

/**
 * Where a layout stands against the window's vertical bars, measured by [modifier] on that layout:
 * [bars] while it reaches their side (and isn't in a dialog or sheet window of its own), else null.
 */
internal class RailPlacement(val bars: VerticalBars?, val modifier: Modifier)

@Composable
internal fun rememberRailPlacement(): RailPlacement {
    val bars = LocalWindowFeatures.current.verticalBars
    // Unknown until placed; assumed, so a full-width screen doesn't open with a horizontal bar first.
    var atBarSide by remember { mutableStateOf(true) }
    val currentBars by rememberUpdatedState(bars)
    val modifier = if (bars != null) {
        remember {
            Modifier.onPlaced { coordinates ->
                val side = currentBars?.side ?: return@onPlaced
                val windowWidth = coordinates.findRootCoordinates().size.width.toFloat()
                atBarSide = reachesBarSide(side, coordinates.positionInRoot().x, coordinates.size.width.toFloat(), windowWidth)
            }
        }
    } else {
        Modifier
    }
    return RailPlacement(bars?.takeIf { atBarSide && !LocalInSeparateWindow.current }, modifier)
}

/** Keeps the rail's reading order inside the screen that draws it, ahead of its body but not of the window's other panes. */
internal fun railTraversalScope(railMode: Boolean): Modifier =
    if (railMode) Modifier.semantics { isTraversalGroup = true } else Modifier

/** Draws the rail for the bar in [slot] while there are [bars], unless a layout nested in this one draws its own. */
@Composable
internal fun BoxScope.SlotRail(slot: RailSlot, bars: VerticalBars?) {
    val spec = slot.spec
    val drawing = bars != null && spec != null && !slot.superseded
    val parent = slot.parent
    if (parent != null) {
        DisposableEffect(parent, drawing) {
            if (drawing) parent.addChildClaim()
            onDispose { if (drawing) parent.removeChildClaim() }
        }
    }
    if (drawing) {
        AdaptiveSideRail(
            spec = spec,
            bars = bars,
            modifier = Modifier.align(if (bars.side == BarSide.RIGHT) AbsoluteAlignment.TopRight else AbsoluteAlignment.TopLeft),
            onBottomDrop = { slot.droppedBottomIds = it },
        )
    }
}

/**
 * A Box for a screen whose top bar is an overlay in its body rather than an `AdaptiveScaffold` top bar.
 * While the window has vertical bars and the box reaches their side, an [AdaptiveTopBar] inside
 * [RailTopBar] draws only its title and hands its controls to an [AdaptiveSideRail] drawn over
 * [content]; [RailBoxScope.inRail] says so to the rest of the screen. Elsewhere it is a plain Box.
 */
@Composable
fun AdaptiveRailBox(modifier: Modifier = Modifier, content: @Composable RailBoxScope.() -> Unit) {
    val parent = LocalRailParent.current
    val slot = remember(parent) { RailSlot(parent) }
    val placement = rememberRailPlacement()
    Box(modifier.then(placement.modifier).then(railTraversalScope(placement.bars != null))) {
        val scope = RailBoxScope(this, slot, inRail = placement.bars != null && !slot.superseded)
        CompositionLocalProvider(LocalRailParent provides slot) { scope.content() }
        SlotRail(slot, placement.bars)
    }
}

@Stable
class RailBoxScope internal constructor(
    box: BoxScope,
    internal val slot: RailSlot,
    /** The box draws a rail: its top bar is a heading, and what the bar holds is in the rail. */
    val inRail: Boolean,
) : BoxScope by box {
    /** The rail holds the bottom action [id]: false once the rail is too short for it, so the screen shows it itself. */
    fun railShowsBottomAction(id: String): Boolean = inRail && id !in slot.droppedBottomIds
}

/** Hands the [AdaptiveTopBar] in [bar] to this box's rail while [RailBoxScope.inRail]; else it draws as usual. */
@Composable
fun RailBoxScope.RailTopBar(bar: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRailSlot provides slot.takeIf { inRail }, content = bar)
}

/**
 * One `AdaptiveScaffold`'s hold on the side rail: its top bar hands its spec here and draws a
 * heading, and the scaffold draws the rail. A scaffold nested in another's body is the [parent]'s
 * child; while a child's bar is in its rail, the parent is [superseded] and its bar stays horizontal,
 * so there is one rail and no bar loses its controls.
 */
@Stable
internal class RailSlot(val parent: RailSlot?) {
    private var childClaims by mutableIntStateOf(0)
    private var owner: Any? = null

    /** The spec of the bar whose controls the rail draws; null while no bar has handed one. */
    var spec by mutableStateOf<AdaptiveTopBarSpec?>(null)
        private set

    val superseded: Boolean get() = childClaims > 0

    /** The ids of the bottom actions the drawn rail has no room for; none until it measures. */
    var droppedBottomIds by mutableStateOf<Set<String>>(emptySet())

    fun claim(owner: Any, spec: AdaptiveTopBarSpec) {
        this.owner = owner
        this.spec = spec
    }

    fun release(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        spec = null
    }

    fun addChildClaim() {
        childClaims++
    }

    fun removeChildClaim() {
        childClaims--
    }
}

/** The slot of the scaffold whose top bar slot this is, while that scaffold draws a rail; else null. */
internal val LocalRailSlot = staticCompositionLocalOf<RailSlot?> { null }

/** The slot of the scaffold whose body this is, for a scaffold nested in it to report to. */
internal val LocalRailParent = staticCompositionLocalOf<RailSlot?> { null }

/**
 * True while the app's sidebar is open beside this content and a swipe closes it. A side rail then
 * leads with no drawer button: the drawer it would open is already on screen.
 */
val LocalSidebarOpen = compositionLocalOf { false }

/** A bar in rail mode: its spec goes to the rail; the title stays here as a heading, above [belowBar]. */
@Composable
internal fun RailHeading(
    spec: AdaptiveTopBarSpec,
    slot: RailSlot,
    materialStyle: MaterialBarStyle,
    belowBar: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val owner = remember { Any() }
    SideEffect { slot.claim(owner, spec) }
    DisposableEffect(slot) { onDispose { slot.release(owner) } }

    val glass = rememberGlassStyle()
    val surface = MaterialTheme.colorScheme.surface
    val background = when {
        glass != null -> Modifier.background(glassEdgeScrim())
        materialStyle.translucent -> Modifier.translucentBarScrim(surface)
        else -> Modifier.background(materialStyle.containerColor.takeOrElse { surface })
    }
    var renaming by remember { mutableStateOf(false) }
    // Content scrolls beneath the heading; a tap on it must not reach what it covers.
    Column(modifier.then(background).consumeUnhandledTouches()) {
        val title = spec.title
        val text = title.text
        if (text != null) {
            val onTitleClick = title.onClick ?: title.rename?.let { { renaming = true } }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // The status bar is in the column, so the heading starts at the window's top.
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .heightIn(min = HeadingMinHeight)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = if (title.alignment == BarTitleAlignment.CENTER) Alignment.Center else Alignment.CenterStart,
            ) {
                val chip = glass?.takeIf { title.style == BarTitleStyle.CHIP }
                Text(
                    text = text,
                    style = if (glass != null) glassTitleStyle() else MaterialTheme.typography.titleLarge,
                    maxLines = title.lines,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (title.alignment == BarTitleAlignment.CENTER) TextAlign.Center else null,
                    modifier = Modifier
                        .semantics { heading() }
                        .then(if (title.alignment == BarTitleAlignment.FILL) Modifier.fillMaxWidth() else Modifier)
                        .then(chip?.let { Modifier.glassSurface(it, CircleShape, LocalGlassBackdrop.current) } ?: Modifier)
                        .then(if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier)
                        .then(if (chip != null) Modifier.padding(horizontal = 16.dp, vertical = 12.dp) else Modifier),
                )
            }
        }
        belowBar?.invoke()
    }
    spec.title.rename?.let { rename ->
        if (renaming) BarRenameDialog(rename = rename, onDismiss = { renaming = false })
    }
}
