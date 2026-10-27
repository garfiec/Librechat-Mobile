package com.garfiec.librechat.feature.chat.components

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

// The picker's spec groups: specs with no group on top, one nested in its endpoint's section, two
// headed groups after the endpoints (the selected spec's open, one closed), and a spec that hides
// its icon.

@PreviewNarrowScreens
@Composable
private fun SpecGroupsMaterialPreview() = SpecGroupsHost(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun SpecGroupsGlassPreview() = SpecGroupsHost(UiStyle.LIQUID_GLASS)

@Composable
private fun SpecGroupsHost(style: UiStyle) {
    fun spec(name: String, label: String, group: String? = null, description: String? = null, iconURL: String? = null) =
        ModelSpec(
            name = name,
            label = label,
            description = description,
            iconURL = iconURL,
            group = group,
            preset = Preset(endpoint = "anthropic", model = "claude-sonnet"),
        )
    val specs = ModelSpecs(
        list = listOf(
            spec("general", "General", description = "Everyday questions"),
            spec("quick", "Quick answers", iconURL = "openAI").copy(showIconInMenu = false),
            spec("claude-coder", "Claude Coder", group = "anthropic", description = "Pair programming"),
            spec("scholar", "Scholar", group = "Research", description = "Finds and cites sources"),
            spec("reviewer", "Paper reviewer", group = "Research"),
            spec("translator", "Translator", group = "Language"),
        ),
    )
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Surface {
            ModelSelectorSheetContent(
                endpointConfigs = mapOf("anthropic" to EndpointConfig(modelDisplayLabel = "Anthropic")),
                availableModels = mapOf("anthropic" to listOf("claude-sonnet", "claude-haiku")),
                agents = emptyList(),
                selectedEndpoint = "anthropic",
                selectedModel = "claude-sonnet",
                onModelSelect = { _, _ -> },
                modelSelect = true,
                onSetApiKey = {},
                modelSpecs = specs,
                selectedSpec = "scholar",
                onToggleSpecFavorite = {},
            )
        }
    }
}
