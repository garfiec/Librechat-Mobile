package com.garfiec.librechat.core.ui.media

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.toByteArray
import com.garfiec.librechat.core.ui.platform.currentTopmostViewController
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIImage
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Strong reference to the open picker's delegate: PHPickerViewController holds it weakly, and
 * Kotlin/Native would collect it while the picker is open. Only one picker is open at a time.
 */
private var activePhotoPickerDelegate: NSObject? = null

/**
 * Opens the system photo picker, images only, up to [selectionLimit] at once. [decode] runs off
 * Main for each picked image with its bytes, read inside the picker's callback (the asset's
 * original data, often HEIC), its suggested name and the decoded image. [onResult] gets every
 * non-null result on Main once all have loaded, or an empty list when the user cancels.
 */
fun <T : Any> openPhotoPicker(
    selectionLimit: Long,
    decode: (bytes: ByteArray, suggestedName: String?, image: UIImage?) -> T?,
    onResult: (List<T>) -> Unit,
) {
    dispatch_async(dispatch_get_main_queue()) {
        val viewController = currentTopmostViewController() ?: run {
            Logger.w { "PhotoPicker: no root view controller" }
            return@dispatch_async
        }

        val config = PHPickerConfiguration()
        config.selectionLimit = selectionLimit
        config.filter = PHPickerFilter.imagesFilter

        val picker = PHPickerViewController(configuration = config)

        // Only results of [decode] are collected, so the list holds nothing but T.
        @Suppress("UNCHECKED_CAST")
        val delegate = PhotoPickerDelegate(decode) { results -> onResult(results as List<T>) }
        picker.delegate = delegate
        activePhotoPickerDelegate = delegate

        viewController.presentViewController(picker, animated = true, completion = null)
    }
}

/** Not generic: it subclasses an Objective-C class. */
private class PhotoPickerDelegate(
    private val decode: (bytes: ByteArray, suggestedName: String?, image: UIImage?) -> Any?,
    private val onResult: (List<Any>) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)

        if (didFinishPicking.isEmpty()) {
            activePhotoPickerDelegate = null
            onResult(emptyList())
            return
        }

        // `results`/`remaining` are only touched on Main, so completions are serialized; each
        // decode runs off Main in the load callback and hops back to record its outcome.
        val results = mutableListOf<Any>()
        var remaining = didFinishPicking.size

        fun completeOne(result: Any?) {
            dispatch_async(dispatch_get_main_queue()) {
                if (result != null) results.add(result)
                remaining--
                if (remaining == 0) {
                    activePhotoPickerDelegate = null
                    onResult(results)
                }
            }
        }

        for (item in didFinishPicking) {
            val provider = (item as? PHPickerResult)?.itemProvider
            if (provider == null || !provider.hasItemConformingToTypeIdentifier("public.image")) {
                completeOne(null)
                continue
            }
            provider.loadDataRepresentationForTypeIdentifier("public.image") { data, error ->
                val bytes = data?.toByteArray()?.takeIf { it.isNotEmpty() }
                val result = if (data != null && bytes != null) {
                    decode(bytes, provider.suggestedName, UIImage(data = data))
                } else {
                    null
                }
                if (result == null) {
                    Logger.e { "PhotoPicker: failed to load image: ${error?.localizedDescription}" }
                }
                completeOne(result)
            }
        }
    }
}
