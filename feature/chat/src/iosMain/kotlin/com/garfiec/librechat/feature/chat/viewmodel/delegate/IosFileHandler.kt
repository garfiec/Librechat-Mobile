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
 * iOS implementation of [PlatformFileHandler]: uploads what the pickers, the camera and a clipboard
 * paste hand over ([IosImageData], [IosFileData]).
 *
 * Unlike an Android `Uri`, an iOS pick can't be read again once it is handed over, so the handler
 * keeps each tray entry's picked data ([picked]) only while the entry can still need it: while its
 * upload is out or waits on a PDF password, and after it failed, since a retry uploads from it. A
 * successful upload drops it. An image's thumbnail is the small copy the picker made of it
 * ([IosImageData.preview] as [AttachedFile.localPreview]), since the picked bytes may be HEIC.
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
     * The picked [IosImageData] / [IosFileData] behind each tray entry, by its minted `uri`, as
     * [pdfPrompts] keeps the [PickedFile] a password retry needs. An entry's data goes once its
     * upload succeeds or it is removed; a removed failed entry keeps it, since Undo can bring it back
     * to be retried. The whole map goes when the tray is cleared. Touched on Main only: every entry
     * point runs there, and so does an upload's outcome (only the request runs on [ioDispatcher]).
     */
    private val picked = mutableMapOf<Any, Any>()

    /** Whether the entry [uri] still holds its picked data in memory ([picked]); read by the tests. */
    internal fun holdsPickedData(uri: Any): Boolean = uri in picked

    /** Each entry's upload, by `uri`, so removing a file mid-upload cancels it. Same thread rule as [picked]. */
    private val uploadJobs = mutableMapOf<Any, Job>()

    override fun describe(platformRefs: List<Any>): List<PickedFile> = platformRefs.mapNotNull { ref ->
        when (ref) {
            is IosImageData -> PickedFile(ref = ref, name = ref.filename, mimeType = ref.mimeType)
            is IosFileData -> PickedFile(ref = ref, name = ref.filename, mimeType = ref.mimeType)
            else -> null
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override fun onFilesSelected(files: List<RoutedFile>) {
        files.forEach { file ->
            val ref = file.file.ref
            if (ref is IosImageData || ref is IosFileData) {
                upload(uniqueId = Uuid.random().toString(), data = ref, route = file.route)
            }
        }
    }

    /**
     * Uploads [data] as the tray entry [uniqueId]: appended, or over the entry's own place when
     * [inPlace] (a retry), which does nothing once that entry is gone (removed, or the tray swapped
     * to another draft). Images have no text route: extraction would need OCR, and the server's
     * attachment pass drops a text-sourced record before it ever reaches its image branch. The
     * router never asks for one; an image ignores [route].
     */
    @Suppress("TooGenericExceptionCaught") // upload boundary: any failure marks the attachment failed
    @OptIn(ExperimentalUuidApi::class)
    private fun upload(
        uniqueId: String,
        data: Any,
        route: UploadRoute,
        pdfPassword: String? = null,
        inPlace: Boolean = false,
    ) {
        val image = data as? IosImageData
        val file = data as? IosFileData
        val filename = image?.filename ?: file?.filename ?: return
        val mimeType = image?.mimeType ?: file?.mimeType ?: return
        val bytes = image?.bytes ?: file?.bytes ?: return
        val effectiveRoute = if (image != null) UploadRoute.PROVIDER else route
        if (inPlace && _attachedFiles.value.none { it.uri == uniqueId }) return
        picked[uniqueId] = data

        val pendingFile = AttachedFile(
            uri = uniqueId,
            name = filename,
            isImage = image != null,
            uploadProgress = null,
            type = mimeType,
            route = effectiveRoute,
            // The picker's small JPEG/PNG, never the picked bytes: a library photo is often HEIC,
            // which the thumbnail's decoder can't read. Null leaves the thumbnail to the server's copy.
            localPreview = image?.preview,
        )
        _attachedFiles.update { list ->
            if (inPlace) {
                list.map { if (it.uri == uniqueId) pendingFile else it }
            } else {
                list + pendingFile
            }
        }

        // On the handle's (Main) scope, with only the upload itself on IO: the outcome touches [picked].
        uploadJobs[uniqueId] = handle.scope.launch {
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
                        onProgress = { pct -> updateEntry(uniqueId) { it.copy(uploadProgress = pct) } },
                        pdfPassword = pdfPassword,
                    )
                }

                when (result) {
                    is Result.Success -> onUploaded(uniqueId, result.data, keepsDimensions = image == null)
                    is Result.Error -> onUploadError(uniqueId, file, filename, result)
                    is Result.Loading -> { /* unexpected */ }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e) { "IosFileHandler: unexpected exception for $filename" }
                updateEntry(uniqueId) { it.copy(uploadFailed = true, uploadProgress = null) }
                handle.setError(ChatNotice.uploadFailed(filename, e.message))
            }
        }
    }

    private fun updateEntry(uniqueId: String, transform: (AttachedFile) -> AttachedFile) {
        _attachedFiles.update { list -> list.map { if (it.uri == uniqueId) transform(it) else it } }
    }

    /**
     * The server's record onto the entry. A file keeps no dimensions of its own; an image takes the
     * server's. The picked data goes: an uploaded entry never uploads again (Undo brings it back
     * already uploaded), and a pick can be tens of MB.
     */
    private fun onUploaded(uniqueId: String, fileObject: FileObject, keepsDimensions: Boolean) {
        Logger.d { "IosFileHandler: upload success -- fileId=${fileObject.fileId}" }
        picked.remove(uniqueId)
        updateEntry(uniqueId) { f ->
            f.copy(
                uploadProgress = 1f,
                fileId = fileObject.fileId,
                filepath = fileObject.filepath,
                type = fileObject.type,
                width = if (keepsDimensions) f.width else fileObject.width,
                height = if (keepsDimensions) f.height else fileObject.height,
            )
        }
    }

    private fun onUploadError(uniqueId: String, file: IosFileData?, filename: String, result: Result.Error) {
        val locked = (result.exception as? PdfPasswordProtectedException)?.takeIf { file != null }
        updateEntry(uniqueId) { it.copy(uploadFailed = locked == null, uploadProgress = null) }
        if (locked != null && file != null) {
            // Not an error to show: the chip waits while the UI asks for the password.
            // It stays pending, not failed, so a send parked on the upload gate
            // keeps waiting for the answer instead of going out without the PDF.
            val pickedFile = PickedFile(ref = file, name = file.filename, mimeType = file.mimeType)
            pdfPrompts.add(PdfPasswordPrompt(uniqueId, pickedFile, locked.incorrectPassword))
            return
        }
        Logger.e(result.exception) { "IosFileHandler: upload failed -- ${result.message}" }
        handle.setError(ChatNotice.uploadFailed(filename, result.message))
    }

    override fun removeFile(file: AttachedFile) {
        pdfPrompts.remove(file.uri)
        uploadJobs.remove(file.uri)?.cancel()
        // A failed entry can come back through Undo and still want its retry.
        if (!file.uploadFailed) picked.remove(file.uri)
        _attachedFiles.update { list -> list.filter { it.uri != file.uri } }
    }

    /**
     * Uploads a failed file again from the data it was picked with, in its place in the tray and
     * with the route it went with while that route still works (`ChatUiState.retryUploadRoute`),
     * as Android's retry does. A file this handler holds no data for
     * (one restored from a queued turn another device made) can't be re-read, so it is removed.
     * A file no longer in the tray is left alone: putting it back would undo the user's ×.
     */
    override fun retryUpload(file: AttachedFile) {
        if (_attachedFiles.value.none { it.uri == file.uri }) return
        val uniqueId = file.uri as? String
        val data = uniqueId?.let { picked[it] }
        if (uniqueId == null || data == null) {
            removeFile(file)
            return
        }
        pdfPrompts.remove(uniqueId)
        uploadJobs.remove(uniqueId)?.cancel()
        upload(
            uniqueId = uniqueId,
            data = data,
            route = handle.state.retryUploadRoute(file.route, file.type),
            inPlace = true,
        )
    }

    override fun insertAt(index: Int, file: AttachedFile) {
        _attachedFiles.update { it.insertedAt(index, file) }
    }

    override suspend fun waitForUploadsAndSend(text: String, doSend: (String) -> Unit) =
        _attachedFiles.awaitUploadsThenSend(text, setError = handle::setError, doSend = doSend)

    override fun hasPendingUploads(): Boolean =
        _attachedFiles.value.any { it.isUploading }

    override fun clearAttachedFiles() {
        pdfPrompts.clear()
        picked.clear()
        uploadJobs.clear()
        _attachedFiles.update { emptyList() }
    }

    override fun restoreAttachedFiles(files: List<AttachedFile>) {
        // [picked] stays: the queued-edit swap restores the stashed draft's own entries afterwards,
        // and a failed one among them is still retryable from it.
        pdfPrompts.clear()
        _attachedFiles.update { files }
    }

    @OptIn(ExperimentalUuidApi::class)
    override fun submitPdfPassword(prompt: PdfPasswordPrompt, password: String) {
        if (!pdfPrompts.take(prompt)) return
        val fileData = prompt.file.ref as? IosFileData ?: return
        _attachedFiles.update { list -> list.filter { it.uri != prompt.chipKey } }
        picked.remove(prompt.chipKey)
        // Routed against the live selection, as the Android handler's password retry is.
        upload(
            uniqueId = Uuid.random().toString(),
            data = fileData,
            route = handle.state.uploadRouteFor(fileData.mimeType),
            pdfPassword = password,
        )
    }

    override fun dismissPdfPassword() {
        val prompt = pdfPrompts.takeHead() ?: return
        picked.remove(prompt.chipKey)
        _attachedFiles.update { list -> list.filter { it.uri != prompt.chipKey } }
    }

    override fun addPreUploadedFiles(files: List<AttachedFile>) {
        _attachedFiles.update { it.appendDedupedByFileId(files) }
    }
}
