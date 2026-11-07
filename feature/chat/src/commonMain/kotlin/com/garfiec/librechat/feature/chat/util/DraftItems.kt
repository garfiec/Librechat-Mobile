package com.garfiec.librechat.feature.chat.util

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.FileReference
import com.garfiec.librechat.core.model.media.resolveFileReferenceUrl
import com.garfiec.librechat.core.ui.media.MediaItem
import com.garfiec.librechat.core.ui.media.MediaPreviewState
import com.garfiec.librechat.feature.chat.components.AttachedFile

@Immutable
sealed interface DraftItem {
    /** Unique within one [DraftItems], stable while the item stays in it. */
    val key: String

    sealed interface Attachment : DraftItem {
        val file: AttachedFile
        val isUploading: Boolean get() = file.isUploading
        val isFailed: Boolean get() = file.uploadFailed
    }

    /** [thumbnail] is null when nothing can be drawn; [viewerSource] is the full copy the media viewer opens. */
    data class Image(
        override val file: AttachedFile,
        val thumbnail: Any?,
        override val key: String,
        val viewerSource: Any? = thumbnail,
    ) : Attachment {
        /** Its key in the media viewer, which its thumbnails register under so the viewer flies back to them. */
        val mediaKey: String get() = "draft:$key"
    }

    data class File(override val file: AttachedFile, override val key: String) : Attachment

    data class Skill(val name: String) : DraftItem {
        override val key: String get() = "skill:$name"
    }

    /**
     * Keyed by its words and [occurrence], never by [index]: removing or restoring a quote shifts the
     * others' indexes, and a key that moved with them would hand one quote's row to another.
     */
    data class Quote(val index: Int, val text: String, val occurrence: Int = 0) : DraftItem {
        override val key: String get() = "quote:$occurrence:$text"
    }
}

/** Holds no upload progress ([DraftUploadProgress]), so a progress tick leaves it equal. */
@Immutable
data class DraftItems(
    val items: List<DraftItem>,
    /** Files among [items]; what `composerCanSend` counts as an attachment. */
    val hasFiles: Boolean,
    /** Files that failed to upload: each holds every send until it is retried or removed. */
    val failedCount: Int,
    val hasUploads: Boolean,
) {
    val isEmpty: Boolean get() = items.isEmpty()
    val hasFailedUploads: Boolean get() = failedCount > 0

    companion object {
        val EMPTY = DraftItems(emptyList(), hasFiles = false, failedCount = 0, hasUploads = false)
    }
}

/** Read only where the rings draw, so a progress tick recomposes nothing above them. */
@Stable
class DraftUploadProgress(private val files: () -> List<AttachedFile>) {
    /** The mean progress of the uploads that report one, or null when none does (indeterminate). */
    fun overall(): Float? = meanUploadProgress(files())

    fun of(file: AttachedFile): Float? = files().firstOrNull { it.key == file.key }?.uploadProgress

    companion object {
        val NONE = DraftUploadProgress { emptyList() }
    }
}

internal fun meanUploadProgress(files: List<AttachedFile>): Float? {
    val progresses = files.filter { it.isUploading }.mapNotNull { it.uploadProgress }
    return progresses.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size }
}

/** Failed files first (they hold the send), then images, other files, skills and quotes. */
fun draftItemsOf(
    files: List<AttachedFile>,
    skills: List<String>,
    quotes: List<String>,
    serverUrl: String,
): DraftItems {
    if (files.isEmpty() && skills.isEmpty() && quotes.isEmpty()) return DraftItems.EMPTY
    val seenKeys = mutableMapOf<String, Int>()
    val attachments = files.map { file ->
        // A handler tells two picks of one file apart ([AttachedFile.key]); an entry it didn't mint
        // (a restored queued turn) can still repeat one, so a repeat gets its own key too.
        val base = "file:${file.key}"
        val repeat = seenKeys[base] ?: 0
        seenKeys[base] = repeat + 1
        val key = if (repeat == 0) base else "$base#$repeat"
        if (file.isImage) {
            val thumbnail = thumbnailFor(file, serverUrl)
            DraftItem.Image(file, thumbnail, key, viewerSourceFor(file, serverUrl) ?: thumbnail)
        } else {
            DraftItem.File(file, key)
        }
    }
    val (failed, settled) = attachments.partition { it.isFailed }
    val items = buildList {
        addAll(failed)
        addAll(settled.filterIsInstance<DraftItem.Image>())
        addAll(settled.filterIsInstance<DraftItem.File>())
        skills.distinct().forEach { add(DraftItem.Skill(it)) }
        val quoteRepeats = mutableMapOf<String, Int>()
        quotes.forEachIndexed { index, text ->
            val occurrence = quoteRepeats[text] ?: 0
            quoteRepeats[text] = occurrence + 1
            add(DraftItem.Quote(index, text, occurrence))
        }
    }
    return DraftItems(
        items = items,
        hasFiles = files.isNotEmpty(),
        failedCount = failed.size,
        hasUploads = files.any { it.isUploading },
    )
}

private fun thumbnailFor(file: AttachedFile, serverUrl: String): Any? {
    file.localPreview?.let { return it }
    // The device copy wins so the thumbnail doesn't reload as the upload lands. A String uri is an
    // id (iOS mints one per pick), never loaded; any other uri is a handle the loader reads.
    if (file.uri !is String) return file.uri
    return serverCopyOf(file, serverUrl)
}

// iOS keeps only a small preview on the item, so its full image is the server's copy.
private fun viewerSourceFor(file: AttachedFile, serverUrl: String): Any? =
    if (file.uri !is String) file.uri else serverCopyOf(file, serverUrl)

private fun serverCopyOf(file: AttachedFile, serverUrl: String): String? {
    if (file.filepath == null && file.fileId == null) return null
    return resolveFileReferenceUrl(FileReference(fileId = file.fileId, filepath = file.filepath, type = file.type), serverUrl)
}

/** What a tap on an item of the attachment strip does ([taps]); null leaves the item untappable. */
sealed interface DraftItemTap {
    data class Retry(val file: AttachedFile) : DraftItemTap

    data class ViewImage(val preview: MediaPreviewState) : DraftItemTap

    data class OpenPdf(val fileId: String, val filename: String) : DraftItemTap

    data class OpenFile(val attachment: Attachment) : DraftItemTap
}

/** A non-image file is tappable only once uploaded: both file viewers read it from the server. */
fun DraftItems.taps(): Map<String, DraftItemTap?> {
    val images = items.filterIsInstance<DraftItem.Image>().filter { !it.isFailed && it.viewerSource != null }
    val media by lazy(LazyThreadSafetyMode.NONE) {
        images.map {
            MediaItem(url = it.mediaKey, contentDescription = it.file.name, filename = it.file.name, model = it.viewerSource)
        }
    }
    return items.associate { item -> item.key to tapOf(item, images) { media } }
}

private fun tapOf(item: DraftItem, images: List<DraftItem.Image>, media: () -> List<MediaItem>): DraftItemTap? {
    if (item !is DraftItem.Attachment) return null
    if (item.isFailed) return DraftItemTap.Retry(item.file)
    if (item is DraftItem.Image) {
        val index = images.indexOf(item).takeIf { it >= 0 } ?: return null
        return DraftItemTap.ViewImage(MediaPreviewState(items = media(), initialIndex = index))
    }
    val file = item.file
    val fileId = file.fileId ?: return null
    val attachment = Attachment(fileId = fileId, filename = file.name, filepath = file.filepath, type = file.type)
    return when (conversationFileTap(attachment, canOpenArtifact = false)) {
        is ConversationFileTap.Pdf -> DraftItemTap.OpenPdf(fileId, file.name)
        else -> DraftItemTap.OpenFile(attachment)
    }
}
