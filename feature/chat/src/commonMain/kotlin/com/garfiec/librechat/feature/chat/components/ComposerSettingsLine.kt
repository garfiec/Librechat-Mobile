package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.model.usage.percentOf
import com.garfiec.librechat.core.ui.components.AdaptiveAssistChip
import com.garfiec.librechat.core.ui.components.AdaptiveChipDefaults
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.core.ui.components.AdaptiveFilterChip
import com.garfiec.librechat.core.ui.components.CornerStatusDot
import com.garfiec.librechat.core.ui.components.reasoningLevelLabel
import com.garfiec.librechat.core.ui.contextusage.pressureColor
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_context_ring
import com.garfiec.librechat.feature.chat.resources.cd_mcp_sign_in_menu
import com.garfiec.librechat.feature.chat.resources.cd_thinking_chip
import com.garfiec.librechat.feature.chat.resources.compact_nudge_action
import com.garfiec.librechat.feature.chat.resources.mcp_chip_cancel_sign_in
import com.garfiec.librechat.feature.chat.resources.mcp_chip_start_over
import com.garfiec.librechat.feature.chat.resources.mcp_sign_in_action
import com.garfiec.librechat.feature.chat.resources.setting_chip_connecting_suffix
import com.garfiec.librechat.feature.chat.resources.setting_chip_set_by
import com.garfiec.librechat.feature.chat.resources.setting_chip_sign_in
import com.garfiec.librechat.feature.chat.resources.thinking_chat_setting
import com.garfiec.librechat.feature.chat.resources.thinking_chip_next_message
import com.garfiec.librechat.feature.chat.resources.thinking_chip_state_next
import com.garfiec.librechat.feature.chat.resources.tool_set_by_spec
import com.garfiec.librechat.feature.chat.util.SettingChip
import com.garfiec.librechat.feature.chat.util.SettingChipState
import com.garfiec.librechat.feature.chat.util.SettingChipTap
import com.garfiec.librechat.feature.chat.util.SettingChips
import com.garfiec.librechat.feature.chat.util.ThinkingChip
import com.garfiec.librechat.feature.chat.util.tapFor
import com.garfiec.librechat.feature.chat.viewmodel.ContextGaugeDetails
import org.jetbrains.compose.resources.stringResource

/**
 * The composer's settings line: what is on for the next message, one chip per thing, on one line
 * that scrolls sideways and never wraps. [leading] comes first (the code workspace's chips), then
 * [chips] (tools, MCP servers, Thinking), with the context ring pinned at the end when [ring] is
 * set. A tap goes to [onChipTap]; the screen decides what it does ([SettingChips.tapFor]), except on
 * an MCP chip whose sign-in is pending, which opens its own menu ([onCancelSignIn],
 * [onStartSignInOver]). Draws nothing when it would hold nothing.
 */
@Composable
internal fun ComposerSettingsLine(
    chips: SettingChips,
    onChipTap: (SettingChip) -> Unit,
    modifier: Modifier = Modifier,
    /** "Cancel sign-in" in a connecting MCP chip's menu ([SettingChip.Mcp.signInPending]). */
    onCancelSignIn: (serverName: String) -> Unit = {},
    /** "Start over" in that menu. */
    onStartSignInOver: (serverName: String) -> Unit = {},
    ring: ContextRingState? = null,
    hasLeading: Boolean = false,
    leading: @Composable RowScope.() -> Unit = {},
) {
    if (chips.isEmpty && ring == null && !hasLeading) return
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val scroll = rememberScrollState()
        Row(
            modifier = Modifier
                .weight(1f)
                .fadeEndWhileScrollable(scroll)
                .horizontalScroll(scroll),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (hasLeading) leading()
            chips.chips.forEach { chip ->
                when (chip) {
                    is SettingChip.Tool -> ToolSettingChip(chip, chips.lockedBy, onClick = { onChipTap(chip) })
                    is SettingChip.Mcp -> McpSettingChip(
                        chip = chip,
                        lockedBy = chips.lockedBy,
                        opensMenu = chips.tapFor(chip) is SettingChipTap.SignInMenu,
                        onClick = { onChipTap(chip) },
                        onCancelSignIn = { onCancelSignIn(chip.name) },
                        onStartSignInOver = { onStartSignInOver(chip.name) },
                    )
                    is SettingChip.Thinking -> ThinkingSettingChip(chip.chip, onClick = { onChipTap(chip) })
                }
            }
        }
        if (ring != null) ContextRing(ring)
    }
}

/** What the context ring at the end of the settings line reads and does. */
internal data class ContextRingState(
    val details: ContextGaugeDetails,
    /** Null when the server does not offer compaction, or nothing can be compacted right now. */
    val onCompact: (() -> Unit)?,
    val onSnoozeCompact: () -> Unit,
)

/**
 * The ring for the composer's chrome: only with the above-the-field placement, a server that reports
 * context usage, and a reading with real usage. Other placements draw the gauge in the "+" sheet or
 * the ⋮ menu instead.
 */
internal fun ComposerChrome.contextRing(): ContextRingState? {
    val details = contextGauge ?: return null
    if (contextBarPlacement != ContextBarPlacement.ABOVE_INPUT || !contextUsageEnabled) return null
    if (details.usage.usedTokens <= 0) return null
    return ContextRingState(details, onCompact, onSnoozeCompact)
}

@Composable
private fun ToolSettingChip(chip: SettingChip.Tool, lockedBy: String?, onClick: () -> Unit) {
    val meta = ephemeralToolMeta(chip.key) ?: return
    val locked = chip.state == SettingChipState.LOCKED
    val lockState = if (locked) lockDescription(lockedBy) else null
    AdaptiveFilterChip(
        selected = chip.state != SettingChipState.PINNED_OFF,
        onClick = onClick,
        label = { Text(stringResource(meta.titleRes), maxLines = 1) },
        modifier = if (lockState != null) Modifier.semantics { stateDescription = lockState } else Modifier,
        colors = AdaptiveChipDefaults.overlayFilterChipColors(),
        leadingIcon = { ChipIcon(meta.icon) },
        trailingIcon = if (locked) ({ ChipIcon(Icons.Default.Lock) }) else null,
    )
}

/**
 * A selected MCP server with its status dot. A chip whose tap signs in reads "· sign in"; one whose
 * sign-in is pending ([opensMenu]) reads "· connecting" and opens a menu with "Cancel sign-in" and
 * "Start over" rather than going to [onClick].
 */
@Composable
private fun McpSettingChip(
    chip: SettingChip.Mcp,
    lockedBy: String?,
    opensMenu: Boolean,
    onClick: () -> Unit,
    onCancelSignIn: () -> Unit,
    onStartSignInOver: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val locked = chip.state == SettingChipState.LOCKED
    val connection = stringResource(mcpStatusLabel(chip.status))
    val state = if (locked) "$connection, ${lockDescription(lockedBy)}" else connection
    val dotColor = mcpStatusColor(chip.status)
    // A tap that does something other than switch the server off is named, for TalkBack as on screen.
    val suffix = when {
        opensMenu -> stringResource(Res.string.setting_chip_connecting_suffix)
        chip.signsInOnTap -> stringResource(Res.string.setting_chip_sign_in)
        else -> null
    }
    val actionLabel = when {
        opensMenu -> stringResource(Res.string.cd_mcp_sign_in_menu)
        chip.signsInOnTap -> stringResource(Res.string.mcp_sign_in_action)
        else -> null
    }
    val tap: () -> Unit = if (opensMenu) ({ menuOpen = true }) else onClick
    Box {
        AdaptiveFilterChip(
            selected = true,
            onClick = tap,
            label = {
                if (suffix != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // A width cap, not a weight: the line scrolls, so the chip is measured unbounded
                        // and a weighted child there gets 0 width, which would hide the server's name.
                        Text(
                            chip.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = McpNameMaxWidth),
                        )
                        Text(suffix, maxLines = 1)
                    }
                } else {
                    Text(chip.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = McpNameMaxWidth))
                }
            },
            modifier = Modifier.semantics {
                stateDescription = state
                if (actionLabel != null) {
                    onClick(label = actionLabel) {
                        tap()
                        true
                    }
                }
            },
            colors = AdaptiveChipDefaults.overlayFilterChipColors(),
            leadingIcon = {
                // Ringed in the chip's own content colour so the dot reads on the filled chip in both styles.
                CornerStatusDot(color = dotColor, size = 12.dp, ringColor = LocalContentColor.current)
            },
            trailingIcon = if (locked) ({ ChipIcon(Icons.Default.Lock) }) else null,
        )
        // Only while the sign-in still reads pending: a status reload that settles it takes the menu down.
        AdaptiveDropdownMenu(expanded = menuOpen && opensMenu, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.mcp_chip_cancel_sign_in)) },
                leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onCancelSignIn()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.mcp_chip_start_over)) },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onStartSignInOver()
                },
            )
        }
    }
}

/**
 * The per-turn Thinking chip: a button that opens the level sheet, showing the level the next message
 * will use. A one-turn choice tints it tertiary in both styles and says "next message".
 */
@Composable
private fun ThinkingSettingChip(chip: ThinkingChip, onClick: () -> Unit) {
    val level = reasoningLevelLabel(chip.shown, chip.unsetIsOff)
    val staged = chip.pending != null
    val label = if (staged) stringResource(Res.string.thinking_chip_next_message, level) else level
    val description = stringResource(Res.string.cd_thinking_chip, level)
    val state = stringResource(if (staged) Res.string.thinking_chip_state_next else Res.string.thinking_chat_setting)
    AdaptiveAssistChip(
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        modifier = Modifier.semantics {
            contentDescription = description
            stateDescription = state
        },
        colors = AdaptiveChipDefaults.overlayAssistChipColors(tint = if (staged) MaterialTheme.colorScheme.tertiary else null),
        border = AssistChipDefaults.assistChipBorder(
            enabled = true,
            borderColor = if (staged) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant,
        ),
        leadingIcon = { ChipIcon(Icons.Default.Psychology) },
        trailingIcon = { ChipIcon(Icons.Default.ArrowDropDown) },
    )
}

@Composable
private fun ChipIcon(icon: ImageVector) {
    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
}

/** "Set by <spec>", or the server's generic wording when the spec has no label. */
@Composable
private fun lockDescription(lockedBy: String?): String =
    lockedBy?.let { stringResource(Res.string.setting_chip_set_by, it) } ?: stringResource(Res.string.tool_set_by_spec)

/**
 * The context window as a 24dp ring with the percentage inside, tinted by [pressureColor], with a
 * tertiary dot while compacting is suggested. A tap opens the breakdown; while the suggestion is due
 * it opens the compact confirmation instead, which keeps "Not now".
 */
@Composable
internal fun ContextRing(state: ContextRingState, modifier: Modifier = Modifier) {
    var showSheet by remember { mutableStateOf(false) }
    var confirmCompact by remember { mutableStateOf(false) }
    val details = state.details
    val usage = details.usage
    val percent = percentOf(usage.usedTokens, usage.windowTokens)
    val nudge = details.nudge.takeIf { state.onCompact != null }
    val label = percentLabel(percent, details.isEstimate)
    val description = stringResource(Res.string.cd_context_ring, label)
    val nudgeState = if (nudge != null) stringResource(Res.string.compact_nudge_action) else null
    val tint = pressureColor(percent) ?: MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Button) { if (nudge != null) confirmCompact = true else showSheet = true }
            .semantics(mergeDescendants = true) {
                contentDescription = description
                if (nudgeState != null) stateDescription = nudgeState
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(RingSize)
                .background(AdaptiveChipDefaults.overlayContainerColor(), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AdaptiveCircularProgressIndicator(
                progress = { usage.usedFraction },
                modifier = Modifier.size(RingSize),
                color = tint,
                strokeWidth = 2.dp,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
            )
            // Sized to the ring rather than the font scale: the ring's description carries the reading.
            BasicText(
                text = label,
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface),
                autoSize = TextAutoSize.StepBased(minFontSize = 4.sp, maxFontSize = 8.sp),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (nudge != null) {
            CornerStatusDot(
                color = MaterialTheme.colorScheme.tertiary,
                size = 8.dp,
                ringColor = AdaptiveChipDefaults.overlayContainerColor(),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp),
            )
        }
    }
    if (showSheet) {
        ContextUsageSheet(details = details, onDismiss = { showSheet = false }, onCompact = state.onCompact)
    }
    val onCompact = state.onCompact
    if (confirmCompact && nudge != null && onCompact != null) {
        CompactConfirmDialog(
            percent = nudge.percent,
            onDismiss = { confirmCompact = false },
            onNotNow = {
                confirmCompact = false
                state.onSnoozeCompact()
            },
            onCompact = {
                confirmCompact = false
                onCompact()
            },
        )
    }
}

private val RingSize = 24.dp

/** The longest an MCP server's name gets on its chip before it ellipsizes. */
private val McpNameMaxWidth = 160.dp

/** Fades the line out toward its end while there is more to scroll to (the start, in RTL). */
private fun Modifier.fadeEndWhileScrollable(state: ScrollState): Modifier =
    graphicsLayer {
        compositingStrategy = if (state.maxValue > 0) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }.drawWithContent {
        drawContent()
        val extent = (state.maxValue - state.value).toFloat().coerceAtMost(FadeLength.toPx())
        if (state.maxValue <= 0 || extent <= 0f) return@drawWithContent
        val rtl = layoutDirection == LayoutDirection.Rtl
        val start = if (rtl) 0f else size.width - extent
        drawRect(
            brush = Brush.horizontalGradient(
                colors = if (rtl) listOf(Color.Transparent, Color.Black) else listOf(Color.Black, Color.Transparent),
                startX = start,
                endX = start + extent,
            ),
            topLeft = Offset(start, 0f),
            size = Size(extent, size.height),
            blendMode = BlendMode.DstIn,
        )
    }

private val FadeLength = 24.dp
