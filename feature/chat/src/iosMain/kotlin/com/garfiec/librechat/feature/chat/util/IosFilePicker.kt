package com.garfiec.librechat.feature.chat.util

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.toByteArray
import com.garfiec.librechat.core.ui.platform.currentTopmostViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.lastPathComponent
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeContent
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue

/**
 * Strong reference to the currently active picker delegate.
 * Prevents Kotlin/Native GC from collecting the delegate while the picker is open.
 * Only one picker can be open at a time, so a single reference suffices.
 */
private var activePickerDelegate: NSObject? = null

/**
 * Opens UIDocumentPickerViewController for general file selection.
 * Calls [onResult] with a list of [IosFileData] or [IosImageData].
 */
fun openDocumentPicker(onResult: (List<Any>) -> Unit) {
    dispatch_async(dispatch_get_main_queue()) {
        val viewController = currentTopmostViewController() ?: run {
            Logger.w { "IosFilePicker: no root view controller" }
            return@dispatch_async
        }

        val types = listOf(UTTypeContent ?: UTType.typeWithIdentifier("public.content")!!)
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = types)
        picker.allowsMultipleSelection = true

        val delegate = DocumentPickerDelegate(onResult)
        picker.delegate = delegate
        activePickerDelegate = delegate

        viewController.presentViewController(picker, animated = true, completion = null)
    }
}

/**
 * Opens UIImagePickerController for camera capture.
 * On simulator (no camera hardware), calls onResult with empty list.
 */
fun openCamera(onResult: (List<Any>) -> Unit) {
    dispatch_async(dispatch_get_main_queue()) {
        val viewController = currentTopmostViewController() ?: run {
            Logger.w { "IosFilePicker: no root view controller" }
            return@dispatch_async
        }

        if (!UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) {
            Logger.w { "IosFilePicker: camera not available (simulator?)" }
            onResult(emptyList())
            return@dispatch_async
        }

        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        picker.allowsEditing = false

        val delegate = CameraDelegate(onResult)
        picker.delegate = delegate
        activePickerDelegate = delegate

        viewController.presentViewController(picker, animated = true, completion = null)
    }
}

// ── Delegates ──────────────────────────────────────────────────

/** A document whose bytes were materialized inside the security-scoped callback. */
private class LoadedDocument(val data: NSData, val filename: String)

private class DocumentPickerDelegate(
    private val onResult: (List<Any>) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    @OptIn(ExperimentalForeignApi::class)
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        // Pull each file's bytes into memory while its security-scoped resource
        // is held — the scope is only valid for the duration of this callback,
        // so the read must happen here. dataWithContentsOfURL fully materializes
        // the NSData, so the scope can be released immediately afterward; the
        // heavy byte-copy + image decode then runs off the Main thread.
        val loaded = mutableListOf<LoadedDocument>()
        for (item in didPickDocumentsAtURLs) {
            val url = item as? NSURL ?: continue
            val accessing = url.startAccessingSecurityScopedResource()
            try {
                val data = NSData.dataWithContentsOfURL(url) ?: continue
                val filename = url.lastPathComponent ?: "file"
                loaded.add(LoadedDocument(data = data, filename = filename))
            } finally {
                if (accessing) url.stopAccessingSecurityScopedResource()
            }
        }
        activePickerDelegate = null

        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            val results = mutableListOf<Any>()
            for (doc in loaded) {
                val bytes = doc.data.toByteArray().takeIf { it.isNotEmpty() } ?: continue
                val mimeType = guessMimeType(doc.filename)

                if (mimeType.startsWith("image/")) {
                    val image = UIImage(data = doc.data)
                    val cgImage = image?.CGImage
                    results.add(
                        IosImageData(
                            bytes = bytes,
                            filename = doc.filename,
                            mimeType = mimeType,
                            width = cgImage?.let { CGImageGetWidth(it).toInt() },
                            height = cgImage?.let { CGImageGetHeight(it).toInt() },
                            preview = draftPreviewOf(image),
                        ),
                    )
                } else {
                    results.add(IosFileData(bytes = bytes, filename = doc.filename, mimeType = mimeType))
                }
            }
            dispatch_async(dispatch_get_main_queue()) {
                onResult(results)
            }
        }
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        activePickerDelegate = null
    }
}

/**
 * A photo-picker pick as an attachment, for the shared photo picker's `decode` (runs off Main).
 * [bytes] are the asset's original data, often HEIC whatever the type says.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun photoPickerAttachment(bytes: ByteArray, suggestedName: String?, image: UIImage?): IosImageData {
    val filename = suggestedName ?: "photo"
    val cgImage = image?.CGImage
    val ext = if (filename.contains(".")) "" else ".jpg"
    return IosImageData(
        bytes = bytes,
        filename = "$filename$ext",
        mimeType = "image/jpeg",
        width = cgImage?.let { CGImageGetWidth(it).toInt() },
        height = cgImage?.let { CGImageGetHeight(it).toInt() },
        preview = draftPreviewOf(image),
    )
}

@OptIn(ExperimentalForeignApi::class)
private class CameraDelegate(
    private val onResult: (List<Any>) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)
        activePickerDelegate = null

        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage ?: return
        // A full-resolution capture (12MP+) is expensive to JPEG-encode and copy.
        // The UIImage is already in memory, so hand the encode to a background
        // queue and deliver the result back on Main.
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            val data = UIImageJPEGRepresentation(image, 0.85) ?: return@dispatch_async
            val bytes = data.toByteArray().takeIf { it.isNotEmpty() } ?: return@dispatch_async
            val cgImage = image.CGImage
            val width = cgImage?.let { CGImageGetWidth(it).toInt() }
            val height = cgImage?.let { CGImageGetHeight(it).toInt() }
            val preview = draftPreviewOf(image)
            val timestamp = NSDate().timeIntervalSince1970.toLong()
            dispatch_async(dispatch_get_main_queue()) {
                onResult(
                    listOf(
                        IosImageData(
                            bytes = bytes,
                            filename = "photo_$timestamp.jpg",
                            mimeType = "image/jpeg",
                            width = width,
                            height = height,
                            preview = preview,
                        ),
                    ),
                )
            }
        }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        activePickerDelegate = null
    }
}

// ── Utilities ──────────────────────────────────────────────────

private fun guessMimeType(filename: String): String {
    val ext = filename.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heic"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        "csv" -> "text/csv"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "zip" -> "application/zip"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "potx" -> "application/vnd.openxmlformats-officedocument.presentationml.template"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        else -> "application/octet-stream"
    }
}
