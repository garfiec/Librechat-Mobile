package com.garfiec.librechat.core.common

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** The manifest meta-data key the `:app` flavors write the [DistributionChannel] wire name into. */
const val CHANNEL_META_DATA = "com.garfiec.librechat.CHANNEL"

internal class AndroidAppInfo(context: Context) : AppInfo {
    // Without GET_META_DATA, applicationInfo.metaData is null and the channel read fails at launch.
    private val packageInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_META_DATA)

    override val versionName: String = packageInfo.versionName ?: "unknown"

    override val versionCode: Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

    override val gitSha: String = BuildConfig.GIT_SHA

    override val channel: DistributionChannel = DistributionChannel.fromWireName(
        packageInfo.applicationInfo?.metaData?.getString(CHANNEL_META_DATA),
        source = "the $CHANNEL_META_DATA meta-data (set per flavor in app/build.gradle.kts)",
    )
}
