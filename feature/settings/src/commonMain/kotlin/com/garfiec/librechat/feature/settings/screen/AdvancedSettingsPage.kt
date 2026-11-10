package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.core.data.datastore.InlineArtifactPrefs
import com.garfiec.librechat.core.data.datastore.LatexRenderer
import com.garfiec.librechat.core.data.datastore.UploadRoutingMode
import com.garfiec.librechat.core.data.datastore.WebSearchPreference
import com.garfiec.librechat.core.data.repository.HeaderWriteFailure
import com.garfiec.librechat.core.model.ArtifactsMode
import com.garfiec.librechat.core.model.speech.SttEngine
import com.garfiec.librechat.core.ui.artifacts.artifactsModeDescription
import com.garfiec.librechat.core.ui.artifacts.artifactsModeName
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveGroupedPage
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.platform.TextFileSaver
import com.garfiec.librechat.core.ui.resources.artifacts_mode_intro
import com.garfiec.librechat.core.ui.resources.artifacts_mode_title
import com.garfiec.librechat.core.ui.resources.server_headers_no_server
import com.garfiec.librechat.core.ui.resources.server_headers_save_error
import com.garfiec.librechat.core.ui.resources.server_headers_unverified_delete
import com.garfiec.librechat.feature.settings.navigation.AdvancedSection
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.ArtifactLibraryChoice
import com.garfiec.librechat.feature.settings.viewmodel.ChatSettingsGates
import com.garfiec.librechat.feature.settings.viewmodel.ChatSettingsUiState
import com.garfiec.librechat.feature.settings.viewmodel.ChatSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.DataSettingsUiState
import com.garfiec.librechat.feature.settings.viewmodel.DataSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.GeneralSettingsUiState
import com.garfiec.librechat.feature.settings.viewmodel.GeneralSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.LogsExportPayload
import com.garfiec.librechat.feature.settings.viewmodel.ServerHeadersViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.garfiec.librechat.core.ui.resources.Res as CoreUiRes

/** Settings › Advanced: every power setting, in sections named after the categories they extend. */
@Composable
fun AdvancedSettingsPage(
    section: AdvancedSection?,
    onNavigateToPresets: () -> Unit,
    onNavigateToApiKeys: () -> Unit,
    onNavigateToCodeExecution: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    chatViewModel: ChatSettingsViewModel = koinViewModel(),
    dataViewModel: DataSettingsViewModel = koinViewModel(),
    generalViewModel: GeneralSettingsViewModel = koinViewModel(),
    // Hoisted rather than resolved inside the dialog: the save confirmation has to outlive the
    // dialog that triggered it, since the dialog closes as soon as the save lands.
    serverHeadersViewModel: ServerHeadersViewModel = koinViewModel(),
) {
    val chat by chatViewModel.uiState.collectAsStateWithLifecycle()
    val gates by chatViewModel.gates.collectAsStateWithLifecycle()
    val artifactLibraries by chatViewModel.artifactLibraries.collectAsStateWithLifecycle()
    val data by dataViewModel.uiState.collectAsStateWithLifecycle()
    val general by generalViewModel.uiState.collectAsStateWithLifecycle()
    val serverHeadersState by serverHeadersViewModel.uiState.collectAsStateWithLifecycle()

    var openDialog by remember { mutableStateOf<ChatSettingDialog?>(null) }
    var showClearLogsDialog by remember { mutableStateOf(false) }
    // Saveable, unlike the other dialog flags: the ViewModel behind it outlives the composition and
    // holds half-typed rows. A rotation that closed it without the discard prompt would strand them,
    // and reopening won't re-read while they are unsaved.
    var showServerHeadersDialog by rememberSaveable { mutableStateOf(false) }
    val dismissDialog = { openDialog = null }
    fun <T> saveAndClose(setter: (T) -> Unit): (T) -> Unit = {
        setter(it)
        openDialog = null
    }

    // The Server engine is offered only once the speech config says the server transcribes.
    LaunchedEffect(Unit) { chatViewModel.retrySpeechConfig() }
    LaunchedEffect(data.error) {
        val error = data.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = error)
        dataViewModel.dismissError()
    }
    DiagnosticLogsSaver(
        payload = data.logsExportReady,
        onConsume = dataViewModel::consumeLogsExport,
        snackbarHostState = snackbarHostState,
    )

    // Consume after showing: the ViewModel outlives the composition, so a flag that merely changes
    // re-fires on the next fresh composition tree (rotation, fold/unfold, theme change).
    val headersSavedMsg = stringResource(Res.string.server_headers_saved)
    LaunchedEffect(serverHeadersState.saved) {
        if (serverHeadersState.saved) {
            // Close on a save that landed, not on the Save tap: a save the store could not persist
            // keeps the dialog open with the typed rows so it can be retried.
            showServerHeadersDialog = false
            snackbarHostState.showSnackbar(message = headersSavedMsg)
            serverHeadersViewModel.consumeSaved()
        }
    }
    // Rendered inside the dialog: a refused save leaves it open, over the snackbar's host.
    val headersSaveFailureMsg = when (serverHeadersState.saveFailure) {
        HeaderWriteFailure.NoServer -> stringResource(CoreUiRes.string.server_headers_no_server)
        HeaderWriteFailure.StorageUnavailable,
        HeaderWriteFailure.NothingUsable,
        -> stringResource(CoreUiRes.string.server_headers_save_error)
        HeaderWriteFailure.UnverifiedDelete -> stringResource(CoreUiRes.string.server_headers_unverified_delete)
        null -> null
    }

    AdvancedSettingsContent(
        section = section,
        chat = chat,
        gates = gates,
        artifactLibraries = artifactLibraries,
        data = data,
        general = general,
        onOpenDialog = { openDialog = it },
        onClientImageResizeChange = chatViewModel::setClientImageResize,
        onSttOnDeviceChange = chatViewModel::setSttOnDevice,
        onAdvancedSearchChange = generalViewModel::setAdvancedSearchEnabled,
        onOpenServerConnection = {
            showServerHeadersDialog = true
            // The ViewModel loads once per server; a read that failed would otherwise keep warning
            // about a store that has since recovered.
            serverHeadersViewModel.reload()
        },
        onOpenPresets = onNavigateToPresets,
        onOpenApiKeys = onNavigateToApiKeys,
        onOpenCodeEnvironments = onNavigateToCodeExecution,
        onExportLogs = dataViewModel::exportLogs,
        onClearLogs = { showClearLogsDialog = true },
        modifier = modifier,
        contentPadding = contentPadding,
    )

    when (openDialog) {
        ChatSettingDialog.DURING_RUN_ACTION -> RadioSelectionDialog(
            title = stringResource(Res.string.during_run_action_title),
            description = stringResource(Res.string.during_run_action_desc),
            options = DuringRunAction.entries,
            selected = chat.duringRunAction,
            onSave = saveAndClose(chatViewModel::setDuringRunAction),
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
            onSave = saveAndClose(chatViewModel::setUploadRoutingMode),
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
            onSave = saveAndClose(chatViewModel::setWebSearchFirst),
            onDismiss = dismissDialog,
            optionLabel = { webSearchFirstLabel(it) },
            optionDescription = { webSearchFirstDescription(it) },
        )
        ChatSettingDialog.ARTIFACTS_MODE -> RadioSelectionDialog(
            title = stringResource(CoreUiRes.string.artifacts_mode_title),
            description = stringResource(CoreUiRes.string.artifacts_mode_intro),
            options = ArtifactsMode.entries,
            selected = chat.artifactsMode,
            onSave = saveAndClose(chatViewModel::setArtifactsMode),
            onDismiss = dismissDialog,
            optionLabel = { artifactsModeName(it) },
            optionDescription = { artifactsModeDescription(it) },
        )
        ChatSettingDialog.RENDER_INLINE -> RenderInlineDialog(
            prefs = chat.inlineArtifactPrefs,
            onMermaidChange = chatViewModel::setInlineArtifactMermaid,
            onSvgChange = chatViewModel::setInlineArtifactSvg,
            onHtmlChange = chatViewModel::setInlineArtifactHtml,
            onReactChange = chatViewModel::setInlineArtifactReact,
            onMarkdownChange = chatViewModel::setInlineArtifactMarkdown,
            onDismiss = dismissDialog,
        )
        ChatSettingDialog.ARTIFACT_LIBRARIES -> ArtifactLibrariesDialog(
            choices = artifactLibraries,
            onDecisionChange = chatViewModel::setArtifactLibraryDecision,
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
            onSave = saveAndClose { engine: SttEngine -> chatViewModel.setSttEngine(engine.storedValue) },
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
            onSave = saveAndClose(chatViewModel::setLatexRenderer),
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

    if (showClearLogsDialog) {
        AdaptiveAlertDialog(
            onDismissRequest = { showClearLogsDialog = false },
            title = { Text(stringResource(Res.string.dialog_title_clear_logs)) },
            text = { Text(stringResource(Res.string.dialog_clear_logs_message)) },
            confirmButton = {
                AdaptiveTextButton(
                    onClick = {
                        showClearLogsDialog = false
                        dataViewModel.clearLogs()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(Res.string.clear_all))
                }
            },
            dismissButton = {
                AdaptiveTextButton(onClick = { showClearLogsDialog = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    if (showServerHeadersDialog) {
        ServerHeadersDialog(
            serverUrl = serverHeadersState.serverUrl,
            headers = serverHeadersState.headers,
            error = serverHeadersState.error,
            loadFailed = serverHeadersState.loadFailed,
            saveFailure = headersSaveFailureMsg,
            isSaving = serverHeadersState.isSaving,
            isDirty = serverHeadersState.isDirty,
            onNameChange = serverHeadersViewModel::onNameChanged,
            onValueChange = serverHeadersViewModel::onValueChanged,
            onAdd = serverHeadersViewModel::addHeaderRow,
            onRemove = serverHeadersViewModel::removeHeaderRow,
            onSave = serverHeadersViewModel::save,
            onDiscard = serverHeadersViewModel::discardEdits,
            onDismiss = { showServerHeadersDialog = false },
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
    onExportLogs: () -> Unit,
    onClearLogs: () -> Unit,
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
                if (hasServerDetails(general.gitSha, general.serverTrustedViaUserCa, general.buildInfo)) {
                    add(
                        "server_details" to {
                            ServerDetails(
                                gitSha = general.gitSha,
                                serverTrustedViaUserCa = general.serverTrustedViaUserCa,
                                buildInfo = general.buildInfo,
                            )
                        },
                    )
                }
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
        AdvancedRows(
            AdvancedSection.TROUBLESHOOTING,
            listOf(
                "export_logs" to {
                    SettingsRow(
                        title = stringResource(Res.string.export_diagnostic_logs),
                        summary = when {
                            data.isLogsExporting -> stringResource(Res.string.exporting_logs)
                            data.logsBufferBytes > 0 -> formatBytes(data.logsBufferBytes)
                            else -> null
                        },
                        onClick = onExportLogs,
                        enabled = !data.isLogsExporting,
                        showChevron = false,
                    )
                },
                "clear_logs" to {
                    SettingsRow(
                        title = stringResource(Res.string.clear_diagnostic_logs),
                        summary = if (data.isLogsClearing) stringResource(Res.string.clearing_logs) else null,
                        onClick = onClearLogs,
                        enabled = !data.isLogsClearing,
                        destructive = true,
                    )
                },
            ),
        ),
    ).filter { it.rows.isNotEmpty() }

    // Item 0 is the intro; each shown section is its header item followed by its rows.
    val anchor = remember(section) {
        var index = 1
        sections.firstNotNullOfOrNull { shown ->
            index.takeIf { shown.section == section }.also { index += 1 + shown.rows.size }
        } ?: 0
    }
    // Saveable, so returning from a sub-page keeps the scroll; keys keep the anchor in place when
    // a gated section above it appears once the config loads.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = anchor)

    AdaptiveGroupedPage(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
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
    AdvancedSection.TROUBLESHOOTING -> Res.string.section_troubleshooting
}

/**
 * Hands an exported diagnostic log to the platform file saver once the ViewModel has read it, and
 * reports how the save went in [snackbarHostState].
 */
@Composable
private fun DiagnosticLogsSaver(
    payload: LogsExportPayload?,
    onConsume: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val currentOnConsume by rememberUpdatedState(onConsume)
    var pendingFileName by remember { mutableStateOf<String?>(null) }
    var pendingContent by remember { mutableStateOf<String?>(null) }
    var saverResult by remember { mutableStateOf<String?>(null) }
    val exportedMsg = stringResource(Res.string.logs_exported)

    LaunchedEffect(payload) {
        payload ?: return@LaunchedEffect
        pendingContent = payload.content
        pendingFileName = payload.fileName
        currentOnConsume()
    }
    TextFileSaver(
        triggerFileName = pendingFileName,
        content = pendingContent,
        onComplete = { success, errorMessage ->
            if (success) {
                saverResult = exportedMsg
            } else if (errorMessage != null) {
                saverResult = errorMessage
            }
        },
        onReset = {
            pendingFileName = null
            pendingContent = null
        },
    )
    LaunchedEffect(saverResult) {
        val message = saverResult ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = message)
        saverResult = null
    }
}

@Composable
private fun sttEngineLabel(engine: SttEngine): String = when (engine) {
    SttEngine.BROWSER -> stringResource(Res.string.stt_engine_browser)
    SttEngine.EXTERNAL -> stringResource(Res.string.stt_engine_external)
}
