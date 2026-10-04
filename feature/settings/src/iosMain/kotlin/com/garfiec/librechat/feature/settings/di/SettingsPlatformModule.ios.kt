package com.garfiec.librechat.feature.settings.di

import co.touchlab.kermit.Logger
import com.garfiec.librechat.feature.settings.util.ContentReader
import com.garfiec.librechat.feature.settings.util.PlatformCacheCleaner
import com.garfiec.librechat.feature.settings.viewmodel.delegate.IosSpeechSettingsDelegate
import com.garfiec.librechat.feature.settings.viewmodel.delegate.SpeechSettingsFactory
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfURL
import platform.posix.memcpy

actual val settingsPlatformModule: Module = module {
    single {
        @OptIn(ExperimentalForeignApi::class)
        object : ContentReader {
            override fun readBytes(uri: Any): ByteArray? {
                val nsUrl = uri as? NSURL
                if (nsUrl == null) {
                    Logger.w("SettingsContentReader") { "readBytes called with non-NSURL: ${uri::class}" }
                    return null
                }
                val data = NSData.dataWithContentsOfURL(nsUrl)
                if (data == null) {
                    Logger.w("SettingsContentReader") { "Failed to read data from: $nsUrl" }
                    return null
                }
                val size = data.length.toInt()
                if (size == 0) return ByteArray(0)
                val result = ByteArray(size)
                result.usePinned { pinned ->
                    memcpy(pinned.addressOf(0), data.bytes, data.length)
                }
                return result
            }
        }
    } bind ContentReader::class
    // The rule reads the anonymous `object :` as a generic type; it binds one non-generic interface.
    @Suppress("GenericDefinitionWithoutQualifier")
    single {
        @OptIn(ExperimentalForeignApi::class)
        object : PlatformCacheCleaner {
            // Neither body needs a catch: NSFileManager reports failure through the NSError
            // out-param (passed null) and a nil/false return, never a Kotlin exception.
            override suspend fun clearCache() {
                withContext(Dispatchers.Default) {
                    val paths = NSSearchPathForDirectoriesInDomains(
                        NSCachesDirectory,
                        NSUserDomainMask,
                        true,
                    )
                    val cachePath = paths.firstOrNull() as? String
                    if (cachePath == null) {
                        Logger.w("CacheCleaner") { "Could not resolve caches directory" }
                        return@withContext
                    }
                    val fm = NSFileManager.defaultManager
                    val contents = fm.contentsOfDirectoryAtPath(cachePath, null) as? List<*>
                    contents?.forEach { item ->
                        val name = item as? String ?: return@forEach
                        fm.removeItemAtPath("$cachePath/$name", null)
                    }
                    Logger.i("CacheCleaner") { "Cleared iOS caches directory" }
                }
            }

            override suspend fun cacheSizeBytes(): Long = withContext(Dispatchers.Default) {
                val cachePath = NSSearchPathForDirectoriesInDomains(
                    NSCachesDirectory,
                    NSUserDomainMask,
                    true,
                ).firstOrNull() as? String ?: return@withContext 0L
                val fm = NSFileManager.defaultManager
                // One level deep, matching what clearCache() removes: the readout and the button
                // must describe the same bytes, or clearing appears not to work.
                val contents = fm.contentsOfDirectoryAtPath(cachePath, null) as? List<*>
                contents.orEmpty().sumOf { item ->
                    val name = item as? String ?: return@sumOf 0L
                    val attrs = fm.attributesOfItemAtPath("$cachePath/$name", null)
                    (attrs?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
                }
            }
        }
    } bind PlatformCacheCleaner::class
    single<SpeechSettingsFactory> {
        SpeechSettingsFactory { stateHandle ->
            IosSpeechSettingsDelegate(
                stateHandle = stateHandle,
                speechRepository = get(),
                settingsDataStore = get(),
            )
        }
    }
}
