package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.FileRepository
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.FileObject
import com.garfiec.librechat.core.model.request.DeleteFileEntry
import com.garfiec.librechat.core.model.response.DeleteFilesResponse
import com.garfiec.librechat.core.model.response.FilePreviewResponse
import com.garfiec.librechat.core.model.response.FileUploadConfig
import com.garfiec.librechat.core.model.response.UploadRoute
import com.garfiec.librechat.core.model.response.toolResource
import com.garfiec.librechat.feature.chat.components.AttachedFile
import com.garfiec.librechat.feature.chat.util.IosFileData
import com.garfiec.librechat.feature.chat.util.IosImageData
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ErrorOnlyHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** iOS keeps a pick's data so a failed upload retries in place, as Android's does. */
class IosFileHandlerRetryTest {

    private class Upload(val bytes: ByteArray, val filename: String, val toolResource: String?)

    /** Fails the first upload of each file name, then succeeds. */
    private class FlakyFileRepository : FileRepository {
        val uploads = mutableListOf<Upload>()
        private val failed = mutableSetOf<String>()

        override suspend fun uploadFile(
            bytes: ByteArray,
            filename: String,
            type: String,
            fileId: String?,
            endpoint: String?,
            model: String?,
            agentId: String?,
            toolResource: String?,
            messageFile: Boolean?,
            width: Int?,
            height: Int?,
            onProgress: ((Float) -> Unit)?,
            pdfPassword: String?,
        ): Result<FileObject> {
            uploads += Upload(bytes, filename, toolResource)
            if (failed.add(filename)) return Result.Error(message = "offline")
            return Result.Success(
                FileObject(fileId = "server-$filename", filename = filename, filepath = "/f/$filename", type = type, bytes = 1),
            )
        }

        override suspend fun uploadFile(
            bytes: ByteArray,
            filename: String,
            type: String,
            onProgress: ((Float) -> Unit)?,
        ): Result<FileObject> = error("unused")

        override suspend fun getFiles(): Result<List<FileObject>> = error("unused")
        override suspend fun getFileConfig(): Result<FileUploadConfig> = error("unused")
        override suspend fun deleteFiles(
            files: List<DeleteFileEntry>,
            agentId: String?,
            toolResource: String?,
        ): Result<DeleteFilesResponse> = error("unused")
        override suspend fun downloadFile(userId: String, fileId: String): Result<ByteArray> = error("unused")
        override suspend fun downloadAttachment(attachment: Attachment, userId: String?): Result<ByteArray> =
            error("unused")
        override suspend fun getAgentFiles(agentId: String): Result<List<FileObject>> = error("unused")
        override suspend fun pollFilePreview(fileId: String): Result<FilePreviewResponse> = error("unused")
        override suspend fun markFilesUsed(fileIds: List<String>): Result<Unit> = error("unused")
        override fun supportsUsageHold(): Boolean = false
        override fun clear() = Unit
    }

    private val repository = FlakyFileRepository()
    private val handler = IosFileHandler(
        handle = ErrorOnlyHandle(ChatStateHandle(MutableStateFlow(ChatUiState()), CoroutineScope(Dispatchers.Unconfined))),
        fileRepository = repository,
        ioDispatcher = Dispatchers.Unconfined,
    )

    private val photo = IosImageData(bytes = byteArrayOf(1, 2, 3), filename = "photo.png", mimeType = "image/png")
    private val notes = IosFileData(bytes = byteArrayOf(4, 5), filename = "notes.pdf", mimeType = "application/pdf")

    private fun pick(vararg refs: Any, route: UploadRoute = UploadRoute.TEXT) =
        handler.onFilesSelected(handler.describe(refs.toList()).map { RoutedFile(it, route) })

    @Test
    fun retryReUploadsThePickedBytesInPlaceWithItsRoute() {
        pick(notes, photo)
        val failed = handler.attachedFiles.value.first()
        assertTrue(failed.uploadFailed)

        handler.retryUpload(failed)

        val retried = repository.uploads.last()
        assertEquals("notes.pdf", retried.filename)
        assertContentEquals(notes.bytes, retried.bytes)
        assertEquals(UploadRoute.TEXT.toolResource(), retried.toolResource)
        val tray = handler.attachedFiles.value
        assertEquals(listOf("notes.pdf", "photo.png"), tray.map { it.name })
        assertEquals("server-notes.pdf", tray.first().fileId)
        assertFalse(tray.first().uploadFailed)
        assertEquals(failed.uri, tray.first().uri)
    }

    @Test
    fun aFailedUploadKeepsItsPickAndAnUploadedOneLetsItGo() {
        pick(notes)
        val entry = handler.attachedFiles.value.single()
        assertTrue(entry.uploadFailed)
        assertTrue(handler.holdsPickedData(entry.uri))

        handler.retryUpload(entry)

        // Uploaded: it never uploads again (Undo restores it as uploaded), so the bytes go.
        assertNotNull(handler.attachedFiles.value.single().fileId)
        assertFalse(handler.holdsPickedData(entry.uri))
    }

    @Test
    fun anImageDrawsItsThumbnailFromThePickersPreview() {
        val preview = byteArrayOf(9, 9)
        val shot = IosImageData(
            bytes = byteArrayOf(1, 2, 3),
            filename = "shot.png",
            mimeType = "image/png",
            preview = preview,
        )
        pick(shot)
        assertSame(preview, handler.attachedFiles.value.single().localPreview)
    }

    @Test
    fun aHeicPickNeverHandsItsOwnBytesToTheThumbnail() {
        // Skia's decoder can't read HEIC: with no preview the thumbnail waits for the server's copy.
        val heic = IosImageData(bytes = byteArrayOf(0, 0, 0, 24), filename = "IMG_0001.HEIC", mimeType = "image/heic")
        pick(heic)
        assertNull(handler.attachedFiles.value.single().localPreview)
    }

    @Test
    fun aRemovedFailedFileUndoneStillRetries() {
        pick(notes)
        val failed = handler.attachedFiles.value.single()
        handler.removeFile(failed)
        handler.insertAt(0, failed)

        handler.retryUpload(failed)

        assertEquals(2, repository.uploads.size)
        assertNotNull(handler.attachedFiles.value.single().fileId)
    }

    @Test
    fun aRemovedFailedFileLetsItsPickGoOnceItsUndoIsGone() {
        pick(notes)
        val failed = handler.attachedFiles.value.single()
        handler.removeFile(failed)
        // Kept while the Undo can still bring it back to be retried.
        assertTrue(handler.holdsPickedData(failed.uri))

        handler.forgetRemoved(failed)

        assertFalse(handler.holdsPickedData(failed.uri))
    }

    @Test
    fun forgettingAFileStillInTheTrayKeepsItsPick() {
        pick(notes)
        val failed = handler.attachedFiles.value.single()

        handler.forgetRemoved(failed)

        assertTrue(handler.holdsPickedData(failed.uri))
    }

    @Test
    fun aQueuedEditSwapKeepsBothDraftsPicksAndDropsARemovedOnes() {
        pick(notes, photo)
        val (failedNotes, failedPhoto) = handler.attachedFiles.value
        // Removed with its Undo pending, which the swap drops.
        handler.removeFile(failedPhoto)
        val draft = handler.attachedFiles.value

        handler.restoreAttachedFiles(emptyList())
        assertTrue(handler.holdsPickedData(failedNotes.uri))
        assertFalse(handler.holdsPickedData(failedPhoto.uri))

        // The edit ends and the stashed draft comes back, still retryable.
        handler.restoreAttachedFiles(draft)
        handler.retryUpload(failedNotes)
        assertNotNull(handler.attachedFiles.value.single().fileId)
    }

    @Test
    fun aFileWithNoPickedDataIsRemovedOnRetry() {
        val orphan = AttachedFile(
            uri = "from-another-device",
            name = "x.pdf",
            uploadFailed = true,
        )
        handler.restoreAttachedFiles(listOf(orphan))

        handler.retryUpload(orphan)

        assertTrue(handler.attachedFiles.value.isEmpty())
        assertTrue(repository.uploads.isEmpty())
    }
}
