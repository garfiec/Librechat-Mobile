package com.garfiec.librechat

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.garfiec.librechat.core.common.CHANNEL_META_DATA
import com.garfiec.librechat.core.common.DistributionChannel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Runs once per variant under `./gradlew test`, against that variant's merged manifest. A flavor
 * whose placeholder is missing or misspelled would otherwise only show up as a launch crash.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DistributionChannelManifestTest {

    @Test
    fun eachFlavorDeclaresItsChannel() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // The same read AndroidAppInfo makes.
        val declared = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_META_DATA)
            .applicationInfo
            ?.metaData
            ?.getString(CHANNEL_META_DATA)

        val expected = when (BuildConfig.FLAVOR) {
            "foss" -> DistributionChannel.OPEN_SOURCE
            "play" -> DistributionChannel.PLAY_STORE
            else -> error("No expected channel for flavor ${BuildConfig.FLAVOR}; add it here.")
        }
        assertEquals(expected, DistributionChannel.fromWireName(declared, source = "the merged manifest"))
    }

    @Test
    fun onlyTheOpenSourceChannelOwnsTheBarePackage() {
        val expected = when (BuildConfig.FLAVOR) {
            "foss" -> "com.garfiec.librechat"
            "play" -> "com.garfiec.librechat.play"
            else -> error("No expected package for flavor ${BuildConfig.FLAVOR}; add it here.")
        }
        assertEquals(expected, BuildConfig.APPLICATION_ID.removeSuffix(".debug"))
    }
}
