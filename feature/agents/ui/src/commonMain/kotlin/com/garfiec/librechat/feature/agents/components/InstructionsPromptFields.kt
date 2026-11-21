package com.garfiec.librechat.feature.agents.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveExposedDropdownMenu
import com.garfiec.librechat.core.ui.components.AdaptiveExposedDropdownMenuBox
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveSegmentedChoice
import com.garfiec.librechat.feature.agents.resources.*
import com.garfiec.librechat.feature.agents.resources.Res
import com.garfiec.librechat.feature.agents.viewmodel.InstructionsPromptSelection
import com.garfiec.librechat.feature.agents.viewmodel.InstructionsPromptView
import com.garfiec.librechat.feature.agents.viewmodel.PromptGroupField
import com.garfiec.librechat.feature.agents.viewmodel.PromptLinkFailure
import com.garfiec.librechat.feature.agents.viewmodel.PromptVersionField
import com.garfiec.librechat.feature.agents.viewmodel.PromptVersionLabel
import org.jetbrains.compose.resources.stringResource

/** Inline or Prompt, for an agent whose instructions may link a prompt group (upstream `Instructions` toggle). */
@Composable
internal fun InstructionsSourceChoice(fromPrompt: Boolean, onSourceChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    AdaptiveSegmentedChoice(
        options = listOf(
            stringResource(Res.string.agent_instructions_source_inline),
            stringResource(Res.string.agent_instructions_source_prompt),
        ),
        selectedIndex = if (fromPrompt) 1 else 0,
        onSelect = { onSourceChange(it == 1) },
        modifier = modifier,
    )
}

/** The prompt group and revision a linked agent's instructions follow (upstream `InstructionsPromptFields`). */
@Composable
internal fun InstructionsPromptFields(
    view: InstructionsPromptView,
    onGroupSelect: (String) -> Unit,
    onVersionSelect: (InstructionsPromptSelection) -> Unit,
    onRetryGroups: () -> Unit,
    onRetryVersions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            val promptLabel = stringResource(Res.string.agent_instructions_source_prompt)
            when (val group = view.group) {
                PromptGroupField.Loading -> StaticField(promptLabel, stringResource(Res.string.agent_instructions_prompt_loading))
                PromptGroupField.Empty -> StaticField(promptLabel, stringResource(Res.string.agent_instructions_prompt_empty))
                is PromptGroupField.Failed -> LoadFailure(group.reason, onRetryGroups, groups = true)
                is PromptGroupField.Picker -> GroupPicker(promptLabel, group, onGroupSelect)
            }
            if (view.linkedGroupMissing) Note(stringResource(Res.string.agent_instructions_prompt_not_found))
            if (view.requiredError) {
                Note(stringResource(Res.string.agent_instructions_prompt_required), isError = true)
            }
        }
        val versionLabel = stringResource(Res.string.agent_instructions_prompt_version_label)
        when (val version = view.version) {
            PromptVersionField.NoGroup ->
                StaticField(versionLabel, stringResource(Res.string.agent_instructions_prompt_version_placeholder))
            PromptVersionField.Loading -> StaticField(versionLabel, stringResource(Res.string.agent_instructions_prompt_loading))
            PromptVersionField.Empty -> StaticField(versionLabel, stringResource(Res.string.agent_instructions_prompt_empty))
            is PromptVersionField.Failed -> LoadFailure(version.reason, onRetryVersions, groups = false)
            is PromptVersionField.Picker -> VersionPicker(versionLabel, version, onVersionSelect)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupPicker(label: String, field: PromptGroupField.Picker, onGroupSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    AdaptiveExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        AdaptiveOutlinedTextField(
            value = field.selected?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            placeholder = { Text(stringResource(Res.string.agent_instructions_prompt_select_placeholder)) },
            leadingIcon = if (field.selected?.isLangfuse == true) {
                { LangfuseBadge() }
            } else {
                null
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        AdaptiveExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            field.options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = if (option.isLangfuse) {
                        { LangfuseBadge() }
                    } else {
                        null
                    },
                    onClick = {
                        onGroupSelect(option.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VersionPicker(
    label: String,
    field: PromptVersionField.Picker,
    onVersionSelect: (InstructionsPromptSelection) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    AdaptiveExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        AdaptiveOutlinedTextField(
            value = field.options.getOrNull(field.selectedIndex)?.label?.let { versionLabelText(it) }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        AdaptiveExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            field.options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(versionLabelText(option.label)) },
                    onClick = {
                        onVersionSelect(option.selection)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun versionLabelText(label: PromptVersionLabel): String = when (label) {
    is PromptVersionLabel.Production -> label.version
        ?.let { stringResource(Res.string.agent_instructions_prompt_production_version, it) }
        ?: stringResource(Res.string.agent_instructions_prompt_production)
    is PromptVersionLabel.Version -> stringResource(Res.string.version_number, label.version)
    is PromptVersionLabel.Missing -> label.version
        ?.let { stringResource(Res.string.agent_instructions_prompt_langfuse_version_missing, it) }
        ?: stringResource(Res.string.agent_instructions_prompt_version_missing)
}

@Composable
private fun LangfuseBadge() {
    Icon(
        imageVector = Icons.Outlined.Cloud,
        contentDescription = stringResource(Res.string.agent_instructions_prompt_source_langfuse),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StaticField(label: String, value: String) {
    AdaptiveOutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        enabled = false,
        singleLine = true,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A failed read: permission failures can't be retried; [groups] picks the library's or a group's wording. */
@Composable
private fun LoadFailure(reason: PromptLinkFailure, onRetry: () -> Unit, groups: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val message = when {
            reason == PromptLinkFailure.FORBIDDEN -> Res.string.agent_instructions_prompt_forbidden
            reason == PromptLinkFailure.SOURCE_UNAVAILABLE && !groups -> Res.string.agent_instructions_prompt_source_unavailable
            else -> Res.string.agent_instructions_prompt_load_error
        }
        Text(
            text = stringResource(message),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (reason != PromptLinkFailure.FORBIDDEN) {
            AdaptiveOutlinedButton(onClick = onRetry) { Text(stringResource(Res.string.agents_retry)) }
        }
    }
}

@Composable
private fun Note(text: String, isError: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
    )
}
