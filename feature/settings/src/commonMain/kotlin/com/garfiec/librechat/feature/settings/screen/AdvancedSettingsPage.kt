package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.server.HeaderWriteFailure
import com.garfiec.librechat.core.ui.resources.server_headers_no_server
import com.garfiec.librechat.core.ui.resources.server_headers_save_error
import com.garfiec.librechat.core.ui.resources.server_headers_unverified_delete
import com.garfiec.librechat.feature.settings.navigation.AdvancedSection
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.ChatSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.DataSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.GeneralSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.ServerHeadersViewModel
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

    // Saveable, unlike the other dialog flags: the ViewModel behind it outlives the composition and
    // holds half-typed rows. A rotation that closed it without the discard prompt would strand them,
    // and reopening won't re-read while they are unsaved.
    var showServerHeadersDialog by rememberSaveable { mutableStateOf(false) }

    // The Server engine is offered only once the speech config says the server transcribes.
    LaunchedEffect(Unit) {
        chatViewModel.retrySpeechConfig()
        chatViewModel.loadImageResizePolicy()
    }

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

    AdvancedSettingsPageContent(
        section = section,
        chat = chat,
        gates = gates,
        artifactLibraries = artifactLibraries,
        data = data,
        general = general,
        serverHeaders = serverHeadersState,
        headersSaveFailure = headersSaveFailureMsg,
        showServerHeadersDialog = showServerHeadersDialog,
        actions = remember(chatViewModel, generalViewModel, serverHeadersViewModel) {
            AdvancedSettingsActions(
                onClientImageResizeChange = chatViewModel::setClientImageResize,
                onSttOnDeviceChange = chatViewModel::setSttOnDevice,
                onAdvancedSearchChange = generalViewModel::setAdvancedSearchEnabled,
                onDuringRunAction = chatViewModel::setDuringRunAction,
                onUploadRoutingMode = chatViewModel::setUploadRoutingMode,
                onWebSearchFirst = chatViewModel::setWebSearchFirst,
                onArtifactsMode = chatViewModel::setArtifactsMode,
                onInlineArtifactMermaid = chatViewModel::setInlineArtifactMermaid,
                onInlineArtifactSvg = chatViewModel::setInlineArtifactSvg,
                onInlineArtifactHtml = chatViewModel::setInlineArtifactHtml,
                onInlineArtifactReact = chatViewModel::setInlineArtifactReact,
                onInlineArtifactMarkdown = chatViewModel::setInlineArtifactMarkdown,
                onArtifactLibraryDecision = chatViewModel::setArtifactLibraryDecision,
                onSttEngine = chatViewModel::setSttEngine,
                onLatexRenderer = chatViewModel::setLatexRenderer,
                onHeaderNameChange = serverHeadersViewModel::onNameChanged,
                onHeaderValueChange = serverHeadersViewModel::onValueChanged,
                onAddHeader = serverHeadersViewModel::addHeaderRow,
                onRemoveHeader = serverHeadersViewModel::removeHeaderRow,
                onSaveHeaders = serverHeadersViewModel::save,
                onDiscardHeaders = serverHeadersViewModel::discardEdits,
            )
        },
        onOpenServerConnection = {
            showServerHeadersDialog = true
            // The ViewModel loads once per server; a read that failed would otherwise keep warning
            // about a store that has since recovered.
            serverHeadersViewModel.reload()
        },
        onDismissServerHeaders = { showServerHeadersDialog = false },
        onNavigateToPresets = onNavigateToPresets,
        onNavigateToApiKeys = onNavigateToApiKeys,
        onNavigateToCodeExecution = onNavigateToCodeExecution,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}
