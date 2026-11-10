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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.garfiec.librechat.core.data.datastore.WebSearchPreference
import com.garfiec.librechat.core.model.ArtifactsMode
import com.garfiec.librechat.core.ui.artifacts.artifactsModeDescription
import com.garfiec.librechat.core.ui.artifacts.artifactsModeName
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveGroupedPage
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.resources.artifacts_mode_intro
import com.garfiec.librechat.core.ui.resources.artifacts_mode_title
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.ChatLayoutSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.ChatSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.ContextUsageSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.SpeechFailure
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.garfiec.librechat.core.ui.resources.Res as CoreUiRes

/** The Chat tab's content, without a Scaffold or top bar. */
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
    val artifactLibraries by viewModel.artifactLibraries.collectAsStateWithLifecycle()
    val contextUsage by contextUsageViewModel.uiState.collectAsStateWithLifecycle()
    val chatLayout by chatLayoutViewModel.uiState.collectAsStateWithLifecycle()
    var openDialog by remember { mutableStateOf<ChatSettingDialog?>(null) }
    val dismissDialog = { openDialog = null }
    fun <T> saveAndClose(setter: (T) -> Unit): (T) -> Unit = {
        setter(it)
        openDialog = null
    }

    // Voice test, preview and playback failures land here.
    val speechErrorText = uiState.error?.let { speechFailureText(it) }
    LaunchedEffect(uiState.error) {
        val text = speechErrorText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = text)
        viewModel.dismissError()
    }
    val voiceTestText = stringResource(Res.string.tts_preview_text)
    val contextSummary = contextUsageSummary(contextUsage.placement, contextUsage.preset, contextUsage.advanced)
    val layoutSummary = chatLayoutSummary(chatLayout)

    AdaptiveGroupedPage(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item(key = "display_header") {
                AdaptiveSectionHeader(stringResource(Res.string.chat_group_display))
            }
            adaptiveSection {
                row(key = "chat_layout") {
                    SettingsRow(
                        title = stringResource(Res.string.chat_layout),
                        summary = layoutSummary,
                        onClick = onNavigateToChatLayout,
                    )
                }
                row(key = "font_size") {
                    SettingsRow(
                        title = stringResource(Res.string.font_size),
                        summary = fontSizeLabel(uiState.chatFontSize),
                        onClick = { openDialog = ChatSettingDialog.FONT_SIZE },
                    )
                }
                row(key = "latex_renderer") {
                    SettingsRow(
                        title = stringResource(Res.string.latex_renderer),
                        summary = latexRendererLabel(uiState.latexRenderer),
                        onClick = { openDialog = ChatSettingDialog.LATEX_RENDERER },
                    )
                }
            }

            item(key = "behavior_header") {
                AdaptiveSectionHeader(stringResource(Res.string.chat_group_behavior))
            }
            adaptiveSection {
                row(key = "thinking_open_by_default") {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.thinking_open_by_default),
                        description = stringResource(Res.string.thinking_open_by_default_desc),
                        checked = uiState.thinkingOpenByDefault,
                        onCheckedChange = viewModel::setThinkingOpenByDefault,
                    )
                }
                row(key = "collapse_long_user_messages") {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.collapse_long_user_messages),
                        description = stringResource(Res.string.collapse_long_user_messages_desc),
                        checked = uiState.collapseLongUserMessages,
                        onCheckedChange = viewModel::setCollapseLongUserMessages,
                    )
                }
                row(key = "show_image_descriptions") {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.show_image_descriptions),
                        description = stringResource(Res.string.show_image_descriptions_desc),
                        checked = uiState.showImageDescriptions,
                        onCheckedChange = viewModel::setShowImageDescriptions,
                    )
                }
                row(key = "show_site_icons") {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.show_site_icons),
                        description = stringResource(Res.string.show_site_icons_desc),
                        checked = uiState.showSiteIcons,
                        onCheckedChange = viewModel::setShowSiteIcons,
                    )
                }
                row(key = "client_image_resize") {
                    val enforced = uiState.clientImageResizeAdmin != null
                    SettingsSwitchRow(
                        title = stringResource(Res.string.client_image_resize),
                        description = stringResource(
                            if (enforced) Res.string.client_image_resize_enforced else Res.string.client_image_resize_desc,
                        ),
                        checked = uiState.clientImageResizeAdmin ?: uiState.clientImageResizePreference,
                        onCheckedChange = viewModel::setClientImageResize,
                        enabled = !enforced,
                    )
                }
                row(key = "dismiss_keyboard_on_send") {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.dismiss_keyboard_on_send),
                        description = stringResource(Res.string.dismiss_keyboard_on_send_desc),
                        checked = uiState.dismissKeyboardOnSend,
                        onCheckedChange = viewModel::setDismissKeyboardOnSend,
                    )
                }
                row(key = "during_run_action") {
                    SettingsRow(
                        title = stringResource(Res.string.during_run_action_title),
                        summary = duringRunActionLabel(uiState.duringRunAction),
                        onClick = { openDialog = ChatSettingDialog.DURING_RUN_ACTION },
                    )
                }
            }

            item(key = "header_context_header") {
                AdaptiveSectionHeader(stringResource(Res.string.chat_group_header_context))
            }
            adaptiveSection {
                row(key = "chat_header") {
                    SettingsRow(
                        title = stringResource(Res.string.chat_header_title),
                        summary = stringResource(
                            Res.string.chat_header_summary_format,
                            chatHeaderContentLabel(uiState.chatHeaderContent),
                            chatHeaderAlignmentLabel(uiState.chatHeaderAlignment),
                        ),
                        onClick = { openDialog = ChatSettingDialog.CHAT_HEADER },
                    )
                }
                row(key = "context_usage") {
                    SettingsRow(
                        title = stringResource(Res.string.context_bar_title),
                        summary = contextSummary,
                        onClick = onNavigateToContextUsage,
                    )
                }
                row(key = "upload_routing") {
                    SettingsRow(
                        title = stringResource(Res.string.upload_routing_title),
                        summary = uploadRoutingModeLabel(uiState.uploadRoutingMode),
                        onClick = { openDialog = ChatSettingDialog.UPLOAD_ROUTING },
                    )
                }
                row(key = "web_search_first") {
                    SettingsRow(
                        title = stringResource(Res.string.web_search_first_title),
                        summary = webSearchFirstLabel(uiState.webSearchFirst),
                        onClick = { openDialog = ChatSettingDialog.WEB_SEARCH_FIRST },
                    )
                }
                row(key = "starred_models") {
                    SettingsRow(
                        title = stringResource(Res.string.starred_models_title),
                        summary = starredModelsDisplayLabel(uiState.starredModelsDisplay),
                        onClick = { openDialog = ChatSettingDialog.STARRED_MODELS },
                    )
                }
                row(key = "new_chat_start") {
                    SettingsRow(
                        title = stringResource(Res.string.new_chat_start_title),
                        summary = newChatStartLabel(uiState.preferLastUsedModel),
                        onClick = { openDialog = ChatSettingDialog.NEW_CHAT_START },
                    )
                }
            }

            item(key = "artifacts_header") {
                AdaptiveSectionHeader(stringResource(Res.string.section_artifacts))
            }
            adaptiveSection {
                row(key = "artifact_viewer") {
                    SettingsRow(
                        title = stringResource(Res.string.artifact_viewer_title),
                        summary = artifactDisplayModeLabel(uiState.artifactDisplayPrefs.mode),
                        onClick = { openDialog = ChatSettingDialog.ARTIFACT_VIEWER },
                    )
                }
                row(key = "artifact_inline") {
                    SettingsRow(
                        title = stringResource(Res.string.artifact_inline_title),
                        summary = stringResource(
                            Res.string.artifact_inline_enabled_count,
                            uiState.inlineArtifactPrefs.enabledCount,
                            InlineArtifactPrefs.FIELD_COUNT,
                        ),
                        onClick = { openDialog = ChatSettingDialog.RENDER_INLINE },
                    )
                }
                row(key = "artifact_libraries") {
                    SettingsRow(
                        title = stringResource(Res.string.artifact_libraries_title),
                        summary = artifactLibrariesSummary(artifactLibraries),
                        onClick = { openDialog = ChatSettingDialog.ARTIFACT_LIBRARIES },
                    )
                }
                row(key = "artifacts_mode") {
                    SettingsRow(
                        title = stringResource(CoreUiRes.string.artifacts_mode_title),
                        summary = artifactsModeName(uiState.artifactsMode),
                        onClick = { openDialog = ChatSettingDialog.ARTIFACTS_MODE },
                    )
                }
            }

            item(key = "presets_header") {
                AdaptiveSectionHeader(stringResource(Res.string.section_presets))
            }
            adaptiveSection {
                row(key = "presets_row") {
                    SettingsRow(
                        icon = Icons.Default.Brush,
                        title = stringResource(Res.string.presets),
                        summary = stringResource(Res.string.presets_subtitle),
                        onClick = onNavigateToPresets,
                    )
                }
            }

            item(key = "speech_header") {
                AdaptiveSectionHeader(stringResource(Res.string.section_speech))
            }
            adaptiveSection {
                row(key = "stt_auto_send") {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.auto_send_after_speech),
                        description = stringResource(Res.string.auto_send_after_speech_desc),
                        checked = uiState.sttAutoSend,
                        onCheckedChange = viewModel::setAutoSendAfterStt,
                    )
                }
                row(key = "auto_read") {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.auto_read_responses),
                        description = stringResource(Res.string.auto_read_responses_desc),
                        checked = uiState.autoReadEnabled,
                        onCheckedChange = viewModel::setAutoReadEnabled,
                    )
                }
                row(key = "speech_voice") {
                    SpeechVoiceSection(
                        selectedVoice = uiState.selectedVoice,
                        availableVoices = uiState.availableVoices,
                        ttsSource = uiState.ttsSource,
                        onVoiceSelect = viewModel::selectVoice,
                        onTestVoice = { viewModel.testVoice(voiceTestText) },
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
                        DuringRunAction.INTERRUPT -> stringResource(Res.string.during_run_action_interrupt_desc)
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

        if (openDialog == ChatSettingDialog.NEW_CHAT_START) {
            RadioSelectionDialog(
                title = stringResource(Res.string.new_chat_start_title),
                description = stringResource(Res.string.new_chat_start_desc),
                options = listOf(false, true),
                selected = uiState.preferLastUsedModel,
                onSave = saveAndClose(viewModel::setPreferLastUsedModel),
                onDismiss = dismissDialog,
                optionLabel = { newChatStartLabel(it) },
            )
        }

        if (openDialog == ChatSettingDialog.WEB_SEARCH_FIRST) {
            RadioSelectionDialog(
                title = stringResource(Res.string.web_search_first_title),
                description = stringResource(Res.string.web_search_first_desc),
                options = WebSearchPreference.entries,
                selected = uiState.webSearchFirst,
                onSave = saveAndClose(viewModel::setWebSearchFirst),
                onDismiss = dismissDialog,
                optionLabel = { webSearchFirstLabel(it) },
                optionDescription = { webSearchFirstDescription(it) },
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

        if (openDialog == ChatSettingDialog.ARTIFACTS_MODE) {
            RadioSelectionDialog(
                title = stringResource(CoreUiRes.string.artifacts_mode_title),
                description = stringResource(CoreUiRes.string.artifacts_mode_intro),
                options = ArtifactsMode.entries,
                selected = uiState.artifactsMode,
                onSave = saveAndClose(viewModel::setArtifactsMode),
                onDismiss = dismissDialog,
                optionLabel = { artifactsModeName(it) },
                optionDescription = { artifactsModeDescription(it) },
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

        if (openDialog == ChatSettingDialog.ARTIFACT_LIBRARIES) {
            ArtifactLibrariesDialog(
                choices = artifactLibraries,
                onDecisionChange = viewModel::setArtifactLibraryDecision,
                onDismiss = dismissDialog,
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
                serverTtsEnabled = uiState.serverTtsEnabled,
                availableEngines = listOf(TTS_DEFAULT_OPTION, "ElevenLabs", "OpenAI"),
                availableVoices = uiState.availableVoices.map { it.name },
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
internal fun SpeechDetailButtons(
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
                    Text(stringResource(Res.string.speech_to_text_settings), modifier = Modifier.weight(1f))
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
                    Text(stringResource(Res.string.text_to_speech_settings), modifier = Modifier.weight(1f))
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
private fun speechFailureText(failure: SpeechFailure): String = stringResource(
    when (failure) {
        SpeechFailure.VOICE_TEST -> Res.string.speech_error_voice_test
        SpeechFailure.VOICE_PREVIEW -> Res.string.speech_error_voice_preview
        SpeechFailure.PLAYBACK -> Res.string.speech_error_playback
    },
)
