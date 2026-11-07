package com.garfiec.librechat.feature.chat.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.pdf.PdfPasswordProtectedException
import com.garfiec.librechat.core.data.repository.FileRepository
import com.garfiec.librechat.core.model.FileObject
import com.garfiec.librechat.core.model.response.UploadRoute
import com.garfiec.librechat.core.model.response.toolResource
import com.garfiec.librechat.feature.chat.components.AttachedFile
import com.garfiec.librechat.feature.chat.util.IosFileData
import com.garfiec.librechat.feature.chat.util.IosImageData
import com.garfiec.librechat.feature.chat.util.IosPickedData
import com.garfiec.librechat.feature.chat.viewmodel.ChatNotice
import com.garfiec.librechat.feature.chat.viewmodel.ErrorOnlyHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * iOS [PlatformFileHandler]. Unlike an Android `Uri`, an iOS pick can't be read again once handed
 * over, so each tray entry's picked data ([picked]) is kept while the entry may still upload from it
 * ([prune]).
 */
class IosFileHandler(
    private val handle: ErrorOnlyHandle,
    private val fileRepository: FileRepository,
    private val ioDispatcher: CoroutineDispatcher,
) : PlatformFileHandler {
    private val _attachedFiles = MutableStateFlow<List<AttachedFile>>(emptyList())
    override val attachedFiles: StateFlow<List<AttachedFile>> = _attachedFiles.asStateFlow()
    override var pendingUploadSendJob: Job? = null

    private val pdfPrompts = PdfPasswordPrompts()
    override val pdfPasswordPrompts: StateFlow<List<PdfPasswordPrompt>> = pdfPrompts.pending

    /**
     * The picked data behind each tray entry, by its [AttachedFile.key]; what stays is [prune]'s rule.
     * Main only: every entry point and an upload's outcome run there (only the request is on [ioDispatcher]).
     */
    private val picked = mutableMapOf<Any, IosPickedData>()

    /** A removed failed entry whose Undo may still bring it back to be retried; null when none. */
    private var undoable: Any? = null

    /** The entries of the tray a queued-edit swap put aside ([restoreAttachedFiles]), until the next swap. */
    private var putAside: Set<Any> = emptySet()

    /** Whether the entry [key] still holds its picked data in memory ([picked]); read by the tests. */
    internal fun holdsPickedData(key: Any): Boolean = key in picked

    /** Each entry's upload, by key, so removing a file mid-upload cancels it. Same thread rule as [picked]. */
    private val uploadJobs = mutableMapOf<Any, Job>()

    override fun describe(platformRefs: List<Any>): List<PickedFile> = platformRefs.mapNotNull { ref ->
        (ref as? IosPickedData)?.let { PickedFile(ref = it, name = it.filename, mimeType = it.mimeType) }
    }

    @OptIn(ExperimentalUuidApi::class)
    override fun onFilesSelected(files: List<RoutedFile>) {
        files.forEach { file ->
            val ref = file.file.ref as? IosPickedData ?: return@forEach
            upload(key = Uuid.random().toString(), data = ref, route = file.route)
        }
    }

    /**
     * Uploads [data] as the tray entry [key], appended or over its own place when [inPlace]. An image
     * ignores [route]: the server's attachment pass drops a text-sourced image record.
     */
    @Suppress("TooGenericExceptionCaught") // upload boundary: any failure marks the attachment failed
    @OptIn(ExperimentalUuidApi::class)
    private fun upload(
        key: Any,
        data: IosPickedData,
        route: UploadRoute,
        pdfPassword: String? = null,
        inPlace: Boolean = false,
    ) {
        val image = data as? IosImageData
        val file = data as? IosFileData
        val filename = data.filename
        val mimeType = data.mimeType
        val bytes = data.bytes
        val effectiveRoute = if (image != null) UploadRoute.PROVIDER else route
        picked[key] = data

        val pendingFile = AttachedFile(
            uri = key,
            name = filename,
            isImage = image != null,
            uploadProgress = null,
            type = mimeType,
            route = effectiveRoute,
            // The picker's small copy, never the picked bytes (often HEIC); null waits for the server's.
            localPreview = image?.preview,
        )
        _attachedFiles.update { list -> if (inPlace) list.updateEntry(key) { pendingFile } else list + pendingFile }

        // On the handle's (Main) scope, with only the upload itself on IO: the outcome touches [picked].
        uploadJobs[key] = handle.scope.launch {
            try {
                val state = handle.state
                val isAgent = state.selectedEndpoint == EndpointConstants.AGENTS
                val fileId = Uuid.random().toString()

                val result = withContext(ioDispatcher) {
                    fileRepository.uploadFile(
                        bytes = bytes,
                        filename = filename,
                        type = mimeType,
                        fileId = fileId,
                        endpoint = state.selectedEndpoint,
                        model = if (!isAgent) state.selectedModel else null,
                        agentId = if (isAgent) state.selectedModel else null,
                        toolResource = if (image != null) null else effectiveRoute.toolResource(),
                        messageFile = true,
                        width = image?.width,
                        height = image?.height,
                        onProgress = { pct -> updateTrayEntry(key) { it.copy(uploadProgress = pct) } },
                        pdfPassword = pdfPassword,
                    )
                }

                when (result) {
                    is Result.Success -> onUploaded(key, result.data, keepsDimensions = image == null)
                    is Result.Error -> onUploadError(key, file, filename, result)
                    is Result.Loading -> { /* unexpected */ }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e) { "IosFileHandler: unexpected exception for $filename" }
                updateTrayEntry(key) { it.copy(uploadFailed = true, uploadProgress = null) }
                handle.setError(ChatNotice.uploadFailed(filename, e.message))
            }
        }
    }

    private fun updateTrayEntry(key: Any, transform: (AttachedFile) -> AttachedFile) {
        _attachedFiles.update { list -> list.updateEntry(key, transform) }
    }

    /**
     * Keeps [picked] to what can still be uploaded from it: the entries in the tray not yet uploaded,
     * those in the tray a queued-edit swap put aside ([putAside]), and a removed failed one whose
     * Undo is pending ([undoable]). An uploaded entry never uploads again (Undo brings it back
     * already uploaded), and a pick can be tens of MB. Run after every change to any of those.
     */
    private fun prune() {
        val keep = _attachedFiles.value.filter { it.fileId == null }.mapTo(HashSet()) { it.key }
        keep += putAside
        undoable?.let { keep += it }
        picked.keys.retainAll(keep)
    }

    private fun onUploaded(key: Any, fileObject: FileObject, keepsDimensions: Boolean) {
        Logger.d { "IosFileHandler: upload success -- fileId=${fileObject.fileId}" }
        updateTrayEntry(key) { f ->
            f.copy(
                uploadProgress = 1f,
                fileId = fileObject.fileId,
                filepath = fileObject.filepath,
                type = fileObject.type,
                width = if (keepsDimensions) f.width else fileObject.width,
                height = if (keepsDimensions) f.height else fileObject.height,
            )
        }
        prune()
    }

    private fun onUploadError(key: Any, file: IosFileData?, filename: String, result: Result.Error) {
        val locked = result.exception as? PdfPasswordProtectedException
        if (locked != null && file != null) {
            // Not an error to show: the chip waits while the UI asks for the password.
            // It stays pending, not failed, so a send parked on the upload gate
            // keeps waiting for the answer instead of going out without the PDF.
            updateTrayEntry(key) { it.copy(uploadProgress = null) }
            val pickedFile = PickedFile(ref = file, name = file.filename, mimeType = file.mimeType)
            pdfPrompts.add(PdfPasswordPrompt(key, pickedFile, locked.incorrectPassword))
            return
        }
        updateTrayEntry(key) { it.copy(uploadFailed = true, uploadProgress = null) }
        Logger.e(result.exception) { "IosFileHandler: upload failed -- ${result.message}" }
        handle.setError(ChatNotice.uploadFailed(filename, result.message))
    }

    override fun removeFile(file: AttachedFile) {
        pdfPrompts.remove(file.key)
        uploadJobs.remove(file.key)?.cancel()
        // A failed entry can come back through Undo and still want its retry.
        if (file.uploadFailed) undoable = file.key
        _attachedFiles.update { list -> list.filter { it.key != file.key } }
        prune()
    }

    /**
     * Re-uploads a failed file in place from its picked data, on `ChatUiState.retryUploadRoute`. A file
     * with no picked data (restored from a queued turn another device made) can't be re-read: removed.
     */
    override fun retryUpload(file: AttachedFile) {
        val data = picked[file.key]
        if (data == null) {
            removeFile(file)
            return
        }
        pdfPrompts.remove(file.key)
        uploadJobs.remove(file.key)?.cancel()
        upload(
            key = file.key,
            data = data,
            route = handle.state.retryUploadRoute(file.route, file.type),
            inPlace = true,
        )
    }

    override fun insertAt(index: Int, file: AttachedFile) {
        // The Undo brought it back: it is the tray's again.
        if (undoable == file.key) undoable = null
        _attachedFiles.update { it.insertedAt(index, file) }
        prune()
    }

    override fun forgetRemoved(file: AttachedFile) {
        if (undoable == file.key) undoable = null
        prune()
    }

    override suspend fun waitForUploadsAndSend(text: String, doSend: (String) -> Unit) =
        _attachedFiles.awaitUploadsThenSend(text, setError = handle::setError, doSend = doSend)

    override fun hasPendingUploads(): Boolean =
        _attachedFiles.value.any { it.isUploading }

    override fun clearAttachedFiles() {
        pdfPrompts.clear()
        uploadJobs.clear()
        undoable = null
        putAside = emptySet()
        _attachedFiles.update { emptyList() }
        prune()
    }

    override fun restoreAttachedFiles(files: List<AttachedFile>) {
        // Keep both trays' data: the swap brings the stashed one back, failed entries still retryable.
        val incoming = files.mapTo(HashSet()) { it.key }
        putAside = _attachedFiles.value.map { it.key }.filterNotTo(HashSet()) { it in incoming }
        undoable = null
        pdfPrompts.clear()
        _attachedFiles.update { files }
        prune()
    }

    @OptIn(ExperimentalUuidApi::class)
    override fun submitPdfPassword(prompt: PdfPasswordPrompt, password: String) {
        if (!pdfPrompts.take(prompt)) return
        val fileData = prompt.file.ref as? IosFileData ?: return
        _attachedFiles.update { list -> list.filter { it.key != prompt.chipKey } }
        prune()
        // Routed against the live selection, as the Android handler's password retry is.
        upload(
            key = Uuid.random().toString(),
            data = fileData,
            route = handle.state.uploadRouteFor(fileData.mimeType),
            pdfPassword = password,
        )
    }

    override fun dismissPdfPassword() {
        val prompt = pdfPrompts.takeHead() ?: return
        _attachedFiles.update { list -> list.filter { it.key != prompt.chipKey } }
        prune()
    }

    override fun addPreUploadedFiles(files: List<AttachedFile>) {
        _attachedFiles.update { it.appendDedupedByFileId(files) }
    }
}
