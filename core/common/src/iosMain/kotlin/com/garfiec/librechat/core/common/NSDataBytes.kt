package com.garfiec.librechat.core.common

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.posix.memcpy

/** Copies the data's bytes into a new [ByteArray]; empty data gives an empty array. */
@OptIn(ExperimentalForeignApi::class)
fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).also { result -> result.usePinned { memcpy(it.addressOf(0), bytes, length) } }
}
