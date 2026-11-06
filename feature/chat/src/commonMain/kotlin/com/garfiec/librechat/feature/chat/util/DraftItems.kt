package com.garfiec.librechat.feature.chat.util

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.FileReference
import com.garfiec.librechat.core.model.media.resolveFileReferenceUrl
import com.garfiec.librechat.core.ui.media.MediaItem
import com.garfiec.librechat.core.ui.media.MediaPreviewState
import com.garfiec.librechat.feature.chat.components.AttachedFile

/**
 * One thing that goes with the message being composed, as the composer's attachment strip draws
 * it: a file (an image or anything else), a picked skill, or a staged quote.
 */
@Immutable
sealed interface DraftItem {
    /** Unique within one [DraftItems], stable while the item stays in it. */
    val key: String

    /** An attached file, [Image] or [File]. */
    sealed interface Attachment : DraftItem {
        val file: AttachedFile
        val isUploading: Boolean get() = file.isUploading
        val isFailed: Boolean get() = file.uploadFailed
    }

    /**
     * An image, with what to draw its thumbnail from ([thumbnail], null when nothing can be drawn) and
     * what the media viewer draws it from ([viewerSource]: the device's full copy where there is one,
     * else the server's, else the thumbnail).
     */
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
     * A staged quote, at [index] in `ComposerState.pendingQuotes`. Keyed by its words, and by
     * [occurrence] among quotes with the same words (quotes may repeat), not by [index]: removing or
     * restoring one quote moves the others' indexes, and a key that moved with them would hand one
     * quote's row to another and announce the wrong one as added.
     */
    data class Quote(val index: Int, val text: String, val occurrence: Int = 0) : DraftItem {
        override val key: String get() = "quote:$occurrence:$text"
    }
}

/**
 * The composer's draft items, in list order, with what the strip and the send hold read. No upload
 * progress: that is [DraftUploadProgress], read where the rings draw, so a progress tick changes
 * nothing here.
 */
@Immutable
data class DraftItems(
    val items: List<DraftItem>,
    /** Files among [items]; what `composerCanSend` counts as an attachment. */
    val hasFiles: Boolean,
    /** Files that failed to upload: each holds every send until it is retried or removed. */
    val failedCount: Int,
    /** A file is still uploading. */
    val hasUploads: Boolean,
) {
    val isEmpty: Boolean get() = items.isEmpty()
    val hasFailedUploads: Boolean get() = failedCount > 0

    companion object {
        val EMPTY = DraftItems(emptyList(), hasFiles = false, failedCount = 0, hasUploads = false)
    }
}

/**
 * The draft's upload progress, kept out of [DraftItems] so a progress tick re-maps nothing above the
 * rings: they read it as they draw. [files] is the tray as the screen holds it, with its progress.
 */
@Stable
class DraftUploadProgress(private val files: () -> List<AttachedFile>) {
    /** The mean progress of the uploads that report one, or null when none does (indeterminate). */
    fun overall(): Float? = meanUploadProgress(files())

    /** [file]'s own progress (its tray entry's, [AttachedFile.key]), or null when it reports none. */
    fun of(file: AttachedFile): Float? = files().firstOrNull { it.key == file.key }?.uploadProgress

    companion object {
        val NONE = DraftUploadProgress { emptyList() }
    }
}

internal fun meanUploadProgress(files: List<AttachedFile>): Float? {
    val progresses = files.filter { it.isUploading }.mapNotNull { it.uploadProgress }
    return progresses.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size }
}

/**
 * Maps the composer's files, picked skills and staged quotes to the list the attachment strip
 * draws: failed files first (they hold the send), then images, other files, skills and quotes,
 * each group in the order it was added. Pure, so `ChatScreen` maps it once per change of its inputs.
 *
 * An image's thumbnail is what the device holds when it holds something drawable (an Android
 * content `Uri`, the picker's small copy on iOS in [AttachedFile.localPreview]), else the server's
 * copy once there is one ([serverUrl] with the `filepath`, or the file id of a server file attached
 * by reference), else nothing: a String `uri` is an id (iOS mints one per pick), not something to
 * load. The device copy wins over the server's so the thumbnail doesn't reload as the upload lands.
 */
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
    // A non-String uri is a platform handle an image loader reads (an Android content Uri).
    if (file.uri !is String) return file.uri
    return serverCopyOf(file, serverUrl)
}

/**
 * The full image for the viewer: an Android content `Uri` is the picked file itself; iOS keeps only a
 * small preview on the item ([AttachedFile.localPreview]), so the server's copy is the full one once
 * the upload lands. Null leaves the thumbnail.
 */
private fun viewerSourceFor(file: AttachedFile, serverUrl: String): Any? =
    if (file.uri !is String) file.uri else serverCopyOf(file, serverUrl)

private fun serverCopyOf(file: AttachedFile, serverUrl: String): String? {
    if (file.filepath == null && file.fileId == null) return null
    return resolveFileReferenceUrl(FileReference(fileId = file.fileId, filepath = file.filepath, type = file.type), serverUrl)
}

/** What a tap on an item of the attachment strip does; null leaves the item untappable. */
sealed interface DraftItemTap {
    /** A failed upload: try it again. */
    data class Retry(val file: AttachedFile) : DraftItemTap

    /** An image: the media viewer, paging through the draft's images. */
    data class ViewImage(val preview: MediaPreviewState) : DraftItemTap

    /** An uploaded PDF: the native PDF preview. */
    data class OpenPdf(val fileId: String, val filename: String) : DraftItemTap

    /** Any other uploaded file: the file actions (open in another app, save, share). */
    data class OpenFile(val attachment: Attachment) : DraftItemTap
}

/**
 * The tap on [item]'s row. A failed file retries. An image opens in the media viewer, from the device's
 * copy while it uploads. Another file opens once it is uploaded (both viewers read it from the
 * server), by the rule the media page's Files tab uses ([conversationFileTap]); until then, and for a
 * skill or a quote, the row isn't tappable.
 */
fun DraftItems.tapFor(item: DraftItem): DraftItemTap? {
    if (item !is DraftItem.Attachment) return null
    if (item.isFailed) return DraftItemTap.Retry(item.file)
    if (item is DraftItem.Image) {
        val images = items.filterIsInstance<DraftItem.Image>().filter { !it.isFailed && it.viewerSource != null }
        val index = images.indexOf(item).takeIf { it >= 0 } ?: return null
        val media = images.map {
            MediaItem(url = it.mediaKey, contentDescription = it.file.name, filename = it.file.name, model = it.viewerSource)
        }
        return DraftItemTap.ViewImage(MediaPreviewState(items = media, initialIndex = index))
    }
    val file = item.file
    val fileId = file.fileId ?: return null
    val attachment = Attachment(fileId = fileId, filename = file.name, filepath = file.filepath, type = file.type)
    return when (conversationFileTap(attachment, canOpenArtifact = false)) {
        is ConversationFileTap.Pdf -> DraftItemTap.OpenPdf(fileId, file.name)
        else -> DraftItemTap.OpenFile(attachment)
    }
}
