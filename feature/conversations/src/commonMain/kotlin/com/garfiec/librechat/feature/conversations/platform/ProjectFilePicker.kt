package com.garfiec.librechat.feature.conversations.platform

import androidx.compose.runtime.Composable

/**
 * One file the user picked for a project. [readBytes] does the read off the main thread; null means
 * the file could not be read (revoked, gone, or too large to hold).
 */
class PickedProjectFile(
    val name: String,
    val mimeType: String,
    val readBytes: suspend () -> ByteArray?,
)

/**
 * The device's document picker for a project's reference files, several at once (Android
 * `OpenMultipleDocuments`, iOS `UIDocumentPickerViewController`). A copy of the agent editor's
 * picker rather than a shared one: feature modules can't depend on each other.
 */
@Composable
expect fun rememberProjectFilePicker(onPick: (List<PickedProjectFile>) -> Unit): ProjectFilePicker

expect class ProjectFilePicker {
    fun launch()
}

/** The upload's MIME type when the platform names none. */
internal const val FALLBACK_MIME_TYPE = "application/octet-stream"
