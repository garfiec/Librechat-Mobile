package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.common.speech.sttLanguageOptions
import com.garfiec.librechat.core.data.datastore.ArtifactDisplayMode
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.core.data.datastore.InlineArtifactPrefs
import com.garfiec.librechat.core.data.datastore.LatexRenderer
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.data.datastore.UploadRoutingMode
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveGroupedPage
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.adaptiveRowColor
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.ChatLayoutSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.ChatSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.ContextUsageSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPresets: () -> Unit,
    onNavigateToContextUsage: () -> Unit,
    onNavigateToChatLayout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    AdaptiveScaffold(
        modifier = modifier,
        snackbarHost = { AdaptiveSnackbarHost(snackbarHostState) },
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), onNavigateBack),
                    title = BarTitle(stringResource(Res.string.title_chat)),
                    actions = emptyList(),
                ),
            )
        },
    ) { innerPadding ->
        ChatSettingsContent(
            onNavigateToPresets = onNavigateToPresets,
            onNavigateToContextUsage = onNavigateToContextUsage,
            onNavigateToChatLayout = onNavigateToChatLayout,
            snackbarHostState = snackbarHostState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

/**
 * Reusable Chat settings content (without Scaffold/TopAppBar).
 * Used by both the standalone screen and the tabbed settings screen.
 */
@Composable
fun ChatSettingsContent(
    onNavigateToPresets: () -> Unit,
    onNavigateToContextUsage: () -> Unit,
    onNavigateToChatLayout: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    viewModel: ChatSettingsViewModel = koinViewModel(),
    contextUsageViewModel: ContextUsageSettingsViewModel = koinViewModel(),
    chatLayoutViewModel: ChatLayoutSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val contextUsage by contextUsageViewModel.uiState.collectAsStateWithLifecycle()
    val chatLayout by chatLayoutViewModel.uiState.collectAsStateWithLifecycle()
    var openDialog by remember { mutableStateOf<ChatSettingDialog?>(null) }
    val dismissDialog = { openDialog = null }
    fun <T> saveAndClose(setter: (T) -> Unit): (T) -> Unit = {
        setter(it)
        openDialog = null
    }

    // Voice test, preview and playback failures land here.
    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = error)
        viewModel.dismissError()
    }

    AdaptiveGroupedPage(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            // Chat Preferences section
            item(key = "chat_header") {
                AdaptiveSectionHeader(stringResource(Res.string.section_chat_preferences))
            }
            adaptiveSection {
                row(key = "chat_settings") {
                    ChatSettingsSection(
                        fontSize = uiState.chatFontSize,
                        autoScrollEnabled = uiState.autoScrollEnabled,
                        showThinkingBlocks = uiState.showThinkingBlocks,
                        contextUsageSummary = contextUsageSummary(contextUsage.placement, contextUsage.preset, contextUsage.advanced),
                        onOpenContextUsage = onNavigateToContextUsage,
                        duringRunAction = uiState.duringRunAction,
                        uploadRoutingMode = uiState.uploadRoutingMode,
                        showImageDescriptions = uiState.showImageDescriptions,
                        showSiteIcons = uiState.showSiteIcons,
                        dismissKeyboardOnSend = uiState.dismissKeyboardOnSend,
                        chatLayoutSummary = chatLayoutSummary(chatLayout),
                        onOpenChatLayout = onNavigateToChatLayout,
                        latexRenderer = uiState.latexRenderer,
                        starredModelsDisplay = uiState.starredModelsDisplay,
                        chatHeaderContent = uiState.chatHeaderContent,
                        chatHeaderAlignment = uiState.chatHeaderAlignment,
                        onAutoScrollChange = viewModel::setAutoScrollEnabled,
                        onShowThinkingChange = viewModel::setShowThinkingBlocks,
                        onShowImageDescriptionsChange = viewModel::setShowImageDescriptions,
                        onShowSiteIconsChange = viewModel::setShowSiteIcons,
                        onDismissKeyboardOnSendChange = viewModel::setDismissKeyboardOnSend,
                        onOpenDialog = { openDialog = it },
                    )
                }
            }

            // Artifacts section
            item(key = "artifacts_header") {
                AdaptiveSectionHeader(stringResource(Res.string.section_artifacts))
            }
            adaptiveSection {
                row(key = "artifacts_settings") {
                    val prefs = uiState.inlineArtifactPrefs
                    ArtifactSettingsSection(
                        displayPrefs = uiState.artifactDisplayPrefs,
                        inlineArtifactSummary = stringResource(
                            Res.string.artifact_inline_enabled_count,
                            prefs.enabledCount,
                            InlineArtifactPrefs.FIELD_COUNT,
                        ),
                        onOpenArtifactViewerDialog = { openDialog = ChatSettingDialog.ARTIFACT_VIEWER },
                        onOpenRenderInlineDialog = { openDialog = ChatSettingDialog.RENDER_INLINE },
                    )
                }
            }

            // Presets section
            item(key = "presets_header") {
                AdaptiveSectionHeader(stringResource(Res.string.section_presets))
            }
            adaptiveSection {
                row(key = "presets_row") {
                    ChatSettingsRow(
                        icon = Icons.Default.Brush,
                        title = stringResource(Res.string.presets),
                        subtitle = stringResource(Res.string.presets_subtitle),
                        onClick = onNavigateToPresets,
                    )
                }
            }

            // Advanced section
            item(key = "advanced_header") {
                AdaptiveSectionHeader(stringResource(Res.string.section_advanced))
            }
            adaptiveSection {
                row(key = "fork_settings_row") {
                    ChatSettingsRow(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        title = stringResource(Res.string.fork_behavior),
                        subtitle = forkModeLabel(ForkMode.fromApiValue(uiState.forkMode)),
                        onClick = viewModel::showForkSettingsDialog,
                    )
                }
            }

            // Speech section
            item(key = "speech_header") {
                AdaptiveSectionHeader(stringResource(Res.string.section_speech))
            }
            adaptiveSection {
                row(key = "speech_settings") {
                    SpeechSettingsSection(
                        autoSendAfterSttEnabled = uiState.sttAutoSend,
                        autoReadEnabled = uiState.autoReadEnabled,
                        selectedVoice = uiState.selectedVoice,
                        availableVoices = uiState.availableVoices,
                        ttsSource = uiState.ttsSource,
                        onAutoSendAfterSttChange = viewModel::setAutoSendAfterStt,
                        onAutoReadChange = viewModel::setAutoReadEnabled,
                        onVoiceSelect = viewModel::selectVoice,
                        onTestVoice = viewModel::testVoice,
                    )
                }
                row(key = "speech_detail_buttons") {
                    SpeechDetailButtons(
                        onSttDetailClick = viewModel::showSttDetailDialog,
                        onTtsDetailClick = viewModel::showTtsDetailDialog,
                    )
                }
            }

            // Bottom spacing
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }

        if (openDialog == ChatSettingDialog.FONT_SIZE) {
            RadioSelectionDialog(
                title = stringResource(Res.string.font_size),
                options = ChatFontSize.entries,
                selected = uiState.chatFontSize,
                onSave = saveAndClose(viewModel::setChatFontSize),
                onDismiss = dismissDialog,
                optionLabel = { fontSizeLabel(it) },
            )
        }

        if (openDialog == ChatSettingDialog.LATEX_RENDERER) {
            RadioSelectionDialog(
                title = stringResource(Res.string.latex_renderer),
                options = LatexRenderer.entries,
                selected = uiState.latexRenderer,
                onSave = saveAndClose(viewModel::setLatexRenderer),
                onDismiss = dismissDialog,
                optionLabel = { latexRendererLabel(it) },
                optionDescription = {
                    when (it) {
                        LatexRenderer.KATEX -> stringResource(Res.string.latex_katex_desc)
                        LatexRenderer.NATIVE -> stringResource(Res.string.latex_native_desc)
                    }
                },
            )
        }

        if (openDialog == ChatSettingDialog.DURING_RUN_ACTION) {
            RadioSelectionDialog(
                title = stringResource(Res.string.during_run_action_title),
                description = stringResource(Res.string.during_run_action_desc),
                options = DuringRunAction.entries,
                selected = uiState.duringRunAction,
                onSave = saveAndClose(viewModel::setDuringRunAction),
                onDismiss = dismissDialog,
                optionLabel = { duringRunActionLabel(it) },
                optionDescription = {
                    when (it) {
                        DuringRunAction.QUEUE -> stringResource(Res.string.during_run_action_queue_desc)
                        DuringRunAction.STEER -> stringResource(Res.string.during_run_action_steer_desc)
                    }
                },
            )
        }

        if (openDialog == ChatSettingDialog.UPLOAD_ROUTING) {
            RadioSelectionDialog(
                title = stringResource(Res.string.upload_routing_title),
                description = stringResource(Res.string.upload_routing_desc),
                options = UploadRoutingMode.entries,
                selected = uiState.uploadRoutingMode,
                onSave = saveAndClose(viewModel::setUploadRoutingMode),
                onDismiss = dismissDialog,
                optionLabel = { uploadRoutingModeLabel(it) },
                optionDescription = {
                    when (it) {
                        UploadRoutingMode.AUTO -> stringResource(Res.string.upload_routing_auto_desc)
                        UploadRoutingMode.MANUAL -> stringResource(Res.string.upload_routing_manual_desc)
                    }
                },
            )
        }

        if (openDialog == ChatSettingDialog.STARRED_MODELS) {
            RadioSelectionDialog(
                title = stringResource(Res.string.starred_models_title),
                description = stringResource(Res.string.starred_models_desc),
                options = StarredModelsDisplay.entries,
                selected = uiState.starredModelsDisplay,
                onSave = saveAndClose(viewModel::setStarredModelsDisplay),
                onDismiss = dismissDialog,
                optionLabel = { starredModelsDisplayLabel(it) },
            )
        }

        if (openDialog == ChatSettingDialog.CHAT_HEADER) {
            ChatHeaderSettingsDialog(
                content = uiState.chatHeaderContent,
                alignment = uiState.chatHeaderAlignment,
                onSave = { content, alignment ->
                    viewModel.setChatHeaderContent(content)
                    viewModel.setChatHeaderAlignment(alignment)
                    dismissDialog()
                },
                onDismiss = dismissDialog,
            )
        }

        if (openDialog == ChatSettingDialog.ARTIFACT_VIEWER) {
            RadioSelectionDialog(
                title = stringResource(Res.string.artifact_viewer_title),
                description = stringResource(Res.string.artifact_viewer_desc),
                options = ArtifactDisplayMode.entries,
                selected = uiState.artifactDisplayPrefs.mode,
                onSave = saveAndClose(viewModel::setArtifactDisplayMode),
                onDismiss = dismissDialog,
                optionLabel = { artifactDisplayModeLabel(it) },
                optionDescription = {
                    when (it) {
                        ArtifactDisplayMode.BOTTOM_SHEET -> stringResource(Res.string.artifact_mode_bottom_sheet_desc)
                        ArtifactDisplayMode.FULLSCREEN -> stringResource(Res.string.artifact_mode_fullscreen_desc)
                    }
                },
            )
        }

        if (openDialog == ChatSettingDialog.RENDER_INLINE) {
            RenderInlineDialog(
                prefs = uiState.inlineArtifactPrefs,
                onMermaidChange = viewModel::setInlineArtifactMermaid,
                onSvgChange = viewModel::setInlineArtifactSvg,
                onHtmlChange = viewModel::setInlineArtifactHtml,
                onReactChange = viewModel::setInlineArtifactReact,
                onMarkdownChange = viewModel::setInlineArtifactMarkdown,
                onDismiss = dismissDialog,
            )
        }

        // Fork settings dialog
        if (uiState.showForkSettingsDialog) {
            ForkSettingsDialog(
                selectedMode = ForkMode.fromApiValue(uiState.forkMode),
                onModeSelect = { mode ->
                    viewModel.setForkMode(mode.apiValue)
                },
                onDismiss = viewModel::dismissForkSettingsDialog,
            )
        }

        // STT detail dialog
        if (uiState.showSttDetailDialog) {
            SttDetailDialog(
                selectedEngine = uiState.sttEngine,
                selectedLanguage = uiState.sttLanguage,
                selectedOnDevice = uiState.sttOnDevice,
                selectedEndOfSpeech = uiState.sttEndOfSpeech,
                serverSttEnabled = uiState.serverSttEnabled,
                availableLanguages = sttLanguageOptions,
                onConfirm = viewModel::saveSttSettings,
                onDismiss = viewModel::dismissSttDetailDialog,
            )
        }

        // TTS detail dialog
        if (uiState.showTtsDetailDialog) {
            TtsDetailDialog(
                selectedEngine = uiState.ttsEngine,
                selectedVoice = uiState.ttsVoice,
                speechRate = uiState.ttsSpeechRate,
                pitch = uiState.ttsPitch,
                deviceVoiceName = uiState.ttsDeviceVoiceName,
                cachingEnabled = uiState.ttsCaching,
                ttsSource = uiState.ttsSource,
                availableEngines = listOf("Default", "ElevenLabs", "OpenAI"),
                availableVoices = uiState.availableVoices.map { it.name }.ifEmpty {
                    listOf("Default", "Alloy", "Echo", "Fable", "Onyx", "Nova", "Shimmer")
                },
                availableDeviceVoices = uiState.availableDeviceVoices,
                isPreviewPlaying = uiState.isTtsPreviewPlaying,
                onPreviewDevice = viewModel::previewDeviceTts,
                onPreviewServer = viewModel::previewServerTts,
                onStopPreview = viewModel::stopTtsPreview,
                onConfirm = viewModel::saveTtsSettings,
                onDismiss = viewModel::dismissTtsDetailDialog,
            )
        }
    }
}

@Composable
private fun SpeechDetailButtons(
    onSttDetailClick: () -> Unit,
    onTtsDetailClick: () -> Unit,
) {
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AdaptiveOutlinedButton(
                onClick = onSttDetailClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(Res.string.speech_to_text_settings))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            AdaptiveOutlinedButton(
                onClick = onTtsDetailClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(Res.string.text_to_speech_settings))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        AdaptiveDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ChatSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
            color = adaptiveRowColor,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AdaptiveDivider()
    }
}
