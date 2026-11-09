package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.feature.chat.components.AttachedFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * An in-memory [PlatformFileHandler]: the tray is [files], uploads are whatever a test writes there.
 * A pick or a retry puts its entry in the tray as uploading, as both real handlers do synchronously.
 * Records what the attachment list asked of it ([removed], [inserted], [retried]).
 */
internal class FakeFileHandler(initial: List<AttachedFile> = emptyList()) : PlatformFileHandler {
    val files = MutableStateFlow(initial)
    override val attachedFiles: StateFlow<List<AttachedFile>> get() = files
    override var pendingUploadSendJob: Job? = null
    override val pdfPasswordPrompts: StateFlow<List<PdfPasswordPrompt>> = MutableStateFlow(emptyList())

    val removed = mutableListOf<AttachedFile>()
    val inserted = mutableListOf<Pair<Int, AttachedFile>>()
    val retried = mutableListOf<AttachedFile>()

    override fun describe(platformRefs: List<Any>): List<PickedFile> = emptyList()
    override fun onFilesSelected(files: List<RoutedFile>) {
        this.files.value += files.map { AttachedFile(uri = it.file.ref, name = it.file.name, type = it.file.mimeType, route = it.route) }
    }

    override fun removeFile(file: AttachedFile) {
        removed += file
        files.value = files.value.filter { it.key != file.key }
    }

    override fun retryUpload(file: AttachedFile) {
        retried += file
        files.value = files.value.updateEntry(file.key) { it.copy(uploadFailed = false, uploadProgress = null, fileId = null) }
    }

    override fun insertAt(index: Int, file: AttachedFile) {
        inserted += index to file
        files.value = files.value.insertedAt(index, file)
    }

    override suspend fun waitForUploadsAndSend(text: String, doSend: (String) -> Unit) =
        files.awaitUploadsThenSend(text, setError = {}, doSend = doSend)

    override fun hasPendingUploads(): Boolean = files.value.any { it.isUploading }
    override fun clearAttachedFiles() {
        files.value = emptyList()
    }

    override fun restoreAttachedFiles(files: List<AttachedFile>) {
        this.files.value = files
    }

    override fun addPreUploadedFiles(files: List<AttachedFile>) {
        this.files.value = this.files.value.appendDedupedByFileId(files)
    }

    override fun submitPdfPassword(prompt: PdfPasswordPrompt, password: String) = Unit
    override fun dismissPdfPassword() = Unit
}
