package com.garfiec.librechat.feature.agents.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.config.EndpointDropParams
import com.garfiec.librechat.core.ui.components.EndpointParameterRegistry
import com.garfiec.librechat.core.ui.components.ModelParameterContent
import com.garfiec.librechat.feature.agents.components.model.AgentAdvancedSettings
import com.garfiec.librechat.feature.agents.resources.*
import com.garfiec.librechat.feature.agents.resources.Res
import kotlinx.serialization.json.JsonElement
import org.jetbrains.compose.resources.stringResource

/**
 * Advanced (model parameter) section of the agent editor. Renders the same
 * dynamic parameter sheet used in chat (`ModelParameterContent`), routing
 * through the `agents` endpoint dispatcher so the controls match the
 * underlying provider/model — full parity with upstream's `ModelPanel`
 * driven by `agentParamSettings`.
 *
 * Typed slots (temperature / topP / maxTokens) bridge directly to
 * [AgentAdvancedSettings] fields; every other rendered key is preserved
 * in [AgentAdvancedSettings.extras] so unsupported / future server-set
 * keys survive a load → save round-trip.
 */
@Composable
fun AgentAdvancedPanel(
    settings: AgentAdvancedSettings,
    onSettingsChange: (AgentAdvancedSettings) -> Unit,
    provider: String,
    model: String,
    extendedEffortSupported: Boolean,
    modifier: Modifier = Modifier,
    dropParamsMap: Map<String, JsonElement>? = null,
) {
    val dropParams = remember(dropParamsMap, provider, model) {
        EndpointDropParams.resolve(dropParamsMap, endpoint = "agents", provider = provider, model = model)
    }
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.advanced_settings),
                style = MaterialTheme.typography.titleSmall,
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) {
                    stringResource(Res.string.cd_collapse)
                } else {
                    stringResource(Res.string.cd_expand)
                },
            )
        }

        AnimatedVisibility(visible = expanded) {
            val parameters = remember(settings) { settings.toModelParameters() }
            val definitions = remember(provider, model, extendedEffortSupported, dropParams) {
                EndpointParameterRegistry.getDefinitions(
                    endpoint = "agents",
                    extendedEffortSupported = extendedEffortSupported,
                    provider = provider.takeIf { it.isNotBlank() },
                    model = model.takeIf { it.isNotBlank() },
                    dropParams = dropParams,
                )
            }

            ModelParameterContent(
                parameters = parameters,
                onParametersChange = { updated ->
                    onSettingsChange(updated.toAgentAdvancedSettings(settings, definitions))
                },
                selectedEndpoint = "agents",
                selectedProvider = provider.takeIf { it.isNotBlank() },
                selectedModel = model.takeIf { it.isNotBlank() },
                dropParams = dropParams,
                extendedEffortSupported = extendedEffortSupported,
                showHeader = false,
                showSaveAsPreset = false,
                applyVerticalScroll = false,
            )
        }
    }
}
