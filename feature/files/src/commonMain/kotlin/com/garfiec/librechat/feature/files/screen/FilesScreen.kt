package com.garfiec.librechat.feature.files.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.FileObject
import com.garfiec.librechat.feature.files.platform.PdfPreview
import com.garfiec.librechat.feature.files.platform.rememberFilePickerLauncher
import com.garfiec.librechat.feature.files.viewmodel.FilesViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FilesScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    /**
     * When true the screen acts as an attachment picker: selection is always on, tapping a row
     * (including images) toggles selection rather than opening a preview, and the top bar offers
     * an "Attach" confirm that emits the chosen files via [onConfirmSelection] instead of deleting.
     */
    pickerMode: Boolean = false,
    onConfirmSelection: ((List<FileObject>) -> Unit)? = null,
    viewModel: FilesViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // `enterPickerMode()` makes selection sticky in the VM (no auto-exit when the set empties). The
    // VM flag it sets lags the first frame, so the view chrome is driven off the stable
    // [pickerMode] param instead — picker UI is correct from frame zero, no upload-FAB/preview flash.
    if (pickerMode) {
        LaunchedEffect(Unit) { viewModel.enterPickerMode() }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    val filePickerLauncher = rememberFilePickerLauncher(
        onFilePick = { fileRef -> viewModel.uploadFile(fileRef) },
    )

    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error)
        viewModel.dismissError()
    }

    FilesScreenContent(
        uiState = uiState,
        actions = FilesActions(
            onUploadClick = { filePickerLauncher.launch(uiState.pickerMimeTypes) },
            onConfirmSelection = { onConfirmSelection?.invoke(viewModel.confirmSelection()) },
            onFilterSettled = viewModel::setFilter,
            onRefresh = viewModel::refresh,
            onRetryLoad = {
                viewModel.dismissError()
                viewModel.loadFiles()
            },
            onSelectAll = viewModel::selectAll,
            onToggleViewMode = viewModel::toggleViewMode,
            onSetSort = viewModel::setSort,
            onEnterEditMode = viewModel::enterEditMode,
            onEnterSelectionMode = viewModel::enterSelectionMode,
            onExitSelectionMode = viewModel::exitSelectionMode,
            onToggleFileSelection = viewModel::toggleFileSelection,
            onOpenImagePreview = viewModel::openImagePreview,
            onOpenFilePreview = viewModel::openFilePreview,
            onCloseFilePreview = viewModel::closeFilePreview,
            onCloseMediaPreview = viewModel::closeMediaPreview,
            onCancelUpload = viewModel::cancelUpload,
            onDeleteSelected = viewModel::deleteSelected,
            onDeleteFile = viewModel::deleteFile,
            onSubmitPdfPassword = viewModel::submitPdfPassword,
            onDismissPdfPassword = viewModel::dismissPdfPassword,
            onDownloadFile = viewModel::downloadFileBytes,
        ),
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        onBack = onBack,
        pickerMode = pickerMode,
        pdfPreview = { file, onDownloadFile, previewModifier -> PdfPreview(file, onDownloadFile, previewModifier) },
    )
}
