package com.garfiec.librechat.feature.agents.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ArtifactsMode
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveSegmentedChoice
import com.garfiec.librechat.core.ui.components.AdaptiveSwitch
import com.garfiec.librechat.feature.agents.components.model.AgentCapabilities
import com.garfiec.librechat.feature.agents.resources.*
import com.garfiec.librechat.feature.agents.resources.Res
import org.jetbrains.compose.resources.stringResource

@Composable
fun AgentCapabilitiesSection(
    capabilities: AgentCapabilities,
    onCapabilitiesChange: (AgentCapabilities) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.label_capabilities),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Artifacts: 4-option SegmentedButton (Off / Default / shadcnui / Custom)
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(
                text = stringResource(Res.string.label_artifacts),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(Res.string.artifacts_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            ArtifactsModePicker(
                selected = capabilities.artifactsMode,
                onSelect = { mode ->
                    onCapabilitiesChange(capabilities.copy(artifactsMode = mode))
                },
            )
        }

        CapabilityToggle(
            label = stringResource(Res.string.label_end_after_tools),
            description = stringResource(Res.string.end_after_tools_description),
            checked = capabilities.endAfterTools,
            onCheckedChange = {
                onCapabilitiesChange(capabilities.copy(endAfterTools = it))
            },
        )

        CapabilityToggle(
            label = stringResource(Res.string.label_hide_sequential_outputs),
            description = stringResource(Res.string.hide_sequential_description),
            checked = capabilities.hideSequentialOutputs,
            onCheckedChange = {
                onCapabilitiesChange(capabilities.copy(hideSequentialOutputs = it))
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(Res.string.label_recursion_limit),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(Res.string.recursion_limit_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            AdaptiveOutlinedTextField(
                value = capabilities.recursionLimit.toString(),
                onValueChange = { newValue ->
                    val filtered = newValue.filter { it.isDigit() }
                    val intVal = filtered.toIntOrNull() ?: 0
                    onCapabilitiesChange(
                        capabilities.copy(recursionLimit = intVal.coerceIn(1, 100)),
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ArtifactsModePicker(
    selected: ArtifactsMode?,
    onSelect: (ArtifactsMode?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Order: Off (null), Default, shadcnui, Custom
    val options: List<Pair<ArtifactsMode?, String>> = listOf(
        null to stringResource(Res.string.artifacts_mode_off),
        ArtifactsMode.DEFAULT to stringResource(Res.string.artifacts_mode_default),
        ArtifactsMode.SHADCN_UI to stringResource(Res.string.artifacts_mode_shadcnui),
        ArtifactsMode.CUSTOM to stringResource(Res.string.artifacts_mode_custom),
    )
    AdaptiveSegmentedChoice(
        options = options.map { it.second },
        selectedIndex = options.indexOfFirst { it.first == selected },
        onSelect = { onSelect(options[it].first) },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun CapabilityToggle(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AdaptiveSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
