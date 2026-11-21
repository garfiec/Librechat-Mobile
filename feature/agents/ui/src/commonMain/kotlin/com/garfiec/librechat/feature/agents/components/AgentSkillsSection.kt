package com.garfiec.librechat.feature.agents.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.SkillSummary
import com.garfiec.librechat.core.ui.components.AdaptiveCheckbox
import com.garfiec.librechat.core.ui.components.AdaptiveInputChip
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveSwitch
import com.garfiec.librechat.core.ui.components.InitialTile
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.core.ui.components.adaptiveSelectedRowColor
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.feature.agents.resources.*
import com.garfiec.librechat.feature.agents.resources.Res
import org.jetbrains.compose.resources.stringResource

/**
 * Agent skills selector (v0.8.6). Master `skills_enabled` toggle + a removable
 * chip per selected skill + "Add skills" picker. Mirrors upstream
 * `client/src/components/SidePanel/Agents/AgentConfig.tsx` (skills block).
 *
 * Empty allowlist + enabled = "full catalog" (the [hint] reflects this) — the
 * caller must NOT auto-clear [enabled] when the chip list is emptied.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentSkillsSection(
    enabled: Boolean,
    selectedSkillIds: List<String>,
    availableSkills: List<SkillSummary>,
    onToggle: (Boolean) -> Unit,
    onSkillToggle: (skillId: String) -> Unit,
    onSkillRemove: (skillId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    // _id -> display label map for resolving chips. Saved-but-unresolvable ids
    // (denied/empty catalog) fall back to the raw id so they never silently
    // drop or crash.
    val labelOf: (String) -> String = { id ->
        availableSkills.firstOrNull { it.id == id }?.skillLabel() ?: id
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // One toggleable row: the switch is named by its label, and the label takes the tap too.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(value = enabled, role = Role.Switch, onValueChange = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.label_skills),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                )
                AdaptiveSwitch(checked = enabled, onCheckedChange = null)
            }

            val hint = when {
                !enabled -> Res.string.skills_disabled_hint
                selectedSkillIds.isEmpty() -> Res.string.skills_enabled_all_hint
                else -> Res.string.skills_enabled_allowlist_hint
            }
            Text(
                text = stringResource(hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (enabled) {
                if (selectedSkillIds.isNotEmpty()) {
                    Spacer(modifier = Modifier.padding(top = 4.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        selectedSkillIds.forEach { id ->
                            val label = labelOf(id)
                            AdaptiveInputChip(
                                selected = false,
                                onClick = {},
                                label = { Text(label) },
                                trailingIcon = {
                                    IconButton(onClick = { onSkillRemove(id) }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(Res.string.cd_remove_item, label),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.padding(top = 4.dp))
                AdaptiveOutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.add_skills))
                }
            }
        }
    }

    if (showPicker) {
        SkillPickerSheet(
            availableSkills = availableSkills,
            selectedSkillIds = selectedSkillIds.toSet(),
            onToggle = onSkillToggle,
            onDismiss = { showPicker = false },
        )
    }
}

/**
 * Searchable multi-select sheet over the skill catalog. Tapping a row toggles the id in the
 * agent's allowlist and leaves the sheet open (matching upstream `SkillSelectDialog`); Done or a
 * dismiss closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SkillPickerSheet(
    availableSkills: List<SkillSummary>,
    selectedSkillIds: Set<String>,
    onToggle: (skillId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    AdaptiveModalBottomSheet(
        modifier = Modifier.clearFocusOnTap(),
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { LowProfileDragHandle() },
    ) {
        SkillPickerSheetContent(
            availableSkills = availableSkills,
            selectedSkillIds = selectedSkillIds,
            query = query,
            onQueryChange = { query = it },
            onToggle = onToggle,
            onDone = onDismiss,
        )
    }
}

@Composable
internal fun SkillPickerSheetContent(
    availableSkills: List<SkillSummary>,
    selectedSkillIds: Set<String>,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggle: (skillId: String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = remember(query, availableSkills) {
        if (query.isBlank()) {
            availableSkills
        } else {
            availableSkills.filter { skill ->
                skill.name.contains(query, ignoreCase = true) ||
                    skill.displayTitle?.contains(query, ignoreCase = true) == true
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.select_skills),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(onClick = onDone) {
                Text(stringResource(Res.string.skills_done))
            }
        }
        AdaptiveOutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(Res.string.search_skills_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        when {
            availableSkills.isEmpty() -> SkillPickerMessage(stringResource(Res.string.no_skills_available))
            filtered.isEmpty() -> SkillPickerMessage(stringResource(Res.string.no_skills_matching, query))
            else -> LazyColumn(
                // Shrinks to a short catalog; a long one scrolls inside the sheet's height.
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                contentPadding = PaddingValues(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(filtered, key = { it.id }) { skill ->
                    SkillPickerRow(
                        skill = skill,
                        checked = skill.id in selectedSkillIds,
                        onToggle = { onToggle(skill.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillPickerMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
    )
}

@Composable
private fun SkillPickerRow(
    skill: SkillSummary,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = skill.skillLabel()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(if (checked) adaptiveSelectedRowColor else Color.Transparent)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InitialTile(seed = skill.id, label = label)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val description = skill.description
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // The row is the toggle; the box only shows its state.
        AdaptiveCheckbox(checked = checked, onCheckedChange = null)
    }
}

private val RowShape = RoundedCornerShape(12.dp)

private fun SkillSummary.skillLabel(): String =
    displayTitle?.takeIf { it.isNotBlank() } ?: name
