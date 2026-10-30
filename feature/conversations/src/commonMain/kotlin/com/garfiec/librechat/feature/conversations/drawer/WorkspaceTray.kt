package com.garfiec.librechat.feature.conversations.drawer

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.core.ui.components.DragSelectRowShape
import com.garfiec.librechat.core.ui.components.MenuDragSelection
import com.garfiec.librechat.core.ui.components.UpwardDrag
import com.garfiec.librechat.core.ui.components.adaptiveSelectedRowColor
import com.garfiec.librechat.core.ui.components.dragSelectRow
import com.garfiec.librechat.core.ui.components.dragSelectRowIcon
import com.garfiec.librechat.core.ui.components.menuDragAnchor
import com.garfiec.librechat.core.ui.components.menuDragPressEffect
import com.garfiec.librechat.core.ui.components.menuDragTarget
import com.garfiec.librechat.core.ui.components.rememberMenuDragSelection
import com.garfiec.librechat.core.ui.resources.bookmarks
import com.garfiec.librechat.core.ui.theme.isLiquidGlass
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.agents
import com.garfiec.librechat.feature.conversations.resources.files
import com.garfiec.librechat.feature.conversations.resources.pinned
import com.garfiec.librechat.feature.conversations.resources.project_move_down
import com.garfiec.librechat.feature.conversations.resources.project_move_up
import com.garfiec.librechat.feature.conversations.resources.schedules
import com.garfiec.librechat.feature.conversations.resources.skills
import com.garfiec.librechat.feature.conversations.resources.workspace
import com.garfiec.librechat.feature.conversations.resources.workspace_archived
import com.garfiec.librechat.feature.conversations.resources.workspace_collapsed
import com.garfiec.librechat.feature.conversations.resources.workspace_current
import com.garfiec.librechat.feature.conversations.resources.workspace_expanded
import com.garfiec.librechat.feature.conversations.resources.workspace_group_build
import com.garfiec.librechat.feature.conversations.resources.workspace_group_data
import com.garfiec.librechat.feature.conversations.resources.workspace_hint_agents
import com.garfiec.librechat.feature.conversations.resources.workspace_hint_archived
import com.garfiec.librechat.feature.conversations.resources.workspace_hint_bookmarks
import com.garfiec.librechat.feature.conversations.resources.workspace_hint_files
import com.garfiec.librechat.feature.conversations.resources.workspace_hint_mcp_servers
import com.garfiec.librechat.feature.conversations.resources.workspace_hint_memories
import com.garfiec.librechat.feature.conversations.resources.workspace_hint_prompts
import com.garfiec.librechat.feature.conversations.resources.workspace_hint_schedules
import com.garfiec.librechat.feature.conversations.resources.workspace_hint_shared_links
import com.garfiec.librechat.feature.conversations.resources.workspace_hint_skills
import com.garfiec.librechat.feature.conversations.resources.workspace_mcp_servers
import com.garfiec.librechat.feature.conversations.resources.workspace_memories
import com.garfiec.librechat.feature.conversations.resources.workspace_pin
import com.garfiec.librechat.feature.conversations.resources.workspace_prompts
import com.garfiec.librechat.feature.conversations.resources.workspace_reorder
import com.garfiec.librechat.feature.conversations.resources.workspace_shared_links
import com.garfiec.librechat.feature.conversations.resources.workspace_unpin
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.garfiec.librechat.core.ui.resources.Res as CoreRes

internal val WorkspaceDestination.icon: ImageVector
    get() = when (this) {
        WorkspaceDestination.AGENTS -> Icons.Default.SmartToy
        WorkspaceDestination.SKILLS -> Icons.Default.Extension
        WorkspaceDestination.PROMPTS -> Icons.Default.EditNote
        WorkspaceDestination.MCP_SERVERS -> Icons.Default.Hub
        WorkspaceDestination.SCHEDULES -> Icons.Default.Schedule
        WorkspaceDestination.MEMORIES -> Icons.Default.Psychology
        WorkspaceDestination.BOOKMARKS -> Icons.Default.Bookmark
        WorkspaceDestination.FILES -> Icons.Default.Folder
        WorkspaceDestination.SHARED_LINKS -> Icons.Default.Link
        WorkspaceDestination.ARCHIVED -> Icons.Default.Archive
    }

private val WorkspaceDestination.labelRes: StringResource
    get() = when (this) {
        WorkspaceDestination.AGENTS -> Res.string.agents
        WorkspaceDestination.SKILLS -> Res.string.skills
        WorkspaceDestination.PROMPTS -> Res.string.workspace_prompts
        WorkspaceDestination.MCP_SERVERS -> Res.string.workspace_mcp_servers
        WorkspaceDestination.SCHEDULES -> Res.string.schedules
        WorkspaceDestination.MEMORIES -> Res.string.workspace_memories
        WorkspaceDestination.BOOKMARKS -> CoreRes.string.bookmarks
        WorkspaceDestination.FILES -> Res.string.files
        WorkspaceDestination.SHARED_LINKS -> Res.string.workspace_shared_links
        WorkspaceDestination.ARCHIVED -> Res.string.workspace_archived
    }

private val WorkspaceDestination.hintRes: StringResource
    get() = when (this) {
        WorkspaceDestination.AGENTS -> Res.string.workspace_hint_agents
        WorkspaceDestination.SKILLS -> Res.string.workspace_hint_skills
        WorkspaceDestination.PROMPTS -> Res.string.workspace_hint_prompts
        WorkspaceDestination.MCP_SERVERS -> Res.string.workspace_hint_mcp_servers
        WorkspaceDestination.SCHEDULES -> Res.string.workspace_hint_schedules
        WorkspaceDestination.MEMORIES -> Res.string.workspace_hint_memories
        WorkspaceDestination.BOOKMARKS -> Res.string.workspace_hint_bookmarks
        WorkspaceDestination.FILES -> Res.string.workspace_hint_files
        WorkspaceDestination.SHARED_LINKS -> Res.string.workspace_hint_shared_links
        WorkspaceDestination.ARCHIVED -> Res.string.workspace_hint_archived
    }

@Composable
internal fun WorkspaceDestination.label(): String = stringResource(labelRes)

/**
 * The top of the drawer footer: the Workspace row, which opens the tray of [visible] destinations,
 * then the [pinned] ones as one-tap rows in the user's order. With a single visible destination the
 * row is that destination and there is no tray.
 *
 * [current] is the destination open in the tablet's content pane (null on a phone, or for a chat):
 * its pinned row is highlighted if pinned, else the Workspace row, showing its icon and name.
 */
@Composable
internal fun WorkspaceFooterRows(
    visible: List<WorkspaceDestination>,
    pinned: List<WorkspaceDestination>?,
    current: WorkspaceDestination?,
    onOpen: (WorkspaceDestination) -> Unit,
    onPin: (WorkspaceDestination) -> Unit,
    onUnpin: (WorkspaceDestination) -> Unit,
    onReorderPins: (List<WorkspaceDestination>) -> Unit,
    isOpen: Boolean = true,
) {
    val only = visible.singleOrNull()
    if (only != null) {
        DrawerFooterItem(
            icon = only.icon,
            label = only.label(),
            onClick = { onOpen(only) },
            selected = current == only,
        )
        return
    }
    val pins = pinned.orEmpty()
    WorkspaceRow(
        visible = visible,
        pinned = pins,
        current = current?.takeIf { it !in pins },
        onOpen = onOpen,
        onPin = onPin,
        onUnpin = onUnpin,
        onReorderPins = onReorderPins,
        isOpen = isOpen,
    )
    pins.forEach { destination ->
        DrawerFooterItem(
            icon = destination.icon,
            label = destination.label(),
            onClick = { onOpen(destination) },
            selected = current == destination,
        )
    }
}

@Composable
private fun WorkspaceRow(
    visible: List<WorkspaceDestination>,
    pinned: List<WorkspaceDestination>,
    current: WorkspaceDestination?,
    onOpen: (WorkspaceDestination) -> Unit,
    onPin: (WorkspaceDestination) -> Unit,
    onUnpin: (WorkspaceDestination) -> Unit,
    onReorderPins: (List<WorkspaceDestination>) -> Unit,
    isOpen: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    // The tray is an overlay, so it would outlive the drawer closing under it.
    LaunchedEffect(isOpen) { if (!isOpen) expanded = false }
    val drag = rememberMenuDragSelection()
    val workspace = stringResource(Res.string.workspace)
    val label = current?.let { stringResource(Res.string.workspace_current, it.label()) } ?: workspace
    // The chevron points to where the tray goes: up to open it, down to put it away.
    val chevronTurn by animateFloatAsState(if (expanded) 180f else 0f, label = "workspaceChevron")
    val stateText = stringResource(if (expanded) Res.string.workspace_expanded else Res.string.workspace_collapsed)
    // A hold or an upward drag opens the tray, toward where it opens; a sideways drag is left to the
    // drawer swipe and a downward one to the footer scroll.
    Box(
        modifier = Modifier.menuDragAnchor(
            drag,
            opensOnDragIf = UpwardDrag,
            onOpen = { expanded = true },
            onCancel = { expanded = false },
        ),
    ) {
        // Selected while its tray is open, and while the tablet shows one of its destinations.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (expanded || current != null) {
                        Modifier.background(adaptiveSelectedRowColor, DragSelectRowShape)
                    } else {
                        Modifier
                    },
                )
                .dragSelectRow(state = null, role = Role.Button) { expanded = !expanded }
                .semantics { stateDescription = stateText }
                .menuDragPressEffect(drag, swellDepth = ROW_SWELL)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = current?.icon ?: Icons.Default.Apps,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.ExpandLess,
                contentDescription = null,
                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = chevronTurn },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val scroll = rememberScrollState()
        // Opens scrolled to its bottom end, nearest the row, so a press-slide lands on a row.
        var scrolledForOpen by remember { mutableStateOf(false) }
        LaunchedEffect(expanded, scroll.maxValue) {
            if (!expanded) {
                scrolledForOpen = false
            } else if (!scrolledForOpen && scroll.maxValue > 0) {
                scroll.scrollTo(scroll.maxValue)
                scrolledForOpen = true
            }
        }
        // The tray anchors to this box, inset from the drawer's sides like the content above. The gap
        // above the row can't come from moving the anchor: the footer's scroll clips the anchor's
        // bounds at the divider. It is the menu's offset instead, whose sign the two styles read
        // oppositely when the menu opens upward, as it always does here.
        BoxWithConstraints(modifier = Modifier.matchParentSize().padding(horizontal = TrayInset)) {
            AdaptiveDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(maxWidth).heightIn(max = TrayMaxHeight),
                offset = DpOffset(x = 0.dp, y = if (isLiquidGlass) TrayGap else -TrayGap),
                scrollState = scroll,
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                dragSelection = drag,
            ) {
                val open: (WorkspaceDestination) -> Unit = { destination ->
                    expanded = false
                    onOpen(destination)
                }
                var first = true
                WorkspaceGroup.entries.forEach { group ->
                    val rows = visible.filter { it.group == group && it !in pinned }
                    if (rows.isEmpty()) return@forEach
                    if (!first) TrayDivider()
                    first = false
                    val header = if (group == WorkspaceGroup.BUILD) Res.string.workspace_group_build else Res.string.workspace_group_data
                    TrayHeader(stringResource(header))
                    rows.forEach { destination ->
                        TrayRow(
                            destination = destination,
                            selected = destination == current,
                            dragSelection = drag,
                            onClick = { open(destination) },
                            trailing = {
                                IconButton(onClick = { onPin(destination) }) {
                                    Icon(
                                        Icons.Outlined.PushPin,
                                        contentDescription = stringResource(Res.string.workspace_pin, destination.label()),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                }
                if (pinned.isNotEmpty()) {
                    if (!first) TrayDivider()
                    TrayHeader(stringResource(Res.string.pinned))
                    PinnedTrayRows(
                        pinned = pinned,
                        current = current,
                        dragSelection = drag,
                        scroll = scroll,
                        onOpen = open,
                        onUnpin = onUnpin,
                        onReorder = onReorderPins,
                    )
                }
            }
        }
    }
}

/**
 * The tray's Pinned group. Dragging a row's ≡ handle moves it; the order is local until the drop,
 * which reports it once. Screen readers get Move up / Move down instead.
 *
 * The tray only scrolls during a drag when the row is pulled to or past its top or bottom edge, faster
 * the further past, with the row kept under the finger; otherwise the drag never scrolls it.
 */
@Composable
private fun PinnedTrayRows(
    pinned: List<WorkspaceDestination>,
    current: WorkspaceDestination?,
    dragSelection: MenuDragSelection,
    scroll: ScrollState,
    onOpen: (WorkspaceDestination) -> Unit,
    onUnpin: (WorkspaceDestination) -> Unit,
    onReorder: (List<WorkspaceDestination>) -> Unit,
) {
    val order = remember(pinned) { pinned.toMutableStateList() }
    var dragging by remember { mutableStateOf<WorkspaceDestination?>(null) }
    // A released row easing from where it was dropped into its slot.
    var landing by remember { mutableStateOf<WorkspaceDestination?>(null) }
    var landingJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableIntStateOf(0) }
    // Each row's resting top in the tray's content, before any drag offset.
    val rowTops = remember { mutableMapOf<WorkspaceDestination, Float>() }
    val maxSpeed = with(LocalDensity.current) { AutoScrollMaxSpeed.toPx() }
    val moveUp = stringResource(Res.string.project_move_up)
    val moveDown = stringResource(Res.string.project_move_down)

    fun move(from: Int, to: Int) {
        if (from == to || to !in order.indices) return
        order.add(to, order.removeAt(from))
    }

    // Swaps the dragged row past a neighbour once it's over half of it.
    fun settle(destination: WorkspaceDestination) {
        if (rowHeight <= 0) return
        val at = order.indexOf(destination)
        // The held row stays within the Pinned group: from the first pinned slot to the last.
        dragOffset = dragOffset.coerceIn(-at * rowHeight.toFloat(), (order.lastIndex - at) * rowHeight.toFloat())
        val half = rowHeight / 2f
        if (dragOffset > half && at < order.lastIndex) {
            move(at, at + 1)
            dragOffset -= rowHeight
        } else if (dragOffset < -half && at > 0) {
            move(at, at - 1)
            dragOffset += rowHeight
        }
    }

    LaunchedEffect(dragging) {
        val destination = dragging ?: return@LaunchedEffect
        var lastFrame = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val seconds = (frame - lastFrame) / NANOS_PER_SECOND
            lastFrame = frame
            val viewport = scroll.viewportSize
            if (viewport == 0 || rowHeight == 0) continue
            val top = (rowTops[destination] ?: continue) + dragOffset - scroll.value
            val zone = rowHeight * AUTO_SCROLL_ZONE
            val past = when {
                top < zone -> top - zone
                top + rowHeight > viewport - zone -> top + rowHeight - (viewport - zone)
                else -> 0f
            }
            if (past == 0f) continue
            val consumed = scroll.scrollBy((past / rowHeight).coerceIn(-1f, 1f) * maxSpeed * seconds)
            // The content moved under the finger; move the row back under it.
            dragOffset += consumed
            settle(destination)
        }
    }

    order.forEachIndexed { index, destination ->
        // Keyed, so a row keeps its drag gesture as the rows reorder under it.
        key(destination) {
            val isDragging = dragging == destination
            val isMoving = isDragging || landing == destination
            // The tinted fill fades in on pick-up and out as the row lands.
            val lift by animateFloatAsState(
                if (isDragging) 1f else 0f,
                tween(LIFT_FADE_MILLIS),
                label = "pinLift",
            )
            val liftBase = MaterialTheme.colorScheme.surfaceContainerHigh
            val liftTint = adaptiveSelectedRowColor
            val actions = buildList {
                if (index > 0) {
                    add(CustomAccessibilityAction(moveUp) { move(index, index - 1); onReorder(order.toList()); true })
                }
                if (index < order.lastIndex) {
                    add(CustomAccessibilityAction(moveDown) { move(index, index + 1); onReorder(order.toList()); true })
                }
            }
            TrayRow(
                destination = destination,
                selected = destination == current,
                dragSelection = dragSelection,
                onClick = { onOpen(destination) },
                modifier = Modifier
                    .onSizeChanged { rowHeight = it.height }
                    .onPlaced { rowTops[destination] = it.positionInParent().y }
                    .zIndex(if (isMoving) 1f else 0f)
                    .graphicsLayer { if (isMoving) translationY = dragOffset }
                    // No shadow: the held row is a rounded, tinted fill in the press shape, opaque so the
                    // rows it passes don't show through it.
                    .clip(DragSelectRowShape)
                    .drawBehind {
                        if (lift > 0f) {
                            drawRect(liftBase, alpha = lift)
                            drawRect(liftTint, alpha = lift)
                        }
                    }
                    .semantics { customActions = actions },
                leading = {
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = stringResource(Res.string.workspace_reorder),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(24.dp)
                            .pointerInput(destination) {
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        landingJob?.cancel()
                                        landing = null
                                        dragging = destination
                                        dragOffset = 0f
                                    },
                                    onDragEnd = {
                                        if (order.toList() != pinned) onReorder(order.toList())
                                        dragging = null
                                        landing = destination
                                        landingJob = scope.launch {
                                            animate(dragOffset, 0f, animationSpec = tween(LAND_MILLIS)) { value, _ ->
                                                dragOffset = value
                                            }
                                            landing = null
                                        }
                                    },
                                    onDragCancel = {
                                        dragging = null
                                        dragOffset = 0f
                                    },
                                    onVerticalDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount
                                        settle(destination)
                                    },
                                )
                            },
                    )
                    Spacer(Modifier.width(8.dp))
                },
                trailing = {
                    IconButton(onClick = { onUnpin(destination) }) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = stringResource(Res.string.workspace_unpin, destination.label()),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun TrayRow(
    destination: WorkspaceDestination,
    selected: Boolean,
    dragSelection: MenuDragSelection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(
                    destination.label(),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(destination.hintRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        leadingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                leading?.invoke()
                Icon(destination.icon, contentDescription = null, modifier = Modifier.dragSelectRowIcon())
            }
        },
        trailingIcon = trailing,
        onClick = onClick,
        modifier = modifier
            .then(if (selected) Modifier.background(adaptiveSelectedRowColor, DragSelectRowShape) else Modifier)
            .menuDragTarget(dragSelection, onClick),
    )
}

@Composable
private fun TrayHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun TrayDivider() {
    AdaptiveDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

private val TrayMaxHeight = 480.dp
private val TrayInset = 12.dp
private val TrayGap = 16.dp

// Auto-scroll while reordering: starts a quarter row inside the tray's edge and speeds up to
// [AutoScrollMaxSpeed] a second (reached a full row past the edge), whatever the refresh rate.
private const val AUTO_SCROLL_ZONE = 0.25f
private const val LIFT_FADE_MILLIS = 150
private const val LAND_MILLIS = 200
private val AutoScrollMaxSpeed = 400.dp
private const val NANOS_PER_SECOND = 1_000_000_000f

// About 7dp a side on a drawer-wide row, a few dp past the press highlight's 4dp inset.
private const val ROW_SWELL = 0.05f
