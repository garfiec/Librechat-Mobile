package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.ChatLayoutConstants
import com.garfiec.librechat.core.data.datastore.ArtifactDisplayMode
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.ChatHeaderAlignment
import com.garfiec.librechat.core.data.datastore.ChatHeaderContent
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.core.data.datastore.InlineArtifactPrefs
import com.garfiec.librechat.core.data.datastore.LatexRenderer
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.data.datastore.UploadRoutingMode
import com.garfiec.librechat.core.data.datastore.WebSearchPreference
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveRadioButton
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.components.ArtifactPresentation
import com.garfiec.librechat.core.ui.components.windowAutomaticArtifactPresentation
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import org.jetbrains.compose.resources.stringResource

internal enum class ChatSettingDialog {
    LATEX_RENDERER,
    DURING_RUN_ACTION,
    UPLOAD_ROUTING,
    WEB_SEARCH_FIRST,
    ARTIFACTS_MODE,
    RENDER_INLINE,
    ARTIFACT_LIBRARIES,
    STT_ENGINE,
}

// Option label resolvers, shared by the settings rows (current-value subtitle)
// and the selection dialogs.

@Composable
internal fun chatLayoutLabel(value: String): String = when (value) {
    ChatLayoutConstants.TWO_SIDED -> stringResource(Res.string.chat_layout_two_sided)
    else -> stringResource(Res.string.chat_layout_thread)
}

@Composable
internal fun fontSizeLabel(size: ChatFontSize): String = when (size) {
    ChatFontSize.SMALL -> stringResource(Res.string.font_size_small)
    ChatFontSize.MEDIUM -> stringResource(Res.string.font_size_medium)
    ChatFontSize.LARGE -> stringResource(Res.string.font_size_large)
}

@Composable
internal fun latexRendererLabel(renderer: LatexRenderer): String = when (renderer) {
    LatexRenderer.KATEX -> stringResource(Res.string.latex_katex)
    LatexRenderer.NATIVE -> stringResource(Res.string.latex_native)
}

@Composable
internal fun contextBarPlacementLabel(placement: ContextBarPlacement): String = when (placement) {
    ContextBarPlacement.HIDDEN -> stringResource(Res.string.context_bar_hidden)
    ContextBarPlacement.ABOVE_INPUT -> stringResource(Res.string.context_bar_above_input)
    ContextBarPlacement.OPTIONS_SHEET -> stringResource(Res.string.context_bar_options_sheet)
    ContextBarPlacement.OVERFLOW_MENU -> stringResource(Res.string.context_bar_overflow_menu)
}

@Composable
internal fun duringRunActionLabel(action: DuringRunAction): String = when (action) {
    DuringRunAction.QUEUE -> stringResource(Res.string.during_run_action_queue)
    DuringRunAction.STEER -> stringResource(Res.string.during_run_action_steer)
    DuringRunAction.INTERRUPT -> stringResource(Res.string.during_run_action_interrupt)
}

@Composable
internal fun uploadRoutingModeLabel(mode: UploadRoutingMode): String = when (mode) {
    UploadRoutingMode.AUTO -> stringResource(Res.string.upload_routing_auto)
    UploadRoutingMode.MANUAL -> stringResource(Res.string.upload_routing_manual)
}

@Composable
internal fun webSearchFirstLabel(first: WebSearchPreference): String = when (first) {
    WebSearchPreference.LIBRECHAT_FIRST -> stringResource(Res.string.web_search_first_librechat)
    WebSearchPreference.BUILT_IN_FIRST -> stringResource(Res.string.web_search_first_built_in)
}

@Composable
internal fun webSearchFirstDescription(first: WebSearchPreference): String = when (first) {
    WebSearchPreference.LIBRECHAT_FIRST -> stringResource(Res.string.web_search_first_librechat_desc)
    WebSearchPreference.BUILT_IN_FIRST -> stringResource(Res.string.web_search_first_built_in_desc)
}

@Composable
internal fun newChatStartLabel(preferLastUsed: Boolean): String =
    stringResource(if (preferLastUsed) Res.string.new_chat_start_last_used else Res.string.new_chat_start_server)

@Composable
internal fun starredModelsDisplayLabel(display: StarredModelsDisplay): String = when (display) {
    StarredModelsDisplay.OFF -> stringResource(Res.string.starred_models_off)
    StarredModelsDisplay.GROUPED -> stringResource(Res.string.starred_models_grouped)
    StarredModelsDisplay.TOP -> stringResource(Res.string.starred_models_top)
}

@Composable
internal fun chatHeaderContentLabel(content: ChatHeaderContent): String = when (content) {
    ChatHeaderContent.TITLE -> stringResource(Res.string.chat_header_content_title_option)
    ChatHeaderContent.MODEL -> stringResource(Res.string.chat_header_content_model)
    ChatHeaderContent.NONE -> stringResource(Res.string.chat_header_content_none)
}

@Composable
internal fun chatHeaderAlignmentLabel(alignment: ChatHeaderAlignment): String = when (alignment) {
    ChatHeaderAlignment.LEFT -> stringResource(Res.string.chat_header_alignment_left)
    ChatHeaderAlignment.CENTER -> stringResource(Res.string.chat_header_alignment_center)
    ChatHeaderAlignment.FILL -> stringResource(Res.string.chat_header_alignment_fill)
}

@Composable
internal fun artifactDisplayModeLabel(mode: ArtifactDisplayMode): String = when (mode) {
    ArtifactDisplayMode.AUTOMATIC -> stringResource(Res.string.artifact_mode_automatic)
    ArtifactDisplayMode.BOTTOM_SHEET -> stringResource(Res.string.artifact_mode_bottom_sheet)
    ArtifactDisplayMode.FULLSCREEN -> stringResource(Res.string.artifact_mode_fullscreen)
}

/** The mode as a summary; Automatic also names what it does in this window. */
@Composable
internal fun artifactDisplayModeSummary(mode: ArtifactDisplayMode): String = when (mode) {
    ArtifactDisplayMode.AUTOMATIC ->
        stringResource(Res.string.artifact_mode_automatic_here, artifactPresentationLabel(windowAutomaticArtifactPresentation()))
    else -> artifactDisplayModeLabel(mode)
}

@Composable
internal fun artifactPresentationLabel(presentation: ArtifactPresentation): String = when (presentation) {
    ArtifactPresentation.SPLIT -> stringResource(Res.string.artifact_mode_split)
    ArtifactPresentation.SHEET -> stringResource(Res.string.artifact_mode_bottom_sheet)
    ArtifactPresentation.FULL_SCREEN -> stringResource(Res.string.artifact_mode_fullscreen)
}

/**
 * Generic single-select radio dialog with Save/Cancel.
 * [optionDescription] may return null for options without a secondary line.
 */
@Composable
internal fun <T> RadioSelectionDialog(
    title: String,
    options: List<T>,
    selected: T,
    onSave: (T) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    optionDescription: @Composable (T) -> String? = { null },
    optionLabel: @Composable (T) -> String,
) {
    var current by remember { mutableStateOf(selected) }

    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        scrollableText = false,
        modifier = modifier,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                RadioGroup(
                    options = options,
                    selected = current,
                    onSelect = { current = it },
                    optionLabel = optionLabel,
                    optionDescription = optionDescription,
                )
            }
        },
        confirmButton = {
            AdaptiveTextButton(onClick = { onSave(current) }) {
                Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = {
            AdaptiveTextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

/** Inline-artifact rendering toggles. Switches apply immediately; Done just closes. */
@Composable
internal fun RenderInlineDialog(
    prefs: InlineArtifactPrefs,
    onMermaidChange: (Boolean) -> Unit,
    onSvgChange: (Boolean) -> Unit,
    onHtmlChange: (Boolean) -> Unit,
    onReactChange: (Boolean) -> Unit,
    onMarkdownChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        scrollableText = false,
        modifier = modifier,
        title = { Text(stringResource(Res.string.artifact_inline_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(Res.string.artifacts_section_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))

                SettingsSwitchRow(
                    title = stringResource(Res.string.inline_artifact_mermaid),
                    description = stringResource(Res.string.inline_artifact_mermaid_desc),
                    checked = prefs.mermaid,
                    onCheckedChange = onMermaidChange,
                    showDivider = false,
                    contentPadding = DialogSwitchPadding,
                )
                SettingsSwitchRow(
                    title = stringResource(Res.string.inline_artifact_svg),
                    description = stringResource(Res.string.inline_artifact_svg_desc),
                    checked = prefs.svg,
                    onCheckedChange = onSvgChange,
                    showDivider = false,
                    contentPadding = DialogSwitchPadding,
                )
                SettingsSwitchRow(
                    title = stringResource(Res.string.inline_artifact_markdown),
                    description = stringResource(Res.string.inline_artifact_markdown_desc),
                    checked = prefs.markdown,
                    onCheckedChange = onMarkdownChange,
                    showDivider = false,
                    contentPadding = DialogSwitchPadding,
                )
                SettingsSwitchRow(
                    title = stringResource(Res.string.inline_artifact_html),
                    description = stringResource(Res.string.inline_artifact_html_desc),
                    checked = prefs.html,
                    onCheckedChange = onHtmlChange,
                    showDivider = false,
                    contentPadding = DialogSwitchPadding,
                )
                SettingsSwitchRow(
                    title = stringResource(Res.string.inline_artifact_react),
                    description = stringResource(Res.string.inline_artifact_react_desc),
                    checked = prefs.react,
                    onCheckedChange = onReactChange,
                    showDivider = false,
                    contentPadding = DialogSwitchPadding,
                )
            }
        },
        confirmButton = {
            AdaptiveTextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_done))
            }
        },
    )
}

@Composable
internal fun <T> RadioGroup(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    optionLabel: @Composable (T) -> String,
    optionDescription: @Composable (T) -> String? = { null },
) {
    Column(modifier = Modifier.selectableGroup()) {
        options.forEach { option ->
            RadioOptionRow(
                label = optionLabel(option),
                description = optionDescription(option),
                selected = selected == option,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun RadioOptionRow(
    label: String,
    description: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = if (description == null) Alignment.CenterVertically else Alignment.Top,
    ) {
        AdaptiveRadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val DialogSwitchPadding = PaddingValues(vertical = 8.dp)
