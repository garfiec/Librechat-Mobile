package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveSuggestionChip
import com.garfiec.librechat.core.ui.components.EndpointIcon
import com.garfiec.librechat.core.ui.components.InitialTile
import com.garfiec.librechat.core.ui.components.TileStatusDot
import com.garfiec.librechat.core.ui.mcp.mcpStatusColor
import com.garfiec.librechat.core.ui.mcp.mcpStatusLabel
import com.garfiec.librechat.feature.chat.model.ComposerPickerItem
import com.garfiec.librechat.feature.chat.model.McpPick
import com.garfiec.librechat.feature.chat.model.PickerMcpServer
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_picker_leave_scope
import com.garfiec.librechat.feature.chat.resources.cd_selected
import com.garfiec.librechat.feature.chat.resources.mcp_set_up_action
import com.garfiec.librechat.feature.chat.resources.mcp_sign_in_action
import com.garfiec.librechat.feature.chat.resources.model_selector_key_expired
import com.garfiec.librechat.feature.chat.resources.model_selector_my_agents
import com.garfiec.librechat.feature.chat.resources.model_selector_needs_key
import com.garfiec.librechat.feature.chat.resources.picker_chip_mcp
import com.garfiec.librechat.feature.chat.resources.picker_chip_models
import com.garfiec.librechat.feature.chat.resources.picker_chip_prompts
import com.garfiec.librechat.feature.chat.resources.picker_chip_skills
import com.garfiec.librechat.feature.chat.resources.picker_loading_skills
import com.garfiec.librechat.feature.chat.resources.picker_mcp_unavailable
import com.garfiec.librechat.feature.chat.resources.picker_models_comparing
import com.garfiec.librechat.feature.chat.resources.picker_models_unavailable
import com.garfiec.librechat.feature.chat.resources.picker_no_matches
import com.garfiec.librechat.feature.chat.resources.picker_scope_mcp
import com.garfiec.librechat.feature.chat.resources.picker_scope_models
import com.garfiec.librechat.feature.chat.resources.picker_scope_prompts
import com.garfiec.librechat.feature.chat.resources.picker_scope_skills
import com.garfiec.librechat.feature.chat.resources.picker_skills_failed
import com.garfiec.librechat.feature.chat.resources.picker_skills_unavailable
import com.garfiec.librechat.feature.chat.resources.picker_type_mcp
import com.garfiec.librechat.feature.chat.resources.picker_type_prompt
import com.garfiec.librechat.feature.chat.resources.picker_type_skill
import com.garfiec.librechat.feature.chat.resources.retry
import com.garfiec.librechat.feature.chat.util.HeaderLabel
import com.garfiec.librechat.feature.chat.util.KeyHint
import com.garfiec.librechat.feature.chat.util.ModelPickerBlock
import com.garfiec.librechat.feature.chat.util.PickerModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val MAX_LIST_HEIGHT = 220.dp
private val SCOPE_HEADER_HEIGHT = 48.dp

/**
 * The composer's `/` and `$` picker. See [parsePickerQuery] for what opens it and [buildPickerRows]
 * for what it lists.
 *
 * Rendered inline in the composer column rather than as a popup or dropdown, so it participates in
 * the same insets as the input itself and cannot be clipped or hidden behind the keyboard.
 *
 * The mixed list tags every row with its type and, when two or more types have items, opens with
 * a chip per type that writes its scope's command into the field ([onPickerTextChange]). Models
 * have a chip wherever they can be switched ([modelBlock] null) but rows only under `/model `. A
 * scoped list says which type it searches, with a close that goes back to `/`, and drops the type
 * tags (a model row keeps its section). With no rows the mixed list says "No matches" when there
 * was something to search ([hasPrompts], [hasSkills], [hasMcp]) and stays hidden otherwise, so a
 * leading `/` in a chat with nothing to pick draws nothing; a scoped list always answers. While the
 * skill catalog loads ([isLoadingSkills]) a loading row closes a list that holds skills; when it
 * failed ([skillsLoadFailed]) a retry row replaces "No matches", which a missing catalog can't vouch
 * for.
 */
@Suppress("LongParameterList")
@Composable
fun PromptSuggestionList(
    picker: PickerQuery?,
    rows: List<ComposerPickerItem>,
    hasPrompts: Boolean,
    hasSkills: Boolean,
    hasMcp: Boolean,
    modelBlock: ModelPickerBlock?,
    canPickSkills: Boolean,
    isLoadingSkills: Boolean,
    onSelect: (ComposerPickerItem) -> Unit,
    onPickerTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    skillsLoadFailed: Boolean = false,
    onRetrySkills: () -> Unit = {},
) {
    if (picker == null) return
    val scope = picker.scope
    val listsSkills = scope == null || scope == PickerScope.SKILLS
    val loading = listsSkills && isLoadingSkills
    val showFailed = listsSkills && skillsLoadFailed && !isLoadingSkills
    val unavailable = when (scope) {
        PickerScope.SKILLS -> Res.string.picker_skills_unavailable.takeIf { !canPickSkills }
        PickerScope.MCP -> Res.string.picker_mcp_unavailable.takeIf { !hasMcp }
        PickerScope.MODEL -> when (modelBlock) {
            ModelPickerBlock.NO_PICKER -> Res.string.picker_models_unavailable
            ModelPickerBlock.COMPARING -> Res.string.picker_models_comparing
            null -> null
        }
        else -> null
    }
    val chipTypes = ChipTypes(prompts = hasPrompts, skills = hasSkills, mcp = hasMcp, models = modelBlock == null)
        .takeIf { scope == null && it.count >= 2 }
    val showNoMatches = rows.isEmpty() && !loading && !showFailed && unavailable == null &&
        (scope != null || hasPrompts || hasSkills || hasMcp)
    if (scope == null && rows.isEmpty() && !showNoMatches && !loading && !showFailed) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = ChatInputDefaults.containerColor,
        border = BorderStroke(
            1.dp,
            if (scope != null) MaterialTheme.colorScheme.primary else ChatInputDefaults.borderColor,
        ),
    ) {
        Column {
            if (scope != null) ScopeHeader(scope = scope, onLeave = { onPickerTextChange("/") })
            PickerItems(
                picker = picker,
                rows = rows,
                tagged = scope == null,
                chipTypes = chipTypes,
                unavailable = unavailable,
                showNoMatches = showNoMatches,
                loading = loading,
                showFailed = showFailed,
                maxHeight = if (scope != null) MAX_LIST_HEIGHT - SCOPE_HEADER_HEIGHT else MAX_LIST_HEIGHT,
                onSelect = onSelect,
                onPickerTextChange = onPickerTextChange,
                onRetrySkills = onRetrySkills,
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun PickerItems(
    picker: PickerQuery,
    rows: List<ComposerPickerItem>,
    tagged: Boolean,
    chipTypes: ChipTypes?,
    unavailable: StringResource?,
    showNoMatches: Boolean,
    loading: Boolean,
    showFailed: Boolean,
    maxHeight: Dp,
    onSelect: (ComposerPickerItem) -> Unit,
    onPickerTextChange: (String) -> Unit,
    onRetrySkills: () -> Unit,
) {
    // Rows are keyed, so a better match ranked in above would otherwise open above the visible top.
    val listState = rememberLazyListState()
    LaunchedEffect(picker) { listState.scrollToItem(0) }
    LazyColumn(state = listState, modifier = Modifier.heightIn(max = maxHeight)) {
        if (chipTypes != null) {
            item(key = "chips", contentType = "picker-chips") {
                ScopeChips(types = chipTypes, onPickerTextChange = onPickerTextChange)
            }
        }
        items(rows, key = { it.key }, contentType = { "picker-row" }) { row ->
            PickerRow(row = row, tagged = tagged, onClick = { onSelect(row) })
        }
        if (unavailable != null) {
            item(key = "unavailable", contentType = "picker-note") {
                PickerNote(text = stringResource(unavailable))
            }
        }
        if (showNoMatches) {
            item(key = "no-matches", contentType = "picker-note") {
                PickerNote(text = stringResource(Res.string.picker_no_matches))
            }
        }
        if (loading) {
            item(key = "loading-skills", contentType = "picker-note") {
                PickerNote(text = stringResource(Res.string.picker_loading_skills), loading = true)
            }
        }
        if (showFailed) {
            item(key = "skills-failed", contentType = "picker-failed") {
                PickerFailedNote(onRetry = onRetrySkills)
            }
        }
    }
}

@Composable
private fun ScopeHeader(scope: PickerScope, onLeave: () -> Unit) {
    val (icon, label) = when (scope) {
        PickerScope.PROMPTS -> promptCategoryIcon(null) to stringResource(Res.string.picker_scope_prompts)
        PickerScope.SKILLS -> SkillGlyph to stringResource(Res.string.picker_scope_skills)
        PickerScope.MCP -> Icons.Default.Hub to stringResource(Res.string.picker_scope_mcp)
        PickerScope.MODEL -> Icons.Outlined.SmartToy to stringResource(Res.string.picker_scope_models)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SCOPE_HEADER_HEIGHT)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
                .semantics { heading() },
        )
        IconButton(onClick = onLeave) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(Res.string.cd_picker_leave_scope),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Which types the mixed list's chips offer: those with something to pick. */
private data class ChipTypes(val prompts: Boolean, val skills: Boolean, val mcp: Boolean, val models: Boolean) {
    val count: Int get() = listOf(prompts, skills, mcp, models).count { it }
}

@Composable
private fun ScopeChips(types: ChipTypes, onPickerTextChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (types.prompts) {
            AdaptiveSuggestionChip(
                onClick = { onPickerTextChange("/prompt ") },
                label = { ChipLabel(stringResource(Res.string.picker_chip_prompts), command = "/prompt") },
            )
        }
        if (types.skills) {
            AdaptiveSuggestionChip(
                onClick = { onPickerTextChange("$") },
                label = { ChipLabel(stringResource(Res.string.picker_chip_skills), command = "$") },
            )
        }
        if (types.mcp) {
            AdaptiveSuggestionChip(
                onClick = { onPickerTextChange("/mcp ") },
                label = { ChipLabel(stringResource(Res.string.picker_chip_mcp), command = "/mcp") },
            )
        }
        if (types.models) {
            AdaptiveSuggestionChip(
                onClick = { onPickerTextChange("/model ") },
                label = { ChipLabel(stringResource(Res.string.picker_chip_models), command = "/model") },
            )
        }
    }
}

@Composable
private fun ChipLabel(text: String, command: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, maxLines = 1)
        Text(
            text = command,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** How the field draws a scoped picker's command (see [pickerCommandRange]). */
@Composable
fun pickerCommandStyle(): SpanStyle = SpanStyle(
    color = MaterialTheme.colorScheme.primary,
    background = MaterialTheme.colorScheme.secondaryContainer,
    fontWeight = FontWeight.SemiBold,
)

@Composable
private fun PickerRow(
    row: ComposerPickerItem,
    tagged: Boolean,
    onClick: () -> Unit,
) {
    when (row) {
        is ComposerPickerItem.Prompt -> InsertRow(
            icon = promptCategoryIcon(row.prompt.category),
            title = row.prompt.name,
            // Falls back to the prompt body so a group with no oneliner still shows what it does —
            // the web client leaves this slot empty entirely.
            description = row.prompt.oneliner?.takeIf { it.isNotBlank() } ?: row.prompt.promptText,
            typeLabel = stringResource(Res.string.picker_type_prompt).takeIf { tagged },
            onClick = onClick,
        )
        is ComposerPickerItem.Skill -> InsertRow(
            icon = SkillGlyph,
            title = row.skill.title,
            description = row.skill.description,
            typeLabel = stringResource(Res.string.picker_type_skill).takeIf { tagged },
            onClick = onClick,
        )
        is ComposerPickerItem.Mcp -> McpPickerRow(mcp = row.mcp, tagged = tagged, onClick = onClick)
        is ComposerPickerItem.Model -> ModelPickerRow(model = row.model, onClick = onClick)
    }
}

/**
 * A spec, agent or model: its icon, its section at the end (as the sheet groups it), a star when
 * starred and a check on the chat's current one.
 */
@Composable
private fun ModelPickerRow(model: PickerModel, onClick: () -> Unit) {
    val keyText = when (model.keyHint) {
        KeyHint.NEEDS_KEY -> stringResource(Res.string.model_selector_needs_key)
        KeyHint.EXPIRED -> stringResource(Res.string.model_selector_key_expired)
        null -> null
    }
    val selectedLabel = stringResource(Res.string.cd_selected)
    PickerRowLayout(
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                selected = model.selected
            },
        leading = {
            val icon = model.icon
            if (icon != null) {
                EndpointIcon(
                    endpointName = icon.endpointName,
                    iconUrl = icon.imageUrl,
                    provider = icon.provider,
                    size = 20.dp,
                    contentDescription = null,
                    glyphTint = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Spacer(Modifier.size(20.dp))
            }
        },
        title = model.title,
        description = keyText ?: model.description,
        typeLabel = when (val group = model.group) {
            HeaderLabel.MyAgents -> stringResource(Res.string.model_selector_my_agents)
            is HeaderLabel.Text -> group.text
            HeaderLabel.Starred, null -> null
        },
        trailing = {
            if (model.starred) RowStateIcon(Icons.Default.Star)
            if (model.selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = selectedLabel,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp).size(18.dp),
                )
            }
        },
    )
}

/** A mixed list tags each row ([typeLabel]): the types sit together, and a pick does different things. */
@Composable
private fun InsertRow(icon: ImageVector, title: String, description: String?, typeLabel: String?, onClick: () -> Unit) {
    PickerRowLayout(
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics { role = Role.Button },
        leading = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        title = title,
        description = description,
        typeLabel = typeLabel,
    )
}

/**
 * A server's row: its connection under its name, and what a tap does at the end ("Sign in",
 * "Set up", a check while on, a lock when the spec keeps it on). A row whose tap would do nothing
 * ([McpPick.NONE]: locked and ready, or a sign-in already out) is disabled.
 */
@Composable
private fun McpPickerRow(mcp: PickerMcpServer, tagged: Boolean, onClick: () -> Unit) {
    val server = mcp.server
    val pick = mcp.pick
    val status = stringResource(mcpStatusLabel(server.status))
    val description = server.description?.takeIf { it.isNotBlank() }
    val ringColor = ChatInputDefaults.containerColor
    PickerRowLayout(
        modifier = Modifier
            .clickable(enabled = pick != McpPick.NONE, onClick = onClick)
            .semantics {
                if (pick == McpPick.TOGGLE || (pick == McpPick.NONE && mcp.locked)) {
                    role = Role.Checkbox
                    toggleableState = ToggleableState(mcp.on || mcp.locked)
                } else {
                    role = Role.Button
                }
                if (pick == McpPick.NONE) disabled()
            },
        leading = {
            InitialTile(seed = server.name, label = server.label, size = 20.dp) {
                TileStatusDot(color = mcpStatusColor(server.status), ringColor = ringColor)
            }
        },
        title = server.label,
        description = if (description != null) "$status · $description" else status,
        typeLabel = stringResource(Res.string.picker_type_mcp).takeIf { tagged },
        trailing = {
            when {
                pick == McpPick.SIGN_IN -> RowAction(stringResource(Res.string.mcp_sign_in_action))
                pick == McpPick.SET_UP -> RowAction(stringResource(Res.string.mcp_set_up_action))
                mcp.locked -> RowStateIcon(Icons.Default.Lock)
                mcp.on -> RowStateIcon(Icons.Default.Check, tint = MaterialTheme.colorScheme.primary)
            }
        },
    )
}

@Composable
private fun RowAction(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        modifier = Modifier.padding(start = 8.dp),
    )
}

@Composable
private fun RowStateIcon(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.padding(start = 8.dp).size(18.dp),
    )
}

@Composable
private fun PickerRowLayout(
    leading: @Composable () -> Unit,
    title: String,
    description: String?,
    typeLabel: String?,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
        if (typeLabel != null) {
            Text(
                text = typeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun PickerFailedNote(onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.picker_skills_failed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(Res.string.retry))
        }
    }
}

@Composable
private fun PickerNote(text: String, loading: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            AdaptiveCircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = if (loading) 12.dp else 0.dp),
        )
    }
}
