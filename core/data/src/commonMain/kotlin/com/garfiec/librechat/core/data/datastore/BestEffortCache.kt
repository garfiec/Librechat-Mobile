package com.garfiec.librechat.core.data.datastore

import okio.IOException

/**
 * Runs a best-effort cache read or write, handing [onFailure] the two ways one fails: DataStore's
 * storage I/O, and a value that no longer encodes or decodes (`SerializationException` is an
 * [IllegalArgumentException], as is the bare one kotlinx.serialization throws for valid JSON that
 * is not a valid instance).
 *
 * Okio's [IOException], not `java.io`'s: on Native, DataStore's storage rethrows okio's, which is a
 * class of its own there; on the JVM it is a typealias for `java.io.IOException`. A corrupt file
 * never reaches here — the settings store heals it with `settingsCorruptionHandler`.
 */
internal inline fun <T> bestEffortCache(onFailure: (Exception) -> T, block: () -> T): T =
    try {
        block()
    } catch (e: IOException) {
        onFailure(e)
    } catch (e: IllegalArgumentException) {
        onFailure(e)
    }
