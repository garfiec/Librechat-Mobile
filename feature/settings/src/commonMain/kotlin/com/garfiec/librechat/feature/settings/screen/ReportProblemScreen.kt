package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader
import com.garfiec.librechat.core.ui.components.AdaptiveSegmentedChoice
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.components.DiscardChangesDialog
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.core.ui.components.PlatformBackHandler
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.glass.GlassControlColors
import com.garfiec.librechat.core.ui.media.rememberEmailComposer
import com.garfiec.librechat.core.ui.media.rememberShareText
import com.garfiec.librechat.core.ui.media.rememberShareTextWithFile
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.util.rememberClipboardWriter
import com.garfiec.librechat.feature.settings.viewmodel.ReportKind
import com.garfiec.librechat.feature.settings.viewmodel.ReportProblemUiState
import com.garfiec.librechat.feature.settings.viewmodel.ReportProblemViewModel
import com.garfiec.librechat.feature.settings.viewmodel.SUPPORT_EMAIL
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Settings › About › Report a problem: a short form handed to the user's mail app. */
@Composable
fun ReportProblemScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportProblemViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnBack by rememberUpdatedState(onBack)
    val composeEmail = rememberEmailComposer()
    val shareText = rememberShareText()
    val shareTextWithFile = rememberShareTextWithFile()
    val clipboard = rememberClipboardWriter()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val addressCopiedMsg = stringResource(Res.string.report_address_copied)
    val copiedMsg = stringResource(Res.string.copied_to_clipboard)
    val title = stringResource(Res.string.report_problem_title)

    LaunchedEffect(uiState.exitRequested) {
        if (uiState.exitRequested) currentOnBack()
    }
    // Only while there is a draft to lose, so a blank form keeps the predictive-back preview.
    PlatformBackHandler(enabled = uiState.hasDraftToLose, onBack = viewModel::onBackRequested)
    if (uiState.showDiscardConfirm) {
        DiscardChangesDialog(onDiscard = viewModel::discardChanges, onDismiss = viewModel::dismissDiscardConfirmation)
    }
    DiagnosticLogsSaver(
        payload = uiState.logExport,
        onConsume = viewModel::consumeLogExport,
        snackbarHostState = snackbarHostState,
    )

    // One host at a time: the page's draws behind an open sheet's scrim.
    val sheetOpen = uiState.showDetailsSheet || uiState.showSendAnotherWay
    ReportProblemContent(
        uiState = uiState,
        onBack = viewModel::onBackRequested,
        onKindChange = viewModel::setKind,
        onTextChange = viewModel::setText,
        onIncludeLogChange = viewModel::setIncludeLog,
        onViewDetails = viewModel::showDetails,
        onContinue = { scope.launch { composeEmail(viewModel.buildDraft(), viewModel::onComposeResult) } },
        snackbarHostState = snackbarHostState.takeUnless { sheetOpen },
        modifier = modifier,
    )

    if (uiState.showDetailsSheet) {
        ReportSheet(onDismiss = viewModel::dismissDetails, snackbarHostState = snackbarHostState) {
            ReportDetailsSheetContent(
                details = uiState.details,
                onSaveCopy = viewModel::saveLogCopy,
                onDone = viewModel::dismissDetails,
            )
        }
    }
    if (uiState.showSendAnotherWay) {
        ReportSheet(onDismiss = viewModel::dismissSendAnotherWay, snackbarHostState = snackbarHostState) {
            SendAnotherWaySheetContent(
                address = SUPPORT_EMAIL,
                logIncluded = uiState.includeLog && uiState.logBytes > 0,
                onCopyAddress = {
                    clipboard.copy(SUPPORT_EMAIL, title)
                    scope.launch { snackbarHostState.showSnackbar(addressCopiedMsg) }
                },
                onShare = {
                    scope.launch {
                        val draft = viewModel.buildDraft()
                        val attachment = draft.attachment
                        if (attachment != null) {
                            shareTextWithFile(draft.body, draft.subject, attachment)
                        } else {
                            shareText(draft.body, draft.subject)
                        }
                    }
                },
                onCopyText = {
                    clipboard.copy(viewModel.reportText(), title)
                    scope.launch { snackbarHostState.showSnackbar(copiedMsg) }
                },
                onClose = viewModel::dismissSendAnotherWay,
            )
        }
    }
}

/** The page without its ViewModel, so previews can render it. */
@Composable
internal fun ReportProblemContent(
    uiState: ReportProblemUiState,
    onBack: () -> Unit,
    onKindChange: (ReportKind) -> Unit,
    onTextChange: (String) -> Unit,
    onIncludeLogChange: (Boolean) -> Unit,
    onViewDetails: () -> Unit,
    onContinue: () -> Unit,
    snackbarHostState: SnackbarHostState?,
    modifier: Modifier = Modifier,
) {
    AdaptiveScaffold(
        // The bottom bar rides above the keyboard.
        modifier = modifier.imePadding().clearFocusOnTap(),
        containerColor = GlassControlColors.groupedBackground,
        snackbarHost = { snackbarHostState?.let { AdaptiveSnackbarHost(it) } },
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    // Through the same check as system back, which never fires on iOS.
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), onBack),
                    title = BarTitle(stringResource(Res.string.report_problem_title)),
                    actions = emptyList(),
                ),
            )
        },
        bottomBar = { ContinueBar(enabled = uiState.canContinue, loading = uiState.preparing, onContinue = onContinue) },
    ) { padding ->
        SettingsListPage(contentPadding = padding) {
            item(key = "kind") {
                AdaptiveSegmentedChoice(
                    options = listOf(
                        stringResource(Res.string.report_kind_problem),
                        stringResource(Res.string.report_kind_suggestion),
                    ),
                    selectedIndex = uiState.kind.ordinal,
                    onSelect = { onKindChange(ReportKind.entries[it]) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item(key = "text") {
                AdaptiveOutlinedTextField(
                    value = uiState.text,
                    onValueChange = onTextChange,
                    label = { Text(stringResource(Res.string.report_what_happened_label)) },
                    supportingText = { Text(stringResource(Res.string.report_what_happened_hint)) },
                    minLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item(key = "sent_with_header") {
                AdaptiveSectionHeader(stringResource(Res.string.report_sent_with_header))
            }
            adaptiveSection {
                row(key = "details") {
                    SettingsRow(
                        title = stringResource(Res.string.report_details_title),
                        summary = stringResource(Res.string.report_details_summary),
                        onClick = onViewDetails,
                        showChevron = false,
                        trailingContent = {
                            Text(
                                text = stringResource(Res.string.report_view),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                    )
                }
                row(key = "log") {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.report_log_title),
                        description = stringResource(Res.string.report_log_summary, formatBytes(uiState.logBytes)),
                        checked = uiState.includeLog,
                        onCheckedChange = onIncludeLogChange,
                        showDivider = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueBar(enabled: Boolean, loading: Boolean, onContinue: () -> Unit) {
    Surface(color = GlassControlColors.groupedBackground) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AdaptiveButton(onClick = onContinue, enabled = enabled, loading = loading, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.report_continue_email))
            }
            Text(
                text = stringResource(Res.string.report_nothing_sent_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A sheet of this page, with its own snackbar host above the scrim. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportSheet(
    onDismiss: () -> Unit,
    snackbarHostState: SnackbarHostState,
    content: @Composable () -> Unit,
) {
    AdaptiveModalBottomSheet(onDismissRequest = onDismiss, dragHandle = { LowProfileDragHandle() }) {
        Box {
            content()
            AdaptiveSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** What's sent: the details text exactly as the email carries it, and what the log holds. */
@Composable
internal fun ReportDetailsSheetContent(
    details: String,
    onSaveCopy: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = stringResource(Res.string.report_sheet_title), style = MaterialTheme.typography.titleLarge)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(Res.string.report_details_title), style = MaterialTheme.typography.titleSmall)
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(Res.string.report_log_title), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(Res.string.report_log_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AdaptiveTextButton(onClick = onSaveCopy) { Text(stringResource(Res.string.report_save_copy)) }
            AdaptiveButton(onClick = onDone) { Text(stringResource(Res.string.action_done)) }
        }
    }
}

/** Shown when no mail app takes the report: the address to copy, a share, and the text to paste. */
@Composable
internal fun SendAnotherWaySheetContent(
    address: String,
    logIncluded: Boolean,
    onCopyAddress: () -> Unit,
    onShare: () -> Unit,
    onCopyText: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = stringResource(Res.string.report_another_way_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(Res.string.report_another_way_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsRow(
            title = address,
            summary = stringResource(Res.string.report_tap_to_copy),
            icon = Icons.Default.ContentCopy,
            onClick = onCopyAddress,
            showChevron = false,
            showDivider = false,
            contentPadding = PaddingValues(vertical = 8.dp),
        )
        AdaptiveOutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (logIncluded) Res.string.report_share_with_log else Res.string.report_share_text))
        }
        AdaptiveOutlinedButton(onClick = onCopyText, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.report_copy_text))
        }
        AdaptiveTextButton(onClick = onClose, modifier = Modifier.align(Alignment.End).padding(bottom = 8.dp)) {
            Text(stringResource(Res.string.action_close))
        }
    }
}
