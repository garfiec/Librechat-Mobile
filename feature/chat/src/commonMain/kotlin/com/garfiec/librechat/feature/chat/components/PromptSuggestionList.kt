package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveSuggestionChip
import com.garfiec.librechat.feature.chat.model.ComposerPickerItem
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_picker_leave_scope
import com.garfiec.librechat.feature.chat.resources.picker_chip_prompts
import com.garfiec.librechat.feature.chat.resources.picker_chip_skills
import com.garfiec.librechat.feature.chat.resources.picker_loading_skills
import com.garfiec.librechat.feature.chat.resources.picker_no_matches
import com.garfiec.librechat.feature.chat.resources.picker_scope_prompts
import com.garfiec.librechat.feature.chat.resources.picker_scope_skills
import com.garfiec.librechat.feature.chat.resources.picker_skills_failed
import com.garfiec.librechat.feature.chat.resources.picker_skills_unavailable
import com.garfiec.librechat.feature.chat.resources.picker_type_prompt
import com.garfiec.librechat.feature.chat.resources.picker_type_skill
import com.garfiec.librechat.feature.chat.resources.retry
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
 * The mixed list tags every row with its type and, when both types have items, opens with chips that
 * write a scope's command into the field ([onPickerTextChange]). A scoped list says which type it
 * searches, with a close that goes back to `/`, and drops the tags. With no rows the mixed list
 * says "No matches" when there was something to search ([hasPrompts], [hasSkills]) and stays hidden
 * otherwise, so a leading `/` in a chat with no prompts and no skills draws nothing; a scoped list
 * always answers. While the skill catalog loads ([isLoadingSkills]) a loading row closes a list that
 * holds skills; when it failed ([skillsLoadFailed]) a retry row replaces "No matches", which a
 * missing catalog can't vouch for.
 */
@Suppress("LongParameterList")
@Composable
fun PromptSuggestionList(
    picker: PickerQuery?,
    rows: List<ComposerPickerItem>,
    hasPrompts: Boolean,
    hasSkills: Boolean,
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
    val listsSkills = scope != PickerScope.PROMPTS
    val loading = listsSkills && isLoadingSkills
    val showFailed = listsSkills && skillsLoadFailed && !isLoadingSkills
    val skillsUnavailable = scope == PickerScope.SKILLS && !canPickSkills
    val showChips = scope == null && hasPrompts && hasSkills
    val showNoMatches = rows.isEmpty() && !loading && !showFailed && !skillsUnavailable &&
        (scope != null || hasPrompts || hasSkills)
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
                showChips = showChips,
                skillsUnavailable = skillsUnavailable,
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
    showChips: Boolean,
    skillsUnavailable: Boolean,
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
        if (showChips) {
            item(key = "chips", contentType = "picker-chips") {
                ScopeChips(onPickerTextChange = onPickerTextChange)
            }
        }
        items(rows, key = { it.key }, contentType = { "picker-row" }) { row ->
            PickerRow(row = row, tagged = tagged, onClick = { onSelect(row) })
        }
        if (skillsUnavailable) {
            item(key = "skills-unavailable", contentType = "picker-note") {
                PickerNote(text = stringResource(Res.string.picker_skills_unavailable))
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

@Composable
private fun ScopeChips(onPickerTextChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AdaptiveSuggestionChip(
            onClick = { onPickerTextChange("/prompt ") },
            label = { ChipLabel(stringResource(Res.string.picker_chip_prompts), command = "/prompt") },
        )
        AdaptiveSuggestionChip(
            onClick = { onPickerTextChange("$") },
            label = { ChipLabel(stringResource(Res.string.picker_chip_skills), command = "$") },
        )
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
    val icon: ImageVector
    val title: String
    val description: String?
    val typeLabel: String
    when (row) {
        is ComposerPickerItem.Prompt -> {
            icon = promptCategoryIcon(row.prompt.category)
            title = row.prompt.name
            // Falls back to the prompt body so a group with no oneliner still shows what it does —
            // the web client leaves this slot empty entirely.
            description = row.prompt.oneliner?.takeIf { it.isNotBlank() } ?: row.prompt.promptText
            typeLabel = stringResource(Res.string.picker_type_prompt)
        }
        is ComposerPickerItem.Skill -> {
            icon = SkillGlyph
            title = row.skill.title
            description = row.skill.description
            typeLabel = stringResource(Res.string.picker_type_skill)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
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
        // A mixed list tags each row: the two types sit together, and a pick does different things.
        if (tagged) {
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
