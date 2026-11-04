package com.garfiec.librechat.feature.conversations.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.toByteArray
import com.garfiec.librechat.core.ui.platform.currentTopmostViewController
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.lastPathComponent
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeContent
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * The picker holds its delegate weakly and Kotlin/Native won't keep it alive, so each presented
 * delegate is held here until it reports a pick or a cancel.
 */
private val activeProjectPickerDelegates = mutableSetOf<NSObject>()

@Composable
actual fun rememberProjectFilePicker(onPick: (List<PickedProjectFile>) -> Unit): ProjectFilePicker {
    val callback = rememberUpdatedState(onPick)
    val picker = remember { ProjectFilePicker() }
    DisposableEffect(picker) {
        picker.onPicked = { files -> callback.value(files) }
        onDispose { picker.onPicked = null }
    }
    return picker
}

actual class ProjectFilePicker {
    internal var onPicked: ((List<PickedProjectFile>) -> Unit)? = null

    actual fun launch() {
        dispatch_async(dispatch_get_main_queue()) {
            val viewController = currentTopmostViewController() ?: run {
                Logger.w { "ProjectFilePicker: no root view controller" }
                return@dispatch_async
            }
            val controller = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeContent, UTTypeData))
            controller.allowsMultipleSelection = true
            val delegate = PickerDelegate { files -> onPicked?.invoke(files) }
            controller.delegate = delegate
            activeProjectPickerDelegates.add(delegate)
            viewController.presentViewController(controller, animated = true, completion = null)
        }
    }
}

private class PickerDelegate(
    private val onResult: (List<PickedProjectFile>) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        try {
            // Read inside the callback, while each security-scoped resource is open; a read later
            // from a coroutine would find the scope closed.
            val files = didPickDocumentsAtURLs.mapNotNull { (it as? NSURL)?.preload() }
            if (files.isNotEmpty()) onResult(files)
        } finally {
            activeProjectPickerDelegates.remove(this)
        }
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        activeProjectPickerDelegates.remove(this)
    }
}

private fun NSURL.preload(): PickedProjectFile {
    val accessing = startAccessingSecurityScopedResource()
    val bytes = try {
        NSData.dataWithContentsOfURL(this)?.toByteArray()
    } finally {
        if (accessing) stopAccessingSecurityScopedResource()
    }
    val name = lastPathComponent ?: "file"
    val ext = name.substringAfterLast('.', "")
    val mime = UTType.typeWithFilenameExtension(ext)?.preferredMIMEType ?: FALLBACK_MIME_TYPE
    return PickedProjectFile(name = name, mimeType = mime) { bytes }
}
