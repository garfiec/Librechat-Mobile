package com.garfiec.librechat.feature.conversations.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.response.AvailableProjectFile

/** What a project file row shows as its status. */
enum class ProjectFileStatus { READY, UNAVAILABLE, PROCESSING, FAILED }

/** Why an upload row failed; the screen words each (the limit names the cap). */
enum class ProjectUploadFailure { UNREADABLE, UPLOAD_FAILED, NOT_SEARCHABLE, FILE_LIMIT, ATTACH_FAILED }

/**
 * One row of the Files card: an attached file, or an upload in flight or failed. [fileId] is the
 * server's id once there is one; [key] is stable across the row's life.
 */
@Immutable
data class ProjectFileRow(
    val key: String,
    val fileId: String?,
    val name: String?,
    val status: ProjectFileStatus,
    val failure: ProjectUploadFailure? = null,
    val isRemoving: Boolean = false,
) {
    val isUpload: Boolean get() = key.startsWith(UPLOAD_KEY_PREFIX)

    /** A failed upload can try again unless the server said the file can't be searched. */
    val canRetry: Boolean get() = status == ProjectFileStatus.FAILED && failure != ProjectUploadFailure.NOT_SEARCHABLE
}

@Immutable
data class ProjectFilesUi(
    val rows: List<ProjectFileRow> = emptyList(),
    /** Files attached on the server. */
    val attachedCount: Int = 0,
    val unavailableCount: Int = 0,
    /** Attached plus uploads still running: what the cap is counted against. */
    val usedCount: Int = 0,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
)

/** The "Choose from your files" sheet. */
@Immutable
data class AvailableFilesUi(
    val isOpen: Boolean = false,
    val query: String = "",
    val files: List<AvailableProjectFile> = emptyList(),
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val nextCursor: String? = null,
    val addingFileId: String? = null,
)

/** The project page's Instructions and Files cards, as the screen draws them. */
@Immutable
data class ProjectContextUi(
    /** The saved instructions, never a draft. Blank shows the empty-state line. */
    val instructions: String,
    /** Null hides the Files card (no RAG API on the server). */
    val files: ProjectFilesUi?,
    val canUploadFiles: Boolean,
    val maxFiles: Int,
) {
    val atFileLimit: Boolean get() = files != null && files.usedCount >= maxFiles
}

const val UPLOAD_KEY_PREFIX = "upload:"
