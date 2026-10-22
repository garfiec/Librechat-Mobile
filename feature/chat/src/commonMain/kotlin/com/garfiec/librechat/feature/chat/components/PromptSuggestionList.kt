package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.feature.chat.model.ComposerPickerItem
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.picker_loading_skills
import com.garfiec.librechat.feature.chat.resources.picker_no_matches
import com.garfiec.librechat.feature.chat.resources.picker_skills_failed
import com.garfiec.librechat.feature.chat.resources.picker_type_prompt
import com.garfiec.librechat.feature.chat.resources.picker_type_skill
import com.garfiec.librechat.feature.chat.resources.retry
import org.jetbrains.compose.resources.stringResource

private val MAX_LIST_HEIGHT = 220.dp

/**
 * The composer's `/` and `$` picker: prompts and skills in one list, each row labelled with its
 * type. See [buildPickerRows] for the order.
 *
 * Rendered inline in the composer column rather than as a popup or dropdown, so it participates in
 * the same insets as the input itself and cannot be clipped or hidden behind the keyboard.
 *
 * Shown while [isOpen]. With no rows it says "No matches" when there was something to search
 * ([hasCandidates]) and stays hidden otherwise, so a leading `/` in a chat with no prompts and no
 * skills draws nothing. While the skill catalog loads ([isLoadingSkills]) a loading row
 * closes the list. When it failed to load ([skillsLoadFailed]) a "Couldn't load skills" row with
 * a retry closes it instead, and replaces "No matches", which a missing catalog can't vouch for.
 */
@Composable
fun PromptSuggestionList(
    rows: List<ComposerPickerItem>,
    isOpen: Boolean,
    hasCandidates: Boolean,
    isLoadingSkills: Boolean,
    onSelect: (ComposerPickerItem) -> Unit,
    modifier: Modifier = Modifier,
    skillsLoadFailed: Boolean = false,
    onRetrySkills: () -> Unit = {},
) {
    if (!isOpen) return
    val showFailed = skillsLoadFailed && !isLoadingSkills
    val showNoMatches = rows.isEmpty() && hasCandidates && !isLoadingSkills && !showFailed
    if (rows.isEmpty() && !showNoMatches && !isLoadingSkills && !showFailed) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = ChatInputDefaults.containerColor,
        border = BorderStroke(1.dp, ChatInputDefaults.borderColor),
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = MAX_LIST_HEIGHT)) {
            items(rows, key = { it.key }, contentType = { "picker-row" }) { row ->
                PickerRow(row = row, onClick = { onSelect(row) })
            }
            if (showNoMatches) {
                item(key = "no-matches", contentType = "picker-note") {
                    PickerNote(text = stringResource(Res.string.picker_no_matches))
                }
            }
            if (isLoadingSkills) {
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
}

@Composable
private fun PickerRow(
    row: ComposerPickerItem,
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
            icon = Icons.Default.Extension
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
        // Every row says what it is: the list mixes the two, and a pick does different things.
        Text(
            text = typeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp),
        )
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
