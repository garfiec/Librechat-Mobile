package com.garfiec.librechat.feature.settings.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

private class AndroidUriOpener(private val context: Context) : UriOpener {
    override fun open(uri: String): Boolean {
        // Android 11+ package visibility hides other apps from resolveActivity(), but startActivity()
        // is still resolved by the system, so catching ActivityNotFoundException -- not pre-checking --
        // is the way to detect "no authenticator installed" without a <queries> manifest entry.
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}

@Composable
actual fun rememberUriOpener(): UriOpener {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidUriOpener(context) }
}
