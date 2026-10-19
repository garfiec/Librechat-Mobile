package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

// The OpenAI sheet: every control type, an "Unset" option, and a changed value so the per-row
// restore button shows.

@PreviewNarrowScreens
@Composable
private fun ModelParameterSheetMaterialPreview() = ModelParameterSheetHost(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun ModelParameterSheetGlassPreview() = ModelParameterSheetHost(UiStyle.LIQUID_GLASS)

@Composable
private fun ModelParameterSheetHost(style: UiStyle) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Surface {
            var parameters by remember {
                mutableStateOf(ModelParameters(temperature = 0.4f, dynamicValues = mapOf("stop" to "END\nSTOP")))
            }
            ModelParameterContent(
                parameters = parameters,
                onParametersChange = { parameters = it },
                selectedEndpoint = "openAI",
                selectedModel = "gpt-5",
                applyVerticalScroll = false,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

// An endpoint the registry has no entry for (a self-hosted one, say) falls back to the default set,
// whose Custom Name placeholder is the generic "Assistant" rather than a brand.

@PreviewNarrowScreens
@Composable
private fun DefaultEndpointSheetMaterialPreview() = DefaultEndpointSheetHost(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun DefaultEndpointSheetGlassPreview() = DefaultEndpointSheetHost(UiStyle.LIQUID_GLASS)

@Composable
private fun DefaultEndpointSheetHost(style: UiStyle) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Surface {
            var parameters by remember { mutableStateOf(ModelParameters()) }
            ModelParameterContent(
                parameters = parameters,
                onParametersChange = { parameters = it },
                selectedEndpoint = "Ollama",
                selectedModel = "llama3",
                applyVerticalScroll = false,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
