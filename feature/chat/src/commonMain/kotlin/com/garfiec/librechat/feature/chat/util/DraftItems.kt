package com.garfiec.librechat.feature.chat.util

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.FileReference
import com.garfiec.librechat.core.model.media.resolveFileReferenceUrl
import com.garfiec.librechat.feature.chat.components.AttachedFile

/**
 * One thing that goes with the message being composed, as the composer's attachment stack and
 * list draw it: a file (an image or anything else), a picked skill, or a staged quote.
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

    /** An image, with what to draw its thumbnail from ([thumbnail], null when nothing can be drawn). */
    data class Image(override val file: AttachedFile, val thumbnail: Any?, override val key: String) : Attachment

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

/** The composer's draft items, in list order, with the counts the stack and the send hold read. */
@Immutable
data class DraftItems(
    val items: List<DraftItem>,
    /** Files among [items]; what `composerCanSend` counts as an attachment. */
    val fileCount: Int,
    /** Files that failed to upload: each holds every send until it is retried or removed. */
    val failedCount: Int,
    /** Files still uploading. */
    val uploadingCount: Int,
    /** Mean progress of the uploads that report one, or null when none does (indeterminate). */
    val uploadProgress: Float?,
) {
    val isEmpty: Boolean get() = items.isEmpty()
    val hasFiles: Boolean get() = fileCount > 0
    val hasFailedUploads: Boolean get() = failedCount > 0

    companion object {
        val EMPTY = DraftItems(emptyList(), fileCount = 0, failedCount = 0, uploadingCount = 0, uploadProgress = null)
    }
}

/**
 * Maps the composer's files, picked skills and staged quotes to the list the attachment stack and
 * panel draw: failed files first (they hold the send), then images, other files, skills and quotes,
 * each group in the order it was added. Pure, so `ChatScreen` maps it once per change of its inputs.
 *
 * An image's thumbnail is what the device holds when it holds something drawable (an Android
 * content `Uri`, iOS's retained bytes in [AttachedFile.localPreview]), else the server's copy once
 * uploaded ([serverUrl] + `filepath`), else the `uri` as given (a server URL from the "From server"
 * picker). The device copy wins over the server's so the thumbnail doesn't reload as the upload lands.
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
            DraftItem.Image(file, thumbnailFor(file, serverUrl), key)
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
    val uploading = files.filter { it.isUploading }
    val progresses = uploading.mapNotNull { it.uploadProgress }
    return DraftItems(
        items = items,
        fileCount = files.size,
        failedCount = failed.size,
        uploadingCount = uploading.size,
        uploadProgress = progresses.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size },
    )
}

private fun thumbnailFor(file: AttachedFile, serverUrl: String): Any? {
    file.localPreview?.let { return it }
    // A non-String uri is a platform handle an image loader reads (an Android content Uri).
    if (file.uri !is String) return file.uri
    val server = file.filepath?.let {
        resolveFileReferenceUrl(FileReference(fileId = file.fileId, filepath = it, type = file.type), serverUrl)
    }
    return server ?: file.uri
}
