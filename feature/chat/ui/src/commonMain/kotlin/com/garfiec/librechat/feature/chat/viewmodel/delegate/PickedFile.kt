package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.model.response.UploadRoute

/**
 * A file the user picked, with just enough resolved about it to decide how to upload it.
 *
 * [ref] is the opaque platform handle the picker produced (an Android `Uri`, an `IosFileData` /
 * `IosImageData`), carried back verbatim in [RoutedFile]. It deliberately isn't a typed parameter:
 * this type sits in `commonMain`, and the whole point is that neither side has to know the other's
 * handle. Deliberately NOT in `:core:model` either — that module is exported into the iOS
 * framework, and an `Any` in its public surface would be.
 */
data class PickedFile(val ref: Any, val name: String, val mimeType: String?)

/**
 * A [PickedFile] paired with the delivery mode chosen for it. Carrying the whole description back
 * (rather than just the ref) means the handler doesn't re-resolve the name and type it already
 * asked for — on Android those are main-thread ContentResolver queries.
 */
data class RoutedFile(val file: PickedFile, val route: UploadRoute)

/**
 * A PDF upload held until the user types the file's password. [chipKey] is the attached-file chip
 * (its `uri`), left pending (not failed) meanwhile; [file] is what to upload again with the password.
 */
data class PdfPasswordPrompt(
    val chipKey: Any,
    val file: PickedFile,
    val incorrectPassword: Boolean,
)
