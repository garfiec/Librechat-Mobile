package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ArtifactsMode
import com.garfiec.librechat.core.model.preferences.ArtifactLibraryDecision
import com.garfiec.librechat.core.model.preferences.ArtifactLibrarySubject
import com.garfiec.librechat.core.model.preferences.DuringRunAction
import com.garfiec.librechat.core.model.preferences.InlineArtifactPrefs
import com.garfiec.librechat.core.model.preferences.LatexRenderer
import com.garfiec.librechat.core.model.preferences.UploadRoutingMode
import com.garfiec.librechat.core.model.preferences.WebSearchPreference
import com.garfiec.librechat.core.model.speech.SttEngine
import com.garfiec.librechat.core.ui.artifacts.artifactsModeDescription
import com.garfiec.librechat.core.ui.artifacts.artifactsModeName
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.resources.artifacts_mode_intro
import com.garfiec.librechat.core.ui.resources.artifacts_mode_title
import com.garfiec.librechat.feature.settings.navigation.AdvancedSection
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.ArtifactLibraryChoice
import com.garfiec.librechat.feature.settings.viewmodel.ChatSettingsGates
import com.garfiec.librechat.feature.settings.viewmodel.ChatSettingsUiState
import com.garfiec.librechat.feature.settings.viewmodel.DataSettingsUiState
import com.garfiec.librechat.feature.settings.viewmodel.GeneralSettingsUiState
import com.garfiec.librechat.feature.settings.viewmodel.ServerHeadersUiState
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.garfiec.librechat.core.ui.resources.Res as CoreUiRes

@Suppress("LongParameterList")
@Immutable
class AdvancedSettingsActions(
    val onClientImageResizeChange: (Boolean) -> Unit,
    val onSttOnDeviceChange: (Boolean) -> Unit,
    val onAdvancedSearchChange: (Boolean) -> Unit,
    val onDuringRunAction: (DuringRunAction) -> Unit,
    val onUploadRoutingMode: (UploadRoutingMode) -> Unit,
    val onWebSearchFirst: (WebSearchPreference) -> Unit,
    val onArtifactsMode: (ArtifactsMode) -> Unit,
    val onInlineArtifactMermaid: (Boolean) -> Unit,
    val onInlineArtifactSvg: (Boolean) -> Unit,
    val onInlineArtifactHtml: (Boolean) -> Unit,
    val onInlineArtifactReact: (Boolean) -> Unit,
    val onInlineArtifactMarkdown: (Boolean) -> Unit,
    val onArtifactLibraryDecision: (ArtifactLibrarySubject, ArtifactLibraryDecision) -> Unit,
    val onSttEngine: (storedValue: String) -> Unit,
    val onLatexRenderer: (LatexRenderer) -> Unit,
    val onHeaderNameChange: (Int, String) -> Unit,
    val onHeaderValueChange: (Int, String) -> Unit,
    val onAddHeader: () -> Unit,
    val onRemoveHeader: (Int) -> Unit,
    val onSaveHeaders: () -> Unit,
    val onDiscardHeaders: () -> Unit,
)

/**
 * Advanced's page with its setting dialogs; the server headers dialog shows while
 * [showServerHeadersDialog], which the caller keeps so a landed save can close it.
 */
@Composable
fun AdvancedSettingsPageContent(
    section: AdvancedSection?,
    chat: ChatSettingsUiState,
    gates: ChatSettingsGates,
    artifactLibraries: List<ArtifactLibraryChoice>?,
    data: DataSettingsUiState,
    general: GeneralSettingsUiState,
    serverHeaders: ServerHeadersUiState,
    headersSaveFailure: String?,
    showServerHeadersDialog: Boolean,
    actions: AdvancedSettingsActions,
    onOpenServerConnection: () -> Unit,
    onDismissServerHeaders: () -> Unit,
    onNavigateToPresets: () -> Unit,
    onNavigateToApiKeys: () -> Unit,
    onNavigateToCodeExecution: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var openDialog by remember { mutableStateOf<ChatSettingDialog?>(null) }
    val dismissDialog = { openDialog = null }
    fun <T> saveAndClose(setter: (T) -> Unit): (T) -> Unit = {
        setter(it)
        openDialog = null
    }

    AdvancedSettingsContent(
        section = section,
        chat = chat,
        gates = gates,
        artifactLibraries = artifactLibraries,
        data = data,
        general = general,
        onOpenDialog = { openDialog = it },
        onClientImageResizeChange = actions.onClientImageResizeChange,
        onSttOnDeviceChange = actions.onSttOnDeviceChange,
        onAdvancedSearchChange = actions.onAdvancedSearchChange,
        onOpenServerConnection = onOpenServerConnection,
        onOpenPresets = onNavigateToPresets,
        onOpenApiKeys = onNavigateToApiKeys,
        onOpenCodeEnvironments = onNavigateToCodeExecution,
        modifier = modifier,
        contentPadding = contentPadding,
    )

    when (openDialog) {
        ChatSettingDialog.DURING_RUN_ACTION -> RadioSelectionDialog(
            title = stringResource(Res.string.during_run_action_title),
            description = stringResource(Res.string.during_run_action_desc),
            options = DuringRunAction.entries,
            selected = chat.duringRunAction,
            onSave = saveAndClose(actions.onDuringRunAction),
            onDismiss = dismissDialog,
            optionLabel = { duringRunActionLabel(it) },
            optionDescription = {
                when (it) {
                    DuringRunAction.QUEUE -> stringResource(Res.string.during_run_action_queue_desc)
                    DuringRunAction.STEER -> stringResource(Res.string.during_run_action_steer_desc)
                    DuringRunAction.INTERRUPT -> stringResource(Res.string.during_run_action_interrupt_desc)
                }
            },
        )
        ChatSettingDialog.UPLOAD_ROUTING -> RadioSelectionDialog(
            title = stringResource(Res.string.upload_routing_title),
            description = stringResource(Res.string.upload_routing_desc),
            options = UploadRoutingMode.entries,
            selected = chat.uploadRoutingMode,
            onSave = saveAndClose(actions.onUploadRoutingMode),
            onDismiss = dismissDialog,
            optionLabel = { uploadRoutingModeLabel(it) },
            optionDescription = {
                when (it) {
                    UploadRoutingMode.AUTO -> stringResource(Res.string.upload_routing_auto_desc)
                    UploadRoutingMode.MANUAL -> stringResource(Res.string.upload_routing_manual_desc)
                }
            },
        )
        ChatSettingDialog.WEB_SEARCH_FIRST -> RadioSelectionDialog(
            title = stringResource(Res.string.web_search_first_title),
            description = stringResource(Res.string.web_search_first_desc),
            options = WebSearchPreference.entries,
            selected = chat.webSearchFirst,
            onSave = saveAndClose(actions.onWebSearchFirst),
            onDismiss = dismissDialog,
            optionLabel = { webSearchFirstLabel(it) },
            optionDescription = { webSearchFirstDescription(it) },
        )
        ChatSettingDialog.ARTIFACTS_MODE -> RadioSelectionDialog(
            title = stringResource(CoreUiRes.string.artifacts_mode_title),
            description = stringResource(CoreUiRes.string.artifacts_mode_intro),
            options = ArtifactsMode.entries,
            selected = chat.artifactsMode,
            onSave = saveAndClose(actions.onArtifactsMode),
            onDismiss = dismissDialog,
            optionLabel = { artifactsModeName(it) },
            optionDescription = { artifactsModeDescription(it) },
        )
        ChatSettingDialog.RENDER_INLINE -> RenderInlineDialog(
            prefs = chat.inlineArtifactPrefs,
            onMermaidChange = actions.onInlineArtifactMermaid,
            onSvgChange = actions.onInlineArtifactSvg,
            onHtmlChange = actions.onInlineArtifactHtml,
            onReactChange = actions.onInlineArtifactReact,
            onMarkdownChange = actions.onInlineArtifactMarkdown,
            onDismiss = dismissDialog,
        )
        ChatSettingDialog.ARTIFACT_LIBRARIES -> ArtifactLibrariesDialog(
            choices = artifactLibraries,
            onDecisionChange = actions.onArtifactLibraryDecision,
            onDismiss = dismissDialog,
        )
        ChatSettingDialog.STT_ENGINE -> RadioSelectionDialog(
            title = stringResource(Res.string.stt_engine_label),
            // Server is offered when the server transcribes, or when it is already the stored choice.
            options = listOfNotNull(
                SttEngine.BROWSER,
                SttEngine.EXTERNAL.takeIf { chat.serverSttEnabled || SttEngine.fromStored(chat.sttEngine) == it },
            ),
            selected = SttEngine.fromStored(chat.sttEngine),
            onSave = saveAndClose { engine: SttEngine -> actions.onSttEngine(engine.storedValue) },
            onDismiss = dismissDialog,
            optionLabel = { sttEngineLabel(it) },
            optionDescription = {
                when (it) {
                    SttEngine.BROWSER -> stringResource(Res.string.stt_hint_browser)
                    SttEngine.EXTERNAL -> stringResource(Res.string.stt_hint_external)
                }
            },
        )
        ChatSettingDialog.LATEX_RENDERER -> RadioSelectionDialog(
            title = stringResource(Res.string.latex_renderer),
            options = LatexRenderer.entries,
            selected = chat.latexRenderer,
            onSave = saveAndClose(actions.onLatexRenderer),
            onDismiss = dismissDialog,
            optionLabel = { latexRendererLabel(it) },
            optionDescription = {
                when (it) {
                    LatexRenderer.KATEX -> stringResource(Res.string.latex_katex_desc)
                    LatexRenderer.NATIVE -> stringResource(Res.string.latex_native_desc)
                }
            },
        )
        null -> Unit
    }

    if (showServerHeadersDialog) {
        ServerHeadersDialog(
            serverUrl = serverHeaders.serverUrl,
            headers = serverHeaders.headers,
            error = serverHeaders.error,
            loadFailed = serverHeaders.loadFailed,
            saveFailure = headersSaveFailure,
            isSaving = serverHeaders.isSaving,
            isDirty = serverHeaders.isDirty,
            onNameChange = actions.onHeaderNameChange,
            onValueChange = actions.onHeaderValueChange,
            onAdd = actions.onAddHeader,
            onRemove = actions.onRemoveHeader,
            onSave = actions.onSaveHeaders,
            onDiscard = actions.onDiscardHeaders,
            onDismiss = onDismissServerHeaders,
        )
    }
}

/** One Advanced section's rows; a section without rows is left out. */
private class AdvancedRows(val section: AdvancedSection, val rows: List<Pair<String, @Composable () -> Unit>>)

@Composable
internal fun AdvancedSettingsContent(
    section: AdvancedSection?,
    chat: ChatSettingsUiState,
    gates: ChatSettingsGates,
    artifactLibraries: List<ArtifactLibraryChoice>?,
    data: DataSettingsUiState,
    general: GeneralSettingsUiState,
    onOpenDialog: (ChatSettingDialog) -> Unit,
    onClientImageResizeChange: (Boolean) -> Unit,
    onSttOnDeviceChange: (Boolean) -> Unit,
    onAdvancedSearchChange: (Boolean) -> Unit,
    onOpenServerConnection: () -> Unit,
    onOpenPresets: () -> Unit,
    onOpenApiKeys: () -> Unit,
    onOpenCodeEnvironments: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val sections = listOf(
        AdvancedRows(
            AdvancedSection.CHATS,
            buildList {
                add(
                    "during_run_action" to {
                        SettingsRow(
                            title = stringResource(Res.string.during_run_action_title),
                            summary = duringRunActionLabel(chat.duringRunAction),
                            onClick = { onOpenDialog(ChatSettingDialog.DURING_RUN_ACTION) },
                        )
                    },
                )
                add(
                    "upload_routing" to {
                        SettingsRow(
                            title = stringResource(Res.string.upload_routing_title),
                            summary = uploadRoutingModeLabel(chat.uploadRoutingMode),
                            onClick = { onOpenDialog(ChatSettingDialog.UPLOAD_ROUTING) },
                        )
                    },
                )
                add(
                    "client_image_resize" to {
                        val enforced = chat.clientImageResizeAdmin != null
                        SettingsSwitchRow(
                            title = stringResource(Res.string.client_image_resize),
                            description = stringResource(
                                if (enforced) Res.string.client_image_resize_enforced else Res.string.client_image_resize_desc,
                            ),
                            checked = chat.clientImageResizeAdmin ?: chat.clientImageResizePreference,
                            onCheckedChange = onClientImageResizeChange,
                            enabled = !enforced,
                        )
                    },
                )
                add(
                    "web_search_first" to {
                        SettingsRow(
                            title = stringResource(Res.string.web_search_first_title),
                            summary = webSearchFirstLabel(chat.webSearchFirst),
                            onClick = { onOpenDialog(ChatSettingDialog.WEB_SEARCH_FIRST) },
                        )
                    },
                )
                if (gates.presets) {
                    add(
                        "presets" to {
                            SettingsRow(
                                title = stringResource(Res.string.presets),
                                summary = stringResource(Res.string.presets_subtitle),
                                onClick = onOpenPresets,
                            )
                        },
                    )
                }
            },
        ),
        AdvancedRows(
            AdvancedSection.ARTIFACTS,
            listOf(
                "artifacts_mode" to {
                    SettingsRow(
                        title = stringResource(CoreUiRes.string.artifacts_mode_title),
                        summary = artifactsModeName(chat.artifactsMode),
                        onClick = { onOpenDialog(ChatSettingDialog.ARTIFACTS_MODE) },
                    )
                },
                "artifact_inline" to {
                    SettingsRow(
                        title = stringResource(Res.string.artifact_inline_title),
                        summary = stringResource(
                            Res.string.artifact_inline_enabled_count,
                            chat.inlineArtifactPrefs.enabledCount,
                            InlineArtifactPrefs.FIELD_COUNT,
                        ),
                        onClick = { onOpenDialog(ChatSettingDialog.RENDER_INLINE) },
                    )
                },
                "artifact_libraries" to {
                    SettingsRow(
                        title = stringResource(Res.string.artifact_libraries_title),
                        summary = artifactLibrariesSummary(artifactLibraries),
                        onClick = { onOpenDialog(ChatSettingDialog.ARTIFACT_LIBRARIES) },
                    )
                },
            ),
        ),
        AdvancedRows(
            AdvancedSection.DISPLAY,
            listOf(
                "latex_renderer" to {
                    SettingsRow(
                        title = stringResource(Res.string.latex_renderer),
                        summary = latexRendererLabel(chat.latexRenderer),
                        onClick = { onOpenDialog(ChatSettingDialog.LATEX_RENDERER) },
                    )
                },
            ),
        ),
        AdvancedRows(
            AdvancedSection.VOICE,
            buildList {
                add(
                    "stt_engine" to {
                        SettingsRow(
                            title = stringResource(Res.string.stt_engine_label),
                            summary = sttEngineLabel(SttEngine.fromStored(chat.sttEngine)),
                            onClick = { onOpenDialog(ChatSettingDialog.STT_ENGINE) },
                        )
                    },
                )
                if (liveRecognizerHonorsPrefs(chat.sttEngine)) {
                    add(
                        "stt_on_device" to {
                            SettingsSwitchRow(
                                title = stringResource(Res.string.stt_on_device_toggle),
                                description = stringResource(Res.string.stt_on_device_toggle_desc),
                                checked = chat.sttOnDevice,
                                onCheckedChange = onSttOnDeviceChange,
                            )
                        },
                    )
                }
            },
        ),
        AdvancedRows(
            AdvancedSection.SEARCH,
            listOf(
                "advanced_search" to {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.advanced_search),
                        description = stringResource(Res.string.advanced_search_desc),
                        checked = general.advancedSearchEnabled,
                        onCheckedChange = onAdvancedSearchChange,
                    )
                },
            ),
        ),
        AdvancedRows(
            AdvancedSection.SERVER,
            buildList {
                add(
                    "server_connection" to {
                        SettingsRow(
                            title = stringResource(Res.string.section_server_connection),
                            summary = stringResource(Res.string.server_connection_subtitle),
                            onClick = onOpenServerConnection,
                        )
                    },
                )
            },
        ),
        AdvancedRows(
            AdvancedSection.DEVELOPER,
            if (gates.apiKeys) {
                listOf(
                    "api_keys" to {
                        SettingsRow(
                            title = stringResource(Res.string.api_keys),
                            summary = stringResource(Res.string.api_keys_subtitle),
                            onClick = onOpenApiKeys,
                        )
                    },
                )
            } else {
                emptyList()
            },
        ),
        AdvancedRows(
            AdvancedSection.CODE_ENVIRONMENTS,
            // Web's Data controls › Code execution, shown on servers with stateful code sessions.
            if (data.codeExecutionEnabled) {
                listOf(
                    "code_execution" to {
                        SettingsRow(
                            title = stringResource(Res.string.code_execution_title),
                            summary = stringResource(Res.string.code_execution_summary),
                            onClick = onOpenCodeEnvironments,
                        )
                    },
                )
            } else {
                emptyList()
            },
        ),
    ).filter { it.rows.isNotEmpty() }

    // Item 0 is the intro; each shown section is its header item followed by its rows.
    var index = 1
    val anchor = sections.firstNotNullOfOrNull { shown ->
        index.takeIf { shown.section == section }.also { index += 1 + shown.rows.size }
    } ?: 0
    // Saveable, so returning from a sub-page keeps the scroll.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = anchor)
    // Gated rows above the section arrive after the first frame and push it down: follow it until
    // the user drags the list.
    var userScrolled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) userScrolled = true }
    }
    LaunchedEffect(anchor) {
        if (section != null && !userScrolled) listState.scrollToItem(anchor)
    }

    SettingsListPage(contentPadding = contentPadding, modifier = modifier, state = listState) {
        item(key = "intro") {
            Text(
                text = stringResource(Res.string.advanced_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 16.dp),
            )
        }
        sections.forEach { shown ->
            item(key = "header_${shown.section.name}") {
                AdaptiveSectionHeader(stringResource(shown.section.title()))
            }
            adaptiveSection {
                shown.rows.forEach { (key, content) -> row(key = key) { content() } }
            }
        }
        item(key = "bottom_gap") { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

private fun AdvancedSection.title(): StringResource = when (this) {
    AdvancedSection.CHATS -> Res.string.category_chats
    AdvancedSection.ARTIFACTS -> Res.string.section_artifacts
    AdvancedSection.DISPLAY -> Res.string.chat_group_display
    AdvancedSection.VOICE -> Res.string.category_voice
    AdvancedSection.SEARCH -> Res.string.section_search
    AdvancedSection.SERVER -> Res.string.section_server
    AdvancedSection.DEVELOPER -> Res.string.section_developer
    AdvancedSection.CODE_ENVIRONMENTS -> Res.string.code_environments
}

@Composable
private fun sttEngineLabel(engine: SttEngine): String = when (engine) {
    SttEngine.BROWSER -> stringResource(Res.string.stt_engine_browser)
    SttEngine.EXTERNAL -> stringResource(Res.string.stt_engine_external)
}
