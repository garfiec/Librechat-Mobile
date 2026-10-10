package com.garfiec.librechat.feature.files.platform

import com.garfiec.librechat.core.common.toByteArray
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.lastPathComponent
import platform.Foundation.pathExtension

class IosFileReader : FileReader {

    override fun readBytes(fileRef: Any): ByteArray? {
        val url = fileRef as? NSURL ?: return null
        val data = NSData.dataWithContentsOfURL(url) ?: return null
        return data.toByteArray()
    }

    override fun getFileName(fileRef: Any): String? {
        val url = fileRef as? NSURL ?: return null
        return url.lastPathComponent
    }

    override fun getMimeType(fileRef: Any): String? {
        val url = fileRef as? NSURL ?: return null
        val ext = url.pathExtension ?: return null
        return CommonMimeTypes.fromExtension(ext)
    }
}
