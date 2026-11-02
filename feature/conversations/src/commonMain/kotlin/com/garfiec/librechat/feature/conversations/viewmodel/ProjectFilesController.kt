package com.garfiec.librechat.feature.conversations.viewmodel

import androidx.compose.runtime.Immutable
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.FileRepository
import com.garfiec.librechat.core.data.repository.ProjectContextFailure
import com.garfiec.librechat.core.data.repository.ProjectRepository
import com.garfiec.librechat.core.data.repository.toProjectContextFailure
import com.garfiec.librechat.core.data.repository.toProjectFileAddFailure
import com.garfiec.librechat.core.model.ChatProject
import com.garfiec.librechat.core.model.response.AvailableProjectFile
import com.garfiec.librechat.core.model.response.ChatProjectFile
import com.garfiec.librechat.feature.conversations.platform.PickedProjectFile
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.project_file_added
import com.garfiec.librechat.feature.conversations.resources.project_file_attach_failed
import com.garfiec.librechat.feature.conversations.resources.project_file_remove_failed
import com.garfiec.librechat.feature.conversations.resources.project_file_removed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

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

/** One-shot notices from the Files card. */
sealed interface ProjectFilesMessage {
    data class Error(val message: StringResource) : ProjectFilesMessage

    /** The add was refused at the project's file limit. */
    data class AtLimit(val maxFiles: Int) : ProjectFilesMessage

    /** [count] picked files were left out because the project was full. */
    data class Excess(val count: Int) : ProjectFilesMessage

    /** Spoken to a screen reader only ("File added"). */
    data class Announce(val message: StringResource) : ProjectFilesMessage
}

internal const val UPLOAD_KEY_PREFIX = "upload:"

/**
 * A project's reference files (v0.8.9-rc1): the list, uploads from the device, the picker of the
 * user's own files, and removal. Network-direct, like the rest of the project page.
 *
 * Uploads run one at a time, as web's do. An upload is only a temporary attachment until the add
 * succeeds, so a failed add keeps the server's file id and its Retry repeats only the add.
 * Every successful add or remove hands the updated project to [onProjectUpdated]: its new
 * `contextRevision` is what the next instructions save must carry. A 404 calls [onProjectGone].
 */
class ProjectFilesController(
    private val scope: CoroutineScope,
    private val projectId: String,
    private val projectRepository: ProjectRepository,
    private val fileRepository: FileRepository,
    private val maxFiles: () -> Int,
    private val onProjectUpdated: (ChatProject) -> Unit,
    private val onProjectGone: () -> Unit,
) {
    private data class Upload(
        val key: String,
        val file: PickedProjectFile,
        val fileId: String? = null,
        val failure: ProjectUploadFailure? = null,
    )

    private val attached = MutableStateFlow<List<ChatProjectFile>>(emptyList())
    private val uploads = MutableStateFlow<List<Upload>>(emptyList())
    private val removing = MutableStateFlow<Set<String>>(emptySet())

    private val _state = MutableStateFlow(ProjectFilesUi())
    val state: StateFlow<ProjectFilesUi> = _state.asStateFlow()

    private val _available = MutableStateFlow(AvailableFilesUi())
    val available: StateFlow<AvailableFilesUi> = _available.asStateFlow()

    private val _messages = MutableSharedFlow<ProjectFilesMessage>(extraBufferCapacity = MESSAGE_BUFFER)
    val messages: SharedFlow<ProjectFilesMessage> = _messages.asSharedFlow()

    private val queue = Channel<String>(Channel.UNLIMITED)
    private var uploadCounter = 0
    private var loadJob: Job? = null
    private var availableJob: Job? = null

    /** Whether the list has been asked for at least once, so a host loads it only on first show. */
    var hasLoaded: Boolean = false
        private set

    init {
        scope.launch { for (key in queue) runUpload(key) }
    }

    private fun publish(isLoading: Boolean = _state.value.isLoading, loadFailed: Boolean = _state.value.loadFailed) {
        val removingIds = removing.value
        val attachedRows = attached.value.map { file ->
            ProjectFileRow(
                key = file.fileId,
                fileId = file.fileId,
                name = file.filename,
                status = if (file.isReady) ProjectFileStatus.READY else ProjectFileStatus.UNAVAILABLE,
                isRemoving = file.fileId in removingIds,
            )
        }
        val uploadRows = uploads.value.map { upload ->
            ProjectFileRow(
                key = upload.key,
                fileId = upload.fileId,
                name = upload.file.name,
                status = if (upload.failure != null) ProjectFileStatus.FAILED else ProjectFileStatus.PROCESSING,
                failure = upload.failure,
            )
        }
        _state.value = ProjectFilesUi(
            rows = attachedRows + uploadRows,
            attachedCount = attached.value.size,
            unavailableCount = attached.value.count { !it.isReady },
            usedCount = attached.value.size + uploads.value.count { it.failure == null },
            isLoading = isLoading,
            loadFailed = loadFailed,
        )
    }

    /** Loads (or reloads) the attached files. Rows already shown stay up while it runs. */
    fun load() {
        hasLoaded = true
        loadJob?.cancel()
        publish(isLoading = true, loadFailed = false)
        loadJob = scope.launch {
            when (val result = projectRepository.listProjectFiles(projectId)) {
                is Result.Success -> {
                    attached.value = result.data
                    publish(isLoading = false, loadFailed = false)
                }
                is Result.Error -> {
                    if (result.toProjectContextFailure() == ProjectContextFailure.NotFound) {
                        onProjectGone()
                    } else {
                        Logger.w(result.exception) { "Failed to load project files" }
                    }
                    publish(isLoading = false, loadFailed = true)
                }
                is Result.Loading -> Unit
            }
        }
    }

    /**
     * Queues [files] for upload. Only as many as the project still has room for are taken; the rest
     * are reported as one [ProjectFilesMessage.Excess].
     */
    fun upload(files: List<PickedProjectFile>) {
        if (files.isEmpty()) return
        val room = (maxFiles() - _state.value.usedCount).coerceAtLeast(0)
        val accepted = files.take(room).map { Upload(key = "$UPLOAD_KEY_PREFIX${uploadCounter++}", file = it) }
        if (accepted.isNotEmpty()) {
            uploads.update { it + accepted }
            publish()
            accepted.forEach { queue.trySend(it.key) }
        }
        if (files.size > accepted.size) _messages.tryEmit(ProjectFilesMessage.Excess(files.size - accepted.size))
    }

    /** Tries a failed upload again: only the add when the file already reached the server. */
    fun retry(key: String) {
        val upload = uploads.value.firstOrNull { it.key == key } ?: return
        if (upload.failure == null || upload.failure == ProjectUploadFailure.NOT_SEARCHABLE) return
        updateUpload(key) { it.copy(failure = null) }
        queue.trySend(key)
    }

    /** Drops a failed upload's row. */
    fun dismiss(key: String) {
        uploads.update { list -> list.filterNot { it.key == key && it.failure != null } }
        publish()
    }

    private fun updateUpload(key: String, transform: (Upload) -> Upload) {
        uploads.update { list -> list.map { if (it.key == key) transform(it) else it } }
        publish()
    }

    private suspend fun runUpload(key: String) {
        val upload = uploads.value.firstOrNull { it.key == key } ?: return
        val fileId = upload.fileId ?: uploadToServer(upload) ?: return
        when (val result = projectRepository.addProjectFile(projectId, fileId)) {
            is Result.Success -> {
                uploads.update { list -> list.filterNot { it.key == key } }
                onAttached(result.data)
            }
            is Result.Error -> {
                val failure = result.toProjectFileAddFailure()
                if (failure == ProjectContextFailure.NotFound) {
                    onProjectGone()
                    return
                }
                Logger.w(result.exception) { "Failed to add an uploaded file to project $projectId" }
                updateUpload(key) {
                    it.copy(
                        failure = if (failure == ProjectContextFailure.FileLimit) {
                            ProjectUploadFailure.FILE_LIMIT
                        } else {
                            ProjectUploadFailure.ATTACH_FAILED
                        },
                    )
                }
            }
            is Result.Loading -> Unit
        }
    }

    /** Uploads [upload]'s bytes as a file-search attachment; the server's file id, or null on failure. */
    private suspend fun uploadToServer(upload: Upload): String? {
        val bytes = upload.file.readBytes()
        if (bytes == null) {
            updateUpload(upload.key) { it.copy(failure = ProjectUploadFailure.UNREADABLE) }
            return null
        }
        return when (
            val result = fileRepository.uploadFile(
                bytes = bytes,
                filename = upload.file.name,
                type = upload.file.mimeType,
                endpoint = AGENTS_ENDPOINT,
                toolResource = FILE_SEARCH_RESOURCE,
                messageFile = true,
            )
        ) {
            is Result.Success -> {
                // Web refuses an upload the RAG API didn't index before attaching it: the add
                // would be refused or the file never found by search.
                if (result.data.embedded != true) {
                    updateUpload(upload.key) { it.copy(failure = ProjectUploadFailure.NOT_SEARCHABLE) }
                    null
                } else {
                    updateUpload(upload.key) { it.copy(fileId = result.data.fileId) }
                    result.data.fileId
                }
            }
            is Result.Error -> {
                Logger.w(result.exception) { "Failed to upload a project file" }
                updateUpload(upload.key) { it.copy(failure = ProjectUploadFailure.UPLOAD_FAILED) }
                null
            }
            is Result.Loading -> null
        }
    }

    private fun onAttached(project: ChatProject) {
        onProjectUpdated(project)
        _messages.tryEmit(ProjectFilesMessage.Announce(Res.string.project_file_added))
        load()
    }

    /** Detaches [fileId]. Not optimistic: the row stays until the server answers. */
    fun remove(fileId: String) {
        if (fileId in removing.value) return
        removing.update { it + fileId }
        publish()
        scope.launch {
            when (val result = projectRepository.removeProjectFile(projectId, fileId)) {
                is Result.Success -> {
                    attached.update { list -> list.filterNot { it.fileId == fileId } }
                    onProjectUpdated(result.data)
                    _messages.tryEmit(ProjectFilesMessage.Announce(Res.string.project_file_removed))
                }
                is Result.Error -> {
                    if (result.toProjectContextFailure() == ProjectContextFailure.NotFound) {
                        onProjectGone()
                    } else {
                        Logger.w(result.exception) { "Failed to remove project file $fileId" }
                        _messages.tryEmit(ProjectFilesMessage.Error(Res.string.project_file_remove_failed))
                    }
                }
                is Result.Loading -> Unit
            }
            removing.update { it - fileId }
            publish()
        }
    }

    // --- Choose from your files ---

    fun openPicker() {
        _available.value = AvailableFilesUi(isOpen = true)
        loadAvailable(reset = true)
    }

    fun closePicker() {
        availableJob?.cancel()
        _available.value = AvailableFilesUi()
    }

    fun setPickerQuery(query: String) {
        if (!_available.value.isOpen) return
        _available.update { it.copy(query = query) }
        loadAvailable(reset = true, debounce = true)
    }

    fun loadMoreAvailable() {
        val state = _available.value
        if (!state.isOpen || state.isLoading || state.nextCursor == null) return
        loadAvailable(reset = false)
    }

    fun retryAvailable() = loadAvailable(reset = _available.value.files.isEmpty())

    private fun loadAvailable(reset: Boolean, debounce: Boolean = false) {
        availableJob?.cancel()
        _available.update { it.copy(isLoading = true, loadFailed = false) }
        availableJob = scope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            var cursor = if (reset) null else _available.value.nextCursor
            val gathered = if (reset) mutableListOf() else _available.value.files.toMutableList()
            var pages = 0
            // A page can come back empty with a cursor (everything on it was already attached);
            // follow it rather than showing an empty list over more files.
            while (true) {
                val query = _available.value.query.trim().ifEmpty { null }
                when (
                    val result = projectRepository.listAvailableProjectFiles(
                        projectId,
                        cursor = cursor,
                        search = query,
                        limit = AVAILABLE_PAGE_SIZE,
                    )
                ) {
                    is Result.Success -> {
                        gathered += result.data.files.filterNot { file -> gathered.any { it.fileId == file.fileId } }
                        cursor = result.data.nextCursor
                        pages++
                        if (result.data.files.isNotEmpty() || cursor == null || pages >= MAX_EMPTY_PAGES) {
                            _available.update {
                                it.copy(files = gathered.toList(), nextCursor = cursor, isLoading = false)
                            }
                            return@launch
                        }
                    }
                    is Result.Error -> {
                        if (result.toProjectContextFailure() == ProjectContextFailure.NotFound) onProjectGone()
                        Logger.w(result.exception) { "Failed to load available project files" }
                        _available.update { it.copy(isLoading = false, loadFailed = true) }
                        return@launch
                    }
                    is Result.Loading -> return@launch
                }
            }
        }
    }

    /** Adds one of the user's own files, then closes the sheet. */
    fun addExisting(fileId: String) {
        if (_available.value.addingFileId != null) return
        _available.update { it.copy(addingFileId = fileId) }
        scope.launch {
            when (val result = projectRepository.addProjectFile(projectId, fileId)) {
                is Result.Success -> {
                    closePicker()
                    onAttached(result.data)
                }
                is Result.Error -> {
                    _available.update { it.copy(addingFileId = null) }
                    when (result.toProjectFileAddFailure()) {
                        ProjectContextFailure.NotFound -> onProjectGone()
                        ProjectContextFailure.FileLimit -> _messages.tryEmit(ProjectFilesMessage.AtLimit(maxFiles()))
                        else -> _messages.tryEmit(ProjectFilesMessage.Error(Res.string.project_file_attach_failed))
                    }
                }
                is Result.Loading -> Unit
            }
        }
    }

    private companion object {
        const val AGENTS_ENDPOINT = "agents"
        const val FILE_SEARCH_RESOURCE = "file_search"
        const val AVAILABLE_PAGE_SIZE = 20
        const val MAX_EMPTY_PAGES = 5
        const val SEARCH_DEBOUNCE_MS = 300L
        const val MESSAGE_BUFFER = 8
    }
}
