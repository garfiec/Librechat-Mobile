package com.garfiec.librechat.feature.settings.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.ui.components.DiscardChangesDialog
import com.garfiec.librechat.core.ui.components.PlatformBackHandler
import com.garfiec.librechat.core.ui.media.rememberEmailComposer
import com.garfiec.librechat.core.ui.media.rememberShareTextWithFile
import com.garfiec.librechat.core.ui.platform.rememberClipboardWriter
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.ReportProblemViewModel
import com.garfiec.librechat.feature.settings.viewmodel.ReportSheet
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

    ReportProblemContent(
        uiState = uiState,
        onBack = viewModel::onBackRequested,
        onKindChange = viewModel::setKind,
        onTextChange = viewModel::setText,
        onIncludeLogChange = viewModel::setIncludeLog,
        onViewDetails = viewModel::showDetails,
        onContinue = { scope.launch { composeEmail(viewModel.buildDraft(), viewModel::onComposeResult) } },
        // One host at a time: the page's draws behind an open sheet's scrim.
        snackbarHostState = snackbarHostState.takeIf { uiState.sheet == null },
        modifier = modifier,
    )

    when (uiState.sheet) {
        null -> Unit
        ReportSheet.DETAILS -> ReportSheetFrame(onDismiss = viewModel::dismissSheet, snackbarHostState = snackbarHostState) {
            ReportDetailsSheetContent(
                details = uiState.details,
                onSaveCopy = viewModel::saveLogCopy,
                onDone = viewModel::dismissSheet,
            )
        }
        ReportSheet.SEND_ANOTHER_WAY -> ReportSheetFrame(onDismiss = viewModel::dismissSheet, snackbarHostState = snackbarHostState) {
            SendAnotherWaySheetContent(
                logIncluded = uiState.includeLog && uiState.logBytes > 0,
                onCopyAddress = {
                    clipboard.copy(SUPPORT_EMAIL, title)
                    scope.launch { snackbarHostState.showSnackbar(addressCopiedMsg) }
                },
                onShare = {
                    scope.launch {
                        val draft = viewModel.buildDraft()
                        shareTextWithFile(draft.body, draft.subject, draft.attachment)
                    }
                },
                onCopyText = {
                    clipboard.copy(viewModel.reportText(), title)
                    scope.launch { snackbarHostState.showSnackbar(copiedMsg) }
                },
                onClose = viewModel::dismissSheet,
            )
        }
    }
}
