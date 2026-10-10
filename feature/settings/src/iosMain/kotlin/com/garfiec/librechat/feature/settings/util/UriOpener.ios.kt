package com.garfiec.librechat.feature.settings.util

import androidx.compose.runtime.Composable
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

private object IosUriOpener : UriOpener {
    override fun open(uri: String): Boolean {
        val url = NSURL.URLWithString(uri) ?: return false
        val app = UIApplication.sharedApplication
        if (!app.canOpenURL(url)) return false
        app.openURL(url)
        return true
    }
}

@Composable
actual fun rememberUriOpener(): UriOpener = IosUriOpener
