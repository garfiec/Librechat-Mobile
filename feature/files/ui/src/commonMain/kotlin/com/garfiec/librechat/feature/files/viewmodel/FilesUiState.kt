package com.garfiec.librechat.feature.files.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.ui.components.PdfPasswordPromptUi
import com.garfiec.librechat.core.ui.media.MediaPreviewState
import com.garfiec.librechat.feature.files.FileDisplayData
import com.garfiec.librechat.feature.files.FilePreviewDisplayData
import com.garfiec.librechat.feature.files.resources.Res
import com.garfiec.librechat.feature.files.resources.filter_all
import com.garfiec.librechat.feature.files.resources.filter_audio
import com.garfiec.librechat.feature.files.resources.filter_documents
import com.garfiec.librechat.feature.files.resources.filter_images
import com.garfiec.librechat.feature.files.resources.filter_video
import com.garfiec.librechat.feature.files.resources.no_filter_audio
import com.garfiec.librechat.feature.files.resources.no_filter_documents
import com.garfiec.librechat.feature.files.resources.no_filter_images
import com.garfiec.librechat.feature.files.resources.no_filter_video
import com.garfiec.librechat.feature.files.resources.sort_date
import com.garfiec.librechat.feature.files.resources.sort_name
import com.garfiec.librechat.feature.files.resources.sort_size
import com.garfiec.librechat.feature.files.resources.sort_type
import org.jetbrains.compose.resources.StringResource

/** [emptyTitle] is null for [ALL], whose empty state is the general "no files" one. */
enum class FileTypeFilter(val label: StringResource, val emptyTitle: StringResource?) {
    ALL(Res.string.filter_all, null),
    IMAGES(Res.string.filter_images, Res.string.no_filter_images),
    DOCUMENTS(Res.string.filter_documents, Res.string.no_filter_documents),
    AUDIO(Res.string.filter_audio, Res.string.no_filter_audio),
    VIDEO(Res.string.filter_video, Res.string.no_filter_video),
    ;

    fun matches(type: String): Boolean = when (this) {
        ALL -> true
        IMAGES -> type.startsWith("image/")
        DOCUMENTS -> type.startsWith("application/") || type.startsWith("text/")
        AUDIO -> type.startsWith("audio/")
        VIDEO -> type.startsWith("video/")
    }
}

// Stored strings are decoupled from the constant names so renaming a constant can't orphan a saved
// preference (matches StarredModelsDisplay/ContextBarPlacement/etc. in :core:data).
enum class FileSortField(val label: StringResource) {
    NAME(Res.string.sort_name),
    DATE(Res.string.sort_date),
    SIZE(Res.string.sort_size),
    TYPE(Res.string.sort_type);

    companion object {
        fun fromString(value: String?): FileSortField = when (value) {
            "name" -> NAME
            "size" -> SIZE
            "type" -> TYPE
            else -> DATE
        }
    }

    fun toStorageString(): String = when (this) {
        NAME -> "name"
        DATE -> "date"
        SIZE -> "size"
        TYPE -> "type"
    }
}

enum class FileSortOrder {
    ASCENDING,
    DESCENDING;

    companion object {
        fun fromString(value: String?): FileSortOrder =
            if (value == "ascending") ASCENDING else DESCENDING
    }

    fun toStorageString(): String = when (this) {
        ASCENDING -> "ascending"
        DESCENDING -> "descending"
    }
}

enum class FileViewMode {
    LIST,
    GRID;

    companion object {
        fun fromString(value: String?): FileViewMode = when (value) {
            "grid" -> GRID
            else -> LIST
        }
    }

    fun toStorageString(): String = when (this) {
        LIST -> "list"
        GRID -> "grid"
    }
}

@Immutable
data class FilesUiState(
    /** The selected filter's page: `filesByFilter[selectedFilter]`. */
    val displayFiles: List<FileDisplayData> = emptyList(),
    /** Every filter's page, so the pager can show the one being swiped in before it's selected. */
    val filesByFilter: Map<FileTypeFilter, List<FileDisplayData>> = emptyMap(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isUploading: Boolean = false,
    val uploadFilename: String = "",
    val uploadProgress: Float? = null,
    val error: String? = null,
    val selectedFilter: FileTypeFilter = FileTypeFilter.ALL,
    val isSelectionMode: Boolean = false,
    val isPickerMode: Boolean = false,
    val selectedFileIds: Set<String> = emptySet(),
    val sortField: FileSortField = FileSortField.DATE,
    val sortOrder: FileSortOrder = FileSortOrder.DESCENDING,
    val previewFile: FilePreviewDisplayData? = null,
    val mediaPreview: MediaPreviewState? = null,
    val hasFiles: Boolean = false,
    val viewMode: FileViewMode = FileViewMode.LIST,
    /**
     * MIME types to filter the system file picker to, derived from the server's
     * `supportedMimeTypes` allowlist. Empty means "show everything" — either the server
     * configured no allowlist, or it configured one we can't represent faithfully.
     */
    val pickerMimeTypes: List<String> = emptyList(),
    /** A picked PDF needs its password before it can be uploaded; the screen asks for it. */
    val pdfPasswordPrompt: PdfPasswordPromptUi? = null,
)
