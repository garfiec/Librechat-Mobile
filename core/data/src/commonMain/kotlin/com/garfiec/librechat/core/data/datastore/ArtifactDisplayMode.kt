package com.garfiec.librechat.core.data.datastore

/**
 * Mobile-only preference controlling how the artifact viewer is presented when a
 * user opens an artifact from a message.
 *
 * - [AUTOMATIC] — decided by the window: split screen where the chat and the artifact
 *   both fit side by side, full screen on a short window, otherwise the bottom sheet.
 *   The default, and what an unset or unknown stored value reads as.
 * - [BOTTOM_SHEET] — always a modal bottom sheet.
 * - [FULLSCREEN] — always a full-screen page, giving complex artifacts (React apps,
 *   HTML dashboards, diagrams) the maximum available room.
 */
enum class ArtifactDisplayMode {
    AUTOMATIC, BOTTOM_SHEET, FULLSCREEN;

    companion object {
        fun fromString(value: String?): ArtifactDisplayMode = when (value) {
            "bottom_sheet" -> BOTTOM_SHEET
            "fullscreen" -> FULLSCREEN
            else -> AUTOMATIC
        }
    }

    fun toStorageString(): String = when (this) {
        AUTOMATIC -> "auto"
        BOTTOM_SHEET -> "bottom_sheet"
        FULLSCREEN -> "fullscreen"
    }
}
