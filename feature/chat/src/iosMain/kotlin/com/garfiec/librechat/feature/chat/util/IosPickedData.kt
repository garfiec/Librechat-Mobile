package com.garfiec.librechat.feature.chat.util

/** What an iOS pick hands the upload, whichever picker made it: [IosImageData] or [IosFileData]. */
sealed interface IosPickedData {
    val bytes: ByteArray
    val filename: String
    val mimeType: String
}
