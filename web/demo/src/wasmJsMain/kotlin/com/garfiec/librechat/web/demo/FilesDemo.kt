package com.garfiec.librechat.web.demo

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.garfiec.librechat.core.ui.media.MediaItem
import com.garfiec.librechat.core.ui.media.MediaPreviewState
import com.garfiec.librechat.core.ui.platform.formatFileSize
import com.garfiec.librechat.feature.files.FileDisplayData
import com.garfiec.librechat.feature.files.FilePreviewDisplayData
import com.garfiec.librechat.feature.files.screen.FilesActions
import com.garfiec.librechat.feature.files.screen.FilesScreenContent
import com.garfiec.librechat.feature.files.viewmodel.FileSortField
import com.garfiec.librechat.feature.files.viewmodel.FileSortOrder
import com.garfiec.librechat.feature.files.viewmodel.FileTypeFilter
import com.garfiec.librechat.feature.files.viewmodel.FileViewMode
import com.garfiec.librechat.feature.files.viewmodel.FilesUiState
import kotlinx.browser.window
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * The app's Files screen over sample files. Filters, sorting, the grid, selection and delete work on
 * the sample list; text files open in the preview with their contents and photos in the photo viewer. Nothing uploads or downloads.
 */
@Composable
internal fun FilesDemo(onBack: (() -> Unit)?) {
    var files by remember { mutableStateOf(SampleFiles) }
    var filter by remember { mutableStateOf(FileTypeFilter.ALL) }
    var sortField by remember { mutableStateOf(FileSortField.DATE) }
    var sortOrder by remember { mutableStateOf(FileSortOrder.DESCENDING) }
    var viewMode by remember { mutableStateOf(FilesUiState().viewMode) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var previewing by remember { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<MediaPreviewState?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val sorted = files.sortedWith(sortField.comparator).let { if (sortOrder == FileSortOrder.DESCENDING) it.reversed() else it }
    val byFilter = FileTypeFilter.entries.associateWith { f -> sorted.filter { f.matches(it.type) }.map { it.display } }
    val toggle: (String) -> Unit = { id ->
        val next = if (id in selected) selected - id else selected + id
        selected = next
        if (next.isEmpty()) selecting = false
    }
    val delete: (Set<String>) -> Unit = { ids ->
        files = files.filterNot { it.id in ids }
        selected = emptySet()
        selecting = false
    }

    FilesScreenContent(
        uiState = FilesUiState(
            displayFiles = byFilter.getValue(filter),
            filesByFilter = byFilter,
            selectedFilter = filter,
            isSelectionMode = selecting,
            selectedFileIds = selected,
            sortField = sortField,
            sortOrder = sortOrder,
            previewFile = files.find { it.id == previewing }?.preview,
            hasFiles = files.isNotEmpty(),
            viewMode = viewMode,
            mediaPreview = viewing,
        ),
        actions = FilesActions(
            onUploadClick = { scope.launch { snackbar.showReplacing(notInTheDemo("Uploading files")) } },
            onFilterSettled = { filter = it },
            onSelectAll = { selected = selected + byFilter.getValue(filter).map { it.fileId } },
            onToggleViewMode = { viewMode = if (viewMode == FileViewMode.LIST) FileViewMode.GRID else FileViewMode.LIST },
            onSetSort = { field, order ->
                sortField = field
                sortOrder = order
            },
            onEnterEditMode = {
                selecting = true
                selected = emptySet()
            },
            onEnterSelectionMode = { id ->
                selecting = true
                selected = setOf(id)
            },
            onExitSelectionMode = {
                selecting = false
                selected = emptySet()
            },
            onToggleFileSelection = toggle,
            onOpenImagePreview = { id -> viewing = photoViewer(byFilter.getValue(FileTypeFilter.IMAGES), id) },
            onCloseMediaPreview = { viewing = null },
            onOpenFilePreview = { previewing = it },
            onCloseFilePreview = { previewing = null },
            onDeleteSelected = { delete(selected) },
            onDeleteFile = { delete(setOf(it)) },
            onDownloadFile = { id, _ -> files.find { it.id == id }?.text?.encodeToByteArray() },
        ),
        snackbarHostState = snackbar,
        onBack = onBack,
    )
}

// The photo viewer pages through the Images tab in its order, opening on the tapped photo, as the app does.
private fun photoViewer(images: List<FileDisplayData>, tapped: String): MediaPreviewState? {
    val items = images.mapNotNull { file -> file.previewUrl?.let { MediaItem(it, file.filename, file.filename) } }
    if (items.isEmpty()) return null
    val tappedUrl = images.firstOrNull { it.fileId == tapped }?.previewUrl
    return MediaPreviewState(items, items.indexOfFirst { it.url == tappedUrl }.coerceAtLeast(0))
}

/** A photo in the site's `demo/photos/`, by absolute URL: the image loader takes no relative one. */
private fun photoUrl(name: String) = window.location.href.substringBefore('#').substringBeforeLast('/') + "/photos/" + name

/** A file in the sample library; [text] is what a text file's preview shows. A photo's [name] is its file in `photos/`. */
private class SampleFile(
    val id: String,
    val name: String,
    val type: String,
    val bytes: Long,
    val ago: Duration,
    val text: String? = null,
) {
    private val createdAt = (PageLoadedAt - ago).toString()
    private val previewUrl = if (type.startsWith("image/")) photoUrl(name) else null
    val display = FileDisplayData(id, name, type, formatFileSize(bytes), createdAt, previewUrl)
    val preview = FilePreviewDisplayData(id, name, type, formatFileSize(bytes), createdAt, source = "local", userId = null)
}

private val FileSortField.comparator: Comparator<SampleFile>
    get() = when (this) {
        FileSortField.NAME -> compareBy { it.name.lowercase() }
        FileSortField.DATE -> compareByDescending { it.ago }
        FileSortField.SIZE -> compareBy { it.bytes }
        FileSortField.TYPE -> compareBy { it.type }
    }

private val SampleFiles = listOf(
    SampleFile(
        "stash-csv", "chocolate-stash.csv", "text/csv", 412, 40.minutes,
        text = """
            bar,brand,bought,eaten
            Dark 70%,Lindt,2026-11-20,no
            Sea salt caramel,Tony's,2026-11-20,yes
            Hazelnut,Ritter Sport,2026-11-21,no
            Milk,Cadbury,2026-11-22,yes
            Mint crisp,Aero,2026-11-23,no
        """.trimIndent(),
    ),
    SampleFile(
        "agent-rules", "agent-shopping-rules.md", "text/markdown", 688, 3.days,
        text = """
            # Shopping rules for my agent

            1. Never buy more than 5 chocolate bars a week.
            2. Ask before anything over ${'$'}20.
            3. Groceries first, snacks last.
            4. If the cart has only chocolate, stop and ask me why.
        """.trimIndent(),
    ),
    SampleFile(
        "game-night", "game-night-list.json", "application/json", 356, 5.hours,
        text = """
            {
              "guests": 6,
              "snacks": ["popcorn", "nachos", "brownies"],
              "drinks": ["sparkling water", "lemonade"],
              "games": ["Codenames", "Wavelength"]
            }
        """.trimIndent(),
    ),
    SampleFile("receipt", "grocery-receipt.pdf", "application/pdf", 184_320, 2.days),
    SampleFile("chocolate", "dark-chocolate.jpg", "image/jpeg", 58_053, 35.minutes),
    SampleFile("lighthouse", "lighthouse-at-night.jpg", "image/jpeg", 148_345, 50.minutes),
    SampleFile("dessert", "dessert-idea.jpg", "image/jpeg", 190_792, 2.hours),
    SampleFile("brownie", "last-brownie.jpg", "image/jpeg", 139_272, 5.hours),
    SampleFile("vacuum", "robot-vacuum-stuck.jpg", "image/jpeg", 152_464, 26.hours),
    SampleFile("whiskers", "whiskers-on-keyboard.jpg", "image/jpeg", 136_797, 2.days),
    SampleFile("pistachio", "pistachio-haul.jpg", "image/jpeg", 399_375, 4.days),
    SampleFile("memo", "voice-memo-snack-ideas.m4a", "audio/mp4", 845_000, 4.days),
    SampleFile("dog", "dog-vs-vacuum.mp4", "video/mp4", 14_680_064, 9.days),
)
