package com.garfiec.librechat.navigation

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.ui.platform.UriHandler
import androidx.test.core.app.ApplicationProvider
import com.garfiec.librechat.MainActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The guard that keeps a `/share/` link the app turned down from coming straight back to it: the
 * app claims those links on every host ("Open with"), so a plain ACTION_VIEW would loop.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ExternalBrowserTest {

    private val url = "https://chat.example.com/share/abc123"

    /** Records what is started; refuses the first [refusals] starts as a device without a browser would. */
    private class RecordingContext(base: Context, private var refusals: Int = 0) : ContextWrapper(base) {
        val started = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            started += intent
            if (refusals-- > 0) throw ActivityNotFoundException()
        }
    }

    private fun context(refusals: Int = 0) =
        RecordingContext(ApplicationProvider.getApplicationContext(), refusals)

    @Test
    fun aShareLinkGoesToTheBrowserSelectorNotAPlainView() {
        val context = context()

        ExternalBrowser.open(context, url)

        val intent = context.started.single()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(url, intent.dataString)
        val selector = assertNotNull(intent.selector)
        assertEquals(Intent.ACTION_MAIN, selector.action)
        assertEquals(setOf(Intent.CATEGORY_APP_BROWSER), selector.categories)
    }

    @Test
    fun withNoBrowserTheChooserLeavesTheAppOut() {
        val context = context(refusals = 1)

        ExternalBrowser.open(context, url)

        assertEquals(2, context.started.size)
        val chooser = context.started[1]
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val excluded = chooser.getParcelableArrayExtra(Intent.EXTRA_EXCLUDE_COMPONENTS).orEmpty().toList()
        assertTrue(ComponentName(context, MainActivity::class.java) in excluded)
        @Suppress("DEPRECATION")
        val target = assertNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
        assertEquals(url, target.dataString)
    }

    @Test
    fun noAppAtAllIsNotACrash() {
        ExternalBrowser.open(context(refusals = 2), url)
    }

    @Test
    fun theAppHandlerSendsShareLinksToTheBrowserAndEverythingElseOn() {
        val toBrowser = mutableListOf<String>()
        val toFallback = mutableListOf<String>()
        val handler = BrowserFirstUriHandler(
            fallback = object : UriHandler {
                override fun openUri(uri: String) {
                    toFallback += uri
                }
            },
            openInBrowser = { toBrowser += it },
        )

        handler.openUri(url)
        handler.openUri("https://home.example/librechat/share/xyz")
        handler.openUri("https://x/c/abc")
        handler.openUri("mailto:someone@example.com")

        assertEquals(listOf(url, "https://home.example/librechat/share/xyz"), toBrowser)
        assertEquals(listOf("https://x/c/abc", "mailto:someone@example.com"), toFallback)
    }
}
