package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.model.media.IconSource
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.feature.chat.util.HeaderLabel
import com.garfiec.librechat.feature.chat.util.KeyHint
import com.garfiec.librechat.feature.chat.util.ModelPick
import com.garfiec.librechat.feature.chat.util.ModelPickerBlock
import com.garfiec.librechat.feature.chat.util.PickerModel

// The composer's `/model ` list. Hosts are shared with SkillsChatPreviews.kt.

@PreviewNarrowScreens
@Composable
private fun PickerModelScopeMaterialPreview() = SkillsSurface(UiStyle.MATERIAL) {
    PickerHost("/model ", models = previewModels, modelBlock = null)
}

@PreviewNarrowScreens
@Composable
private fun PickerModelScopeGlassPreview() = SkillsSurface(UiStyle.LIQUID_GLASS) {
    PickerHost("/model ", models = previewModels, modelBlock = null)
}

@PreviewNarrowScreens
@Composable
private fun PickerMixedWithModelsChipMaterialPreview() = SkillsSurface(UiStyle.MATERIAL) {
    PickerHost("/", mcp = previewMcpServers, models = previewModels, modelBlock = null)
}

@PreviewNarrowScreens
@Composable
private fun PickerModelsComparingMaterialPreview() = SkillsSurface(UiStyle.MATERIAL) {
    PickerHost("/model ", modelBlock = ModelPickerBlock.COMPARING)
}

private fun previewModel(
    title: String,
    group: HeaderLabel?,
    endpoint: String,
    description: String? = null,
    selected: Boolean = false,
    starred: Boolean = false,
    keyHint: KeyHint? = null,
) = PickerModel(
    key = "$endpoint::$title",
    title = title,
    description = description,
    group = group,
    icon = IconSource(endpoint, null),
    selected = selected,
    starred = starred,
    keyHint = keyHint,
    pick = if (keyHint != null) ModelPick.AddKey(endpoint) else ModelPick.Raw(endpoint, title),
)

internal val previewModels = listOf(
    previewModel("claude-sonnet-4-5", HeaderLabel.Text("Anthropic"), "anthropic", starred = true),
    previewModel("Research assistant", null, "openAI", description = "Cites its sources", selected = true),
    previewModel("Helper", HeaderLabel.MyAgents, "agents"),
    previewModel("Writer", HeaderLabel.Text("Google"), "google", keyHint = KeyHint.NEEDS_KEY),
    previewModel("gpt-4o-mini", HeaderLabel.Text("OpenAI"), "openAI"),
)
