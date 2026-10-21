package com.garfiec.librechat

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The merged manifest's "Open with" filter for shared-chat links: every server shape the in-app
 * matcher accepts (root and subpath deployments), and nothing else on the web.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SharedLinkOpenWithFilterTest {

    private fun opensInApp(url: String): Boolean {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = Intent(Intent.ACTION_VIEW, url.toUri())
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(context.packageName)
        return context.packageManager.queryIntentActivities(view, 0)
            .any { it.activityInfo.name == MainActivity::class.java.name }
    }

    @Test
    fun aLinkOnARootServerIsOffered() {
        assertTrue(opensInApp("https://chat.example.com/share/abc123"))
        assertTrue(opensInApp("http://192.168.1.20:3080/share/abc123"))
    }

    @Test
    fun aLinkOnASubpathServerIsOffered() {
        assertTrue(opensInApp("https://home.example/librechat/share/abc123"))
        assertTrue(opensInApp("https://home.example/apps/librechat/share/abc123"))
    }

    @Test
    fun otherWebLinksAreNotClaimed() {
        assertFalse(opensInApp("https://chat.example.com/c/new"))
        assertFalse(opensInApp("https://chat.example.com/"))
        assertFalse(opensInApp("https://example.com/shared/abc"))
    }
}
