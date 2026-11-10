package com.garfiec.librechat.feature.settings.di

import co.touchlab.kermit.Logger
import com.garfiec.librechat.feature.settings.util.ContentReader
import com.garfiec.librechat.feature.settings.util.PlatformCacheCleaner
import com.garfiec.librechat.feature.settings.viewmodel.delegate.IosSpeechSettingsDelegate
import com.garfiec.librechat.feature.settings.viewmodel.delegate.SpeechSettingsFactory
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

actual val settingsPlatformModule: Module = module {
    single {
        object : ContentReader {
            // The avatar picker hands over the bytes it read in its own callback (rememberAvatarPicker).
            override fun readBytes(uri: Any): ByteArray? = (uri as? ByteArray).also {
                if (it == null) Logger.w("SettingsContentReader") { "readBytes called with ${uri::class}" }
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
            )
        }
    }
}
