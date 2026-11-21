package com.garfiec.librechat.feature.chat.components

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.endpoint.KeyState
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

// Under `enforce` with `addedEndpoints: [openAI]`: the note heads the list; a spec nested in OpenAI's
// section above its greyed models; groups named `anthropic` and `agents` (not listed raw) read as
// "Anthropic" and "My Agents" with their endpoints' icons; a custom group keeps its folder; and a
// key-needed endpoint lists its spec beside "Add key".

@PreviewNarrowScreens
@Composable
private fun EnforcedSpecGroupsMaterialPreview() = EnforcedSpecGroupsHost(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun EnforcedSpecGroupsGlassPreview() = EnforcedSpecGroupsHost(UiStyle.LIQUID_GLASS)

@Composable
private fun EnforcedSpecGroupsHost(style: UiStyle) {
    fun spec(name: String, label: String, endpoint: String, group: String? = null, description: String? = null) =
        ModelSpec(
            name = name,
            label = label,
            description = description,
            group = group,
            preset = Preset(endpoint = endpoint, model = "model"),
        )
    val specs = ModelSpecs(
        list = listOf(
            spec("general", "General", "openAI", description = "Everyday questions"),
            spec("fast", "Fast", "openAI", group = "openAI", description = "Quick, cheap answers"),
            spec("groq-fast", "Groq Fast", "groq", group = "groq"),
            spec("claude-coder", "Claude Coder", "anthropic", group = "anthropic"),
            spec("helper", "Helper", "openAI", group = "agents"),
            spec("writer", "Writer", "openAI", group = "Writing"),
        ),
        enforce = true,
        addedEndpoints = listOf("openAI", "groq"),
    )
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Surface {
            ModelSelectorSheetContent(
                endpointConfigs = mapOf(
                    "openAI" to EndpointConfig(modelDisplayLabel = "OpenAI"),
                    "groq" to EndpointConfig(modelDisplayLabel = "Groq", userProvide = true),
                    "anthropic" to EndpointConfig(modelDisplayLabel = "Anthropic"),
                    "agents" to EndpointConfig(),
                ),
                availableModels = mapOf(
                    "openAI" to listOf("gpt-4o", "gpt-4o-mini"),
                    "groq" to listOf("llama-3.3-70b"),
                    "anthropic" to listOf("claude-sonnet"),
                ),
                agents = emptyList(),
                selectedEndpoint = "openAI",
                selectedModel = "model",
                onModelSelect = { _, _ -> },
                modelSelect = true,
                onSetApiKey = {},
                modelSpecs = specs,
                selectedSpec = "fast",
                onToggleSpecFavorite = {},
                endpointKeyStates = mapOf("groq" to KeyState.Unset),
            )
        }
    }
}
