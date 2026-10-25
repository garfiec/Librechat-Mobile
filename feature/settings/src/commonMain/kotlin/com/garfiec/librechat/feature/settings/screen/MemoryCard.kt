package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.datetime.listLabel
import com.garfiec.librechat.core.common.datetime.parseIsoInstantOrNull
import com.garfiec.librechat.core.common.extensions.formatLocalizedNumber
import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.core.ui.components.AdaptiveCard
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.core.ui.components.MenuDragSelection
import com.garfiec.librechat.core.ui.components.dragSelectRowIcon
import com.garfiec.librechat.core.ui.components.menuDragAnchor
import com.garfiec.librechat.core.ui.components.menuDragTarget
import com.garfiec.librechat.core.ui.components.rememberMenuDragSelection
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.core.ui.datetime.LocalRelativeTimeReference
import com.garfiec.librechat.core.ui.datetime.resolve
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.action_delete
import com.garfiec.librechat.feature.settings.resources.cd_memory_options
import com.garfiec.librechat.feature.settings.resources.memory_action_edit
import com.garfiec.librechat.feature.settings.resources.memory_agent_fallback
import com.garfiec.librechat.feature.settings.resources.memory_agent_numbered
import com.garfiec.librechat.feature.settings.resources.memory_redacted_key
import com.garfiec.librechat.feature.settings.resources.memory_redacted_value
import com.garfiec.librechat.feature.settings.resources.memory_redacted_value_no_delete
import com.garfiec.librechat.feature.settings.resources.memory_token_count
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

internal val MemoryCardShape = RoundedCornerShape(16.dp)

/**
 * A content-filtered entry. The server blanks only the fields its filter matched, so this alone
 * doesn't say what is hidden: [hidesKey] and [hidesValue] do. Editing stays blocked either way.
 */
internal val Memory.isRedacted: Boolean get() = contentFilterBlocked == true

/** The filter withheld the key: it arrives blank, so the hidden-entry label stands in for it. */
internal val Memory.hidesKey: Boolean get() = isRedacted && key.isBlank()

/** The filter withheld the value: it arrives empty, so the hidden-content text stands in for it. */
internal val Memory.hidesValue: Boolean get() = isRedacted && value.isEmpty()

/** The name a prompt or menu uses for [memory]: its key, or the hidden-entry label when blanked. */
@Composable
internal fun memoryDisplayKey(memory: Memory): String =
    memory.key.ifBlank { stringResource(Res.string.memory_redacted_key) }

/**
 * An agent's label on its chip and cards: its resolved name, or a generic one when the agent isn't
 * visible to this user, numbered by [ordinal] when another agent's label would read the same.
 */
@Composable
internal fun memoryAgentLabel(agentName: String?, ordinal: Int?): String {
    val name = agentName?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.memory_agent_fallback)
    if (ordinal == null) return name
    val localeTag = LocalDateTimeFormat.current.localeTag
    val number = remember(ordinal, localeTag) { formatLocalizedNumber(ordinal.toDouble(), localeTag) }
    return stringResource(Res.string.memory_agent_numbered, name, number)
}

/**
 * What a hidden entry says in place of its value. It mentions Delete only when [canDelete]: a role
 * without UPDATE gets no Delete anywhere on the page.
 */
@Composable
internal fun redactedValueText(canDelete: Boolean): String = stringResource(
    if (canDelete) Res.string.memory_redacted_value else Res.string.memory_redacted_value_no_delete,
)

/**
 * One memory in the list: its key, two lines of value, and a meta row (owning agent, tokens, last
 * update). The tap opens it. The ⋮ menu (Edit, Delete) shows only with [canUpdate]; a redacted
 * entry's menu has Delete alone, since editing would save the blanks over what is stored.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MemoryCard(
    memory: Memory,
    canUpdate: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    agentOrdinal: Int? = null,
    deleting: Boolean = false,
) {
    val redacted = memory.isRedacted
    val format = LocalDateTimeFormat.current
    val reference = LocalRelativeTimeReference.current
    val updated = remember(memory.updatedAt, format, reference) {
        memory.updatedAt?.let(::parseIsoInstantOrNull)?.listLabel(format, reference)
    }
    AdaptiveCard(
        onClick = onOpen,
        enabled = !deleting,
        modifier = modifier.fillMaxWidth().alpha(if (deleting) DELETING_ALPHA else 1f),
        shape = MemoryCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = if (canUpdate) 4.dp else 16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = memoryDisplayKey(memory),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (memory.key.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (memory.hidesValue) redactedValueText(canDelete = canUpdate) else memory.value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = if (memory.hidesValue) FontStyle.Italic else null,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // Every slot keeps its height when short or empty, so cards in a grid row line up.
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                MemoryMetaRow(memory = memory, agentOrdinal = agentOrdinal, updated = updated?.resolve())
            }
            if (canUpdate) {
                MemoryMenuButton(
                    memoryLabel = memoryDisplayKey(memory),
                    onEdit = onEdit.takeUnless { redacted },
                    onDelete = onDelete,
                    enabled = !deleting,
                )
            }
        }
    }
}

/**
 * The agent, tokens and date on one line that is there even when empty. Items that don't fit are
 * dropped from the end rather than wrapped, so the agent stays and the date gives way first.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoryMetaRow(memory: Memory, agentOrdinal: Int?, updated: String?) {
    val tokens = memory.tokenCount
    // Agent-partitioned entries can share a key with a personal one, so the owner is always named,
    // the same way its filter chip is.
    val agentLabel = if (memory.agentId != null) memoryAgentLabel(memory.agentName, agentOrdinal) else null
    val localeTag = LocalDateTimeFormat.current.localeTag
    Box(Modifier.padding(top = 8.dp).heightIn(min = MetaIconSize), contentAlignment = Alignment.CenterStart) {
        // Holds a text line's height when no item is shown.
        Text(" ", style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.alpha(0f))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
            maxLines = 1,
        ) {
            if (agentLabel != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(MetaIconSize),
                    )
                    Text(
                        text = agentLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = MEMORY_AGENT_LABEL_MAX_WIDTH),
                    )
                }
            }
            if (tokens != null) {
                val formatted = remember(tokens, localeTag) { formatLocalizedNumber(tokens.toDouble(), localeTag) }
                MemoryMetaText(pluralStringResource(Res.plurals.memory_token_count, tokens, formatted))
            }
            if (updated != null) MemoryMetaText(updated)
        }
    }
}

@Composable
private fun MemoryMetaText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The agent mark; the meta row is never shorter than it. */
private val MetaIconSize = 16.dp

private val MenuIconSize = 24.dp
private val MenuGapBelowIcon = 12.dp

/**
 * A memory's ⋮ with its menu, as Projects' cards have it: a tap opens it, or a hold opens it and a
 * drag picks the item under the finger on release. A drag before the hold is left to the list, so
 * a scroll that starts on the button still scrolls. [onEdit] null leaves Edit out.
 */
@Composable
internal fun MemoryMenuButton(
    memoryLabel: String,
    onEdit: (() -> Unit)?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    buttonSize: Dp = 48.dp,
) {
    var expanded by remember { mutableStateOf(false) }
    val drag = rememberMenuDragSelection()
    Box(
        modifier = modifier.menuDragAnchor(
            drag,
            opensOnDrag = false,
            onOpen = { if (enabled) expanded = true },
            onCancel = { expanded = false },
        ),
    ) {
        IconButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.size(buttonSize)) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.cd_memory_options, memoryLabel))
        }
        AdaptiveDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            // The same gap below the icon whatever the button's size.
            offset = DpOffset(0.dp, (MenuGapBelowIcon - (buttonSize - MenuIconSize) / 2).coerceAtLeast(0.dp)),
            dragSelection = drag,
        ) {
            if (onEdit != null) {
                MemoryMenuItem(drag, Icons.Default.Edit, stringResource(Res.string.memory_action_edit)) {
                    expanded = false
                    onEdit()
                }
            }
            MemoryMenuItem(
                drag,
                Icons.Default.Delete,
                stringResource(Res.string.action_delete),
                tint = MaterialTheme.colorScheme.error,
            ) {
                expanded = false
                onDelete()
            }
        }
    }
}

@Composable
private fun MemoryMenuItem(
    dragSelection: MenuDragSelection?,
    icon: ImageVector,
    label: String,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label, color = tint) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                tint = tint.takeOrElse { LocalContentColor.current },
                modifier = Modifier.dragSelectRowIcon(),
            )
        },
        onClick = onClick,
        modifier = Modifier.menuDragTarget(dragSelection, onClick),
    )
}

/**
 * First-load stand-in, laid out like a card with a two-line value: the same padding, a line slot per
 * text line at its style's line height (so it follows the font scale as the card does), and the same
 * gaps, so the grid doesn't jump on arrival.
 */
@Composable
internal fun MemoryCardPlaceholder(modifier: Modifier = Modifier) {
    val typography = MaterialTheme.typography
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MemoryCardShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        PlaceholderLine(typography.titleSmall, widthFraction = KEY_FRACTION)
        Spacer(Modifier.height(4.dp))
        PlaceholderLine(typography.bodyMedium, widthFraction = VALUE_FRACTION)
        PlaceholderLine(typography.bodyMedium, widthFraction = VALUE_END_FRACTION)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.heightIn(min = MetaIconSize), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PlaceholderLine(typography.labelMedium, width = 64.dp)
            PlaceholderLine(typography.labelMedium, width = 48.dp)
        }
    }
}

/** One text line's slot at [style]'s line height, with a bar centred in it. */
@Composable
private fun PlaceholderLine(style: TextStyle, widthFraction: Float = 1f, width: Dp? = null) {
    val lineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
    val bar = Modifier
        .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth(widthFraction))
        .height(PLACEHOLDER_BAR_HEIGHT)
        .clip(RoundedCornerShape(4.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    Box(
        modifier = Modifier.height(lineHeight).then(if (width != null) Modifier else Modifier.fillMaxWidth()),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(bar)
    }
}

private val PLACEHOLDER_BAR_HEIGHT = 12.dp
private const val KEY_FRACTION = 0.5f
private const val VALUE_FRACTION = 0.9f
private const val VALUE_END_FRACTION = 0.6f

private const val DELETING_ALPHA = 0.5f

/** The widest an agent's name gets on its card and its filter chip before it ellipsizes. */
internal val MEMORY_AGENT_LABEL_MAX_WIDTH = 160.dp
