package com.garfiec.librechat.feature.files.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.garfiec.librechat.core.model.files.fileKindOf
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveCheckbox
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveFloatingActionButton
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.AdaptiveTabRow
import com.garfiec.librechat.core.ui.components.AdaptiveTabRowKind
import com.garfiec.librechat.core.ui.components.EmptyState
import com.garfiec.librechat.core.ui.components.ErrorBanner
import com.garfiec.librechat.core.ui.components.FileTypeTile
import com.garfiec.librechat.core.ui.components.PdfPasswordDialog
import com.garfiec.librechat.core.ui.components.PlatformBackHandler
import com.garfiec.librechat.core.ui.components.UnderBarsPullToRefreshBox
import com.garfiec.librechat.core.ui.components.selectedTabIndex
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarAction
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarMenuItem
import com.garfiec.librechat.core.ui.components.topbar.BarMenuSection
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTint
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.components.topbar.MaterialBarStyle
import com.garfiec.librechat.core.ui.datetime.rememberAbsoluteTimestamp
import com.garfiec.librechat.core.ui.glass.GlassBackdrop
import com.garfiec.librechat.core.ui.glass.LocalGlassBackdrop
import com.garfiec.librechat.core.ui.media.MediaActionBar
import com.garfiec.librechat.core.ui.media.MediaViewerHost
import com.garfiec.librechat.core.ui.media.mediaThumbnail
import com.garfiec.librechat.core.ui.media.rememberSaveImageToGallery
import com.garfiec.librechat.core.ui.media.rememberShareImage
import com.garfiec.librechat.feature.files.FileDisplayData
import com.garfiec.librechat.feature.files.components.UploadProgressCard
import com.garfiec.librechat.feature.files.resources.*
import com.garfiec.librechat.feature.files.resources.Res
import com.garfiec.librechat.feature.files.viewmodel.FileSortField
import com.garfiec.librechat.feature.files.viewmodel.FileSortOrder
import com.garfiec.librechat.feature.files.viewmodel.FileTypeFilter
import com.garfiec.librechat.feature.files.viewmodel.FileViewMode
import com.garfiec.librechat.feature.files.viewmodel.FilesUiState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** What the files screen does on a tap; `FilesScreen` binds each to its ViewModel and the platform picker. */
class FilesActions(
    val onUploadClick: () -> Unit = {},
    val onConfirmSelection: () -> Unit = {},
    val onFilterSettled: (FileTypeFilter) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetryLoad: () -> Unit = {},
    val onSelectAll: () -> Unit = {},
    val onToggleViewMode: () -> Unit = {},
    val onSetSort: (FileSortField, FileSortOrder) -> Unit = { _, _ -> },
    val onEnterEditMode: () -> Unit = {},
    val onEnterSelectionMode: (fileId: String) -> Unit = {},
    val onExitSelectionMode: () -> Unit = {},
    val onToggleFileSelection: (fileId: String) -> Unit = {},
    val onOpenImagePreview: (fileId: String) -> Unit = {},
    val onOpenFilePreview: (fileId: String) -> Unit = {},
    val onCloseFilePreview: () -> Unit = {},
    val onCloseMediaPreview: () -> Unit = {},
    val onCancelUpload: () -> Unit = {},
    val onDeleteSelected: () -> Unit = {},
    val onDeleteFile: (fileId: String) -> Unit = {},
    val onSubmitPdfPassword: (String) -> Unit = {},
    val onDismissPdfPassword: () -> Unit = {},
    val onDownloadFile: suspend (fileId: String, userId: String?) -> ByteArray? = { _, _ -> null },
)

/**
 * The files list. With [pickerMode] it is an attachment picker: selection is always on, tapping a
 * row toggles it, and the top bar's Attach calls [FilesActions.onConfirmSelection]. [pdfPreview]
 * draws a PDF in the preview dialog where the platform can.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreenContent(
    uiState: FilesUiState,
    actions: FilesActions,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    pickerMode: Boolean = false,
    pdfPreview: PdfPreviewSlot? = null,
) {
    val currentActions by rememberUpdatedState(actions)
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var singleDeleteFileId by remember { mutableStateOf<String?>(null) }
    var showCancelSelectionConfirmation by remember { mutableStateOf(false) }

    val requestExitSelection = {
        if (uiState.selectedFileIds.isEmpty()) {
            actions.onExitSelectionMode()
        } else {
            showCancelSelectionConfirmation = true
        }
    }

    // Gated so it never arms in picker mode (back should close the picker) or outside selection mode.
    PlatformBackHandler(enabled = uiState.isSelectionMode && !pickerMode) {
        requestExitSelection()
    }

    // The pager owns the filter: a swipe or a tab tap moves it, and the view model follows once it
    // settles, so Select all and the image viewer act on the page in view.
    val filters = FileTypeFilter.entries
    val pagerState = rememberPagerState(initialPage = filters.indexOf(uiState.selectedFilter)) { filters.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { currentActions.onFilterSettled(filters[it]) }
    }

    uiState.pdfPasswordPrompt?.let { prompt ->
        // Keyed so a re-prompt after a wrong password starts from an empty field.
        key(prompt) {
            PdfPasswordDialog(
                filename = prompt.filename,
                incorrectPassword = prompt.incorrectPassword,
                onSubmit = actions.onSubmitPdfPassword,
                onDismiss = actions.onDismissPdfPassword,
            )
        }
    }

    // Remembered outside the viewer so the save/share scope (and the permission launcher) live as
    // long as the screen, letting an in-flight save/share survive the viewer being dismissed.
    val saveImage = rememberSaveImageToGallery()
    val shareImage = rememberShareImage()
    val layoutDirection = LocalLayoutDirection.current
    // Resolved once here (not inside the per-item actions slot) so paging doesn't re-resolve
    // string resources on every swipe.
    val saveDescription = stringResource(Res.string.cd_save_image)
    val shareDescription = stringResource(Res.string.cd_share_image)
    MediaViewerHost(
        preview = uiState.mediaPreview,
        onDismiss = actions.onCloseMediaPreview,
        closeContentDescription = stringResource(Res.string.cd_close_preview),
        defaultContentDescription = stringResource(Res.string.cd_image),
        actions = { item ->
            MediaActionBar(
                item = item,
                onSave = saveImage,
                onShare = shareImage,
                saveContentDescription = saveDescription,
                shareContentDescription = shareDescription,
            )
        },
    ) {
        AdaptiveScaffold(
            modifier = modifier,
            snackbarHost = { AdaptiveSnackbarHost(snackbarHostState) },
            topBar = {
                val spec = if (pickerMode) {
                    AdaptiveTopBarSpec(
                        navigation = onBack?.let {
                            BarNavigation(BarIcons.Close, stringResource(Res.string.cd_close_picker), it)
                        },
                        title = BarTitle(
                            if (uiState.selectedFileIds.isEmpty()) {
                                stringResource(Res.string.select_files)
                            } else {
                                stringResource(Res.string.selected_count, uiState.selectedFileIds.size)
                            },
                        ),
                        actions = listOf(
                            BarAction.Icon(
                                id = "select_all",
                                icon = BarIcons.SelectAll,
                                label = stringResource(Res.string.cd_select_all),
                                onClick = actions.onSelectAll,
                            ),
                            BarAction.Icon(
                                id = "view_mode",
                                icon = if (uiState.viewMode == FileViewMode.LIST) BarIcons.GridView else BarIcons.ListView,
                                label = if (uiState.viewMode == FileViewMode.LIST) {
                                    stringResource(Res.string.cd_switch_to_grid)
                                } else {
                                    stringResource(Res.string.cd_switch_to_list)
                                },
                                onClick = actions.onToggleViewMode,
                            ),
                            BarAction.Text(
                                id = "attach",
                                label = stringResource(Res.string.attach_count, uiState.selectedFileIds.size),
                                compactIcon = BarIcons.Attach,
                                enabled = uiState.selectedFileIds.isNotEmpty(),
                                onClick = actions.onConfirmSelection,
                            ),
                        ),
                    )
                } else if (uiState.isSelectionMode) {
                    AdaptiveTopBarSpec(
                        navigation = BarNavigation(
                            BarIcons.Close,
                            stringResource(Res.string.cd_exit_edit_mode),
                            requestExitSelection,
                        ),
                        title = BarTitle(
                            if (uiState.selectedFileIds.isEmpty()) {
                                stringResource(Res.string.select_files)
                            } else {
                                stringResource(Res.string.selected_count, uiState.selectedFileIds.size)
                            },
                        ),
                        actions = listOf(
                            BarAction.Icon(
                                id = "select_all",
                                icon = BarIcons.SelectAll,
                                label = stringResource(Res.string.cd_select_all),
                                onClick = actions.onSelectAll,
                            ),
                            BarAction.Icon(
                                id = "delete",
                                icon = BarIcons.DeleteFilled,
                                label = stringResource(Res.string.cd_delete_selected),
                                tint = BarTint.DESTRUCTIVE,
                                enabled = uiState.selectedFileIds.isNotEmpty(),
                                onClick = { showDeleteConfirmation = true },
                            ),
                            BarAction.Text(
                                id = "done",
                                label = stringResource(Res.string.done),
                                compactIcon = BarIcons.Confirm,
                                onClick = actions.onExitSelectionMode,
                            ),
                        ),
                    )
                } else {
                    AdaptiveTopBarSpec(
                        navigation = onBack?.let { BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), it) },
                        title = BarTitle(stringResource(Res.string.files)),
                        actions = buildList {
                            if (uiState.hasFiles) {
                                add(
                                    BarAction.Icon(
                                        id = "edit",
                                        icon = BarIcons.EditFilled,
                                        label = stringResource(Res.string.cd_edit_files),
                                        onClick = actions.onEnterEditMode,
                                    ),
                                )
                            }
                            add(
                                BarAction.Icon(
                                    id = "view_mode",
                                    icon = if (uiState.viewMode == FileViewMode.LIST) BarIcons.GridView else BarIcons.ListView,
                                    label = if (uiState.viewMode == FileViewMode.LIST) {
                                        stringResource(Res.string.cd_switch_to_grid)
                                    } else {
                                        stringResource(Res.string.cd_switch_to_list)
                                    },
                                    onClick = actions.onToggleViewMode,
                                ),
                            )
                            add(
                                BarAction.Menu(
                                    id = "sort",
                                    icon = BarIcons.Sort,
                                    label = stringResource(Res.string.cd_sort_files),
                                    sections = listOf(
                                        BarMenuSection(
                                            FileSortField.entries.map { field ->
                                                val isSelected = field == uiState.sortField
                                                BarMenuItem(
                                                    id = field.name,
                                                    label = stringResource(field.label),
                                                    icon = null,
                                                    checked = isSelected,
                                                    subtitle = if (!isSelected) {
                                                        null
                                                    } else if (uiState.sortOrder == FileSortOrder.ASCENDING) {
                                                        stringResource(Res.string.cd_ascending)
                                                    } else {
                                                        stringResource(Res.string.cd_descending)
                                                    },
                                                    // Re-selecting the active field flips the order; a new field starts descending.
                                                    onClick = {
                                                        val order = when {
                                                            !isSelected -> FileSortOrder.DESCENDING
                                                            uiState.sortOrder == FileSortOrder.ASCENDING -> FileSortOrder.DESCENDING
                                                            else -> FileSortOrder.ASCENDING
                                                        }
                                                        actions.onSetSort(field, order)
                                                    },
                                                )
                                            },
                                        ),
                                    ),
                                ),
                            )
                        },
                    )
                }
                AdaptiveTopBar(
                    spec = spec,
                    materialStyle = MaterialBarStyle(translucent = true),
                    belowBar = {
                        FileTypeFilterTabs(
                            selected = filters[pagerState.selectedTabIndex()],
                            onSelect = { scope.launch { pagerState.animateScrollToPage(filters.indexOf(it)) } },
                            backdrop = LocalGlassBackdrop.current,
                            containerColor = Color.Transparent,
                        )
                    },
                )
            },
            floatingActionButton = {
                if (!uiState.isSelectionMode && !pickerMode) {
                    AdaptiveFloatingActionButton(
                        onClick = actions.onUploadClick,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(Res.string.cd_upload_file),
                        )
                    }
                }
            },
        ) { padding ->
            UnderBarsPullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = actions.onRefresh,
                barPadding = padding,
                modifier = Modifier.fillMaxSize(),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    val filter = filters[page]
                    val pageFiles = uiState.filesByFilter[filter].orEmpty()
                    when {
                        uiState.isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(padding),
                                contentAlignment = Alignment.Center,
                            ) {
                                AdaptiveCircularProgressIndicator()
                            }
                        }
                        uiState.error != null && !uiState.hasFiles -> {
                            ErrorBanner(
                                message = uiState.error ?: stringResource(Res.string.could_not_load_files),
                                modifier = Modifier.padding(padding),
                                onRetry = actions.onRetryLoad,
                            )
                        }
                        pageFiles.isEmpty() && !uiState.isRefreshing -> {
                            FilesEmptyState(filter, modifier = Modifier.padding(padding))
                        }
                        else -> {
                            // Room for the last row to scroll clear of the upload FAB.
                            val fabClearance = if (!uiState.isSelectionMode && !pickerMode) FabClearance else 0.dp
                            when (uiState.viewMode) {
                                FileViewMode.LIST -> {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = padding + PaddingValues(bottom = fabClearance),
                                    ) {
                                        items(
                                            items = pageFiles,
                                            key = { it.fileId },
                                            contentType = { "file" },
                                        ) { file ->
                                            FileItem(
                                                file = file,
                                                isEditMode = uiState.isSelectionMode || pickerMode,
                                                isSelected = file.fileId in uiState.selectedFileIds,
                                                // The picker must never delete — it only attaches by reference.
                                                showDelete = !pickerMode,
                                                onDelete = { singleDeleteFileId = file.fileId },
                                                onLongClick = {
                                                    if (!pickerMode) {
                                                        actions.onEnterSelectionMode(file.fileId)
                                                    }
                                                },
                                                onClick = {
                                                    if (uiState.isSelectionMode || pickerMode) {
                                                        actions.onToggleFileSelection(file.fileId)
                                                    } else if (file.type.startsWith("image/")) {
                                                        actions.onOpenImagePreview(file.fileId)
                                                    } else {
                                                        actions.onOpenFilePreview(file.fileId)
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                                FileViewMode.GRID -> {
                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(minSize = FileGridCellMinSize),
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = padding +
                                            PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 8.dp + fabClearance),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(
                                            items = pageFiles,
                                            key = { it.fileId },
                                            contentType = { "file_grid" },
                                        ) { file ->
                                            FileGridItem(
                                                file = file,
                                                isSelectionMode = uiState.isSelectionMode || pickerMode,
                                                isSelected = file.fileId in uiState.selectedFileIds,
                                                onLongClick = {
                                                    if (!pickerMode) {
                                                        actions.onEnterSelectionMode(file.fileId)
                                                    }
                                                },
                                                onClick = {
                                                    if (uiState.isSelectionMode || pickerMode) {
                                                        actions.onToggleFileSelection(file.fileId)
                                                    } else if (file.type.startsWith("image/")) {
                                                        actions.onOpenImagePreview(file.fileId)
                                                    } else {
                                                        actions.onOpenFilePreview(file.fileId)
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Upload progress overlay
                UploadProgressCard(
                    visible = uiState.isUploading,
                    filename = uiState.uploadFilename,
                    progress = uiState.uploadProgress,
                    onCancel = actions.onCancelUpload,
                    modifier = Modifier.align(Alignment.TopCenter).padding(
                        top = padding.calculateTopPadding(),
                        start = padding.calculateStartPadding(layoutDirection),
                        end = padding.calculateEndPadding(layoutDirection),
                    ),
                )
            }
        }
    }

    // Multi-file delete confirmation
    if (showDeleteConfirmation) {
        val count = uiState.selectedFileIds.size
        AdaptiveAlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(deleteFilesTitle(count)) },
            text = {
                Text(
                    text = stringResource(Res.string.action_cannot_be_undone),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    actions.onDeleteSelected()
                    showDeleteConfirmation = false
                }) {
                    Text(
                        text = stringResource(Res.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    // Cancel-selection confirmation (back gesture / close with files selected)
    if (showCancelSelectionConfirmation) {
        AdaptiveAlertDialog(
            onDismissRequest = { showCancelSelectionConfirmation = false },
            title = { Text(stringResource(Res.string.discard_selection_title)) },
            text = {
                Text(
                    text = stringResource(Res.string.discard_selection_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    actions.onExitSelectionMode()
                    showCancelSelectionConfirmation = false
                }) {
                    Text(
                        text = stringResource(Res.string.discard),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelSelectionConfirmation = false }) {
                    Text(stringResource(Res.string.keep_selecting))
                }
            },
        )
    }

    // Single-file delete confirmation
    val pendingDeleteId = singleDeleteFileId
    if (pendingDeleteId != null) {
        val fileName = uiState.filesByFilter[FileTypeFilter.ALL].orEmpty()
            .find { it.fileId == pendingDeleteId }?.filename ?: stringResource(Res.string.this_file)
        AdaptiveAlertDialog(
            onDismissRequest = { singleDeleteFileId = null },
            title = { Text(stringResource(Res.string.delete_file_question)) },
            text = {
                Text(
                    text = stringResource(Res.string.delete_file_message, fileName),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    actions.onDeleteFile(pendingDeleteId)
                    singleDeleteFileId = null
                }) {
                    Text(
                        text = stringResource(Res.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { singleDeleteFileId = null }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    // File preview dialog
    val previewFile = uiState.previewFile
    if (previewFile != null) {
        FilePreviewDialog(
            file = previewFile,
            onDismiss = actions.onCloseFilePreview,
            onDownloadFile = actions.onDownloadFile,
            pdfPreview = pdfPreview,
        )
    }
}

@Composable
internal fun FileTypeFilterTabs(
    selected: FileTypeFilter,
    onSelect: (FileTypeFilter) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    containerColor: Color = Color.Unspecified,
) {
    val filters = FileTypeFilter.entries
    AdaptiveTabRow(
        titles = filters.map { stringResource(it.label) },
        selectedIndex = filters.indexOf(selected),
        onSelect = { onSelect(filters[it]) },
        modifier = modifier,
        kind = AdaptiveTabRowKind.SECONDARY,
        backdrop = backdrop,
        containerColor = containerColor,
    )
}

@Composable
internal fun FilesEmptyState(filter: FileTypeFilter, modifier: Modifier = Modifier) {
    val emptyTitle = filter.emptyTitle
    EmptyState(
        title = stringResource(emptyTitle ?: Res.string.no_files),
        description = if (emptyTitle == null) {
            stringResource(Res.string.upload_to_get_started)
        } else {
            stringResource(Res.string.no_filter_files_hint)
        },
        icon = Icons.Default.Folder,
        modifier = modifier,
    )
}

@Composable
internal fun deleteFilesTitle(count: Int): String = pluralStringResource(Res.plurals.delete_count_files, count, count)

@Composable
private fun FileItem(
    file: FileDisplayData,
    isEditMode: Boolean,
    isSelected: Boolean,
    onDelete: () -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDelete: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isEditMode) {
                AdaptiveCheckbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            FileTypeTile(kind = remember(file.filename, file.type) { fileKindOf(file.filename, file.type) }, size = 40.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.filename,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val created = file.createdAt?.let { rememberAbsoluteTimestamp(it) }
                Text(
                    text = buildString {
                        append(file.formattedSize)
                        created?.let { append(" \u00B7 $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isEditMode && showDelete) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(Res.string.cd_delete_file, file.filename),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        AdaptiveDivider()
    }
}

private val FabClearance = 96.dp

/** The narrowest a grid tile gets: two columns fit a 320dp phone. */
internal val FileGridCellMinSize = 144.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileGridItem(
    file: FileDisplayData,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Box {
            Column {
                // Thumbnail area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    if (file.type.startsWith("image/") && file.previewUrl != null) {
                        AsyncImage(
                            model = file.previewUrl,
                            contentDescription = stringResource(Res.string.cd_thumbnail, file.filename),
                            modifier = Modifier
                                .fillMaxSize()
                                .mediaThumbnail(file.previewUrl, MaterialTheme.shapes.medium),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        FileTypeTile(
                            kind = remember(file.filename, file.type) { fileKindOf(file.filename, file.type) },
                            size = 56.dp,
                        )
                    }
                }

                // File info
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = file.filename,
                        style = MaterialTheme.typography.bodySmall,
                        // Two lines even for a short name, so tiles in a grid row line up.
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = file.formattedSize,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            // Selection checkbox overlay
            if (isSelectionMode) {
                AdaptiveCheckbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                )
            }
        }
    }
}
