package com.garfiec.librechat.navigation

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.ui.platform.UriHandler
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import com.garfiec.librechat.MainActivity
import com.garfiec.librechat.core.common.identity.ShareLinkMatcher

/**
 * Opens a link in a browser and never back in this app. The app claims `/share/` links on every
 * host ("Open with"), so a plain ACTION_VIEW of one can come straight back here: a disambiguation
 * dialog listing the app on Android 8–11, or the app itself where the user allowed it to open
 * links. Each pass would route it again and hand it back to the browser.
 */
internal object ExternalBrowser {

    fun open(context: Context, url: String) {
        val view = Intent(Intent.ACTION_VIEW, url.toUri())
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // The default browser, or a chooser of browsers: the app is not one. Not
        // makeMainSelectorActivity: that returns a MAIN/LAUNCHER intent with the browser selector
        // nested inside, and as a selector it resolves to every launcher activity, this app's too.
        val inBrowser = Intent(view).apply {
            selector = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
        }
        try {
            context.startActivity(inBrowser)
        } catch (_: ActivityNotFoundException) {
            // No app declares itself a browser: let the user pick, with this app left off the list.
            val chooser = Intent.createChooser(view, null)
                .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, MainActivity::class.java)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(chooser)
            } catch (e: ActivityNotFoundException) {
                Logger.w(e) { "No app can open a web link" }
            }
        }
    }
}

/**
 * The app's link handler: `/share/` links the app has decided not to open go to a browser through
 * [ExternalBrowser], every other link through [fallback] as before.
 */
internal class BrowserFirstUriHandler(
    private val fallback: UriHandler,
    private val openInBrowser: (String) -> Unit,
) : UriHandler {
    override fun openUri(uri: String) {
        if (ShareLinkMatcher.parse(uri) != null) openInBrowser(uri) else fallback.openUri(uri)
    }
}
