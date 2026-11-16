package com.garfiec.librechat.core.data.gate

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * Opens the gate's sign-in in a Custom Tab launched from the app's current activity, so the tab sits
 * in the app's own task and [close] can bring the app back over it. The browser counts as gone when
 * the launching activity resumes after the tab covered it, whether the user closed the tab or [close]
 * did.
 *
 * Built from the Custom Tabs intent contract (an `ACTION_VIEW` carrying [EXTRA_SESSION]) rather than
 * `androidx.browser`, which would add a dependency for one extra.
 */
internal class AndroidSystemAuthBrowser(application: Application) : SystemAuthBrowser {

    private var current: WeakReference<Activity>? = null
    private var launcher: WeakReference<Activity>? = null
    private var gone: CompletableDeferred<Unit>? = null
    private var covered = false

    init {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                current = WeakReference(activity)
                if (covered && activity === launcher?.get()) {
                    covered = false
                    gone?.complete(Unit)
                }
            }

            override fun onActivityPaused(activity: Activity) {
                if (gone != null && activity === launcher?.get()) covered = true
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    override suspend fun open(url: String) {
        val signal = CompletableDeferred<Unit>()
        withContext(Dispatchers.Main.immediate) {
            val activity = checkNotNull(current?.get()) { "no activity to open the browser from" }
            launcher = WeakReference(activity)
            covered = false
            gone = signal
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                putExtras(Bundle().apply { putBinder(EXTRA_SESSION, null) })
            }
            activity.startActivity(intent)
        }
        try {
            signal.await()
        } finally {
            withContext(Dispatchers.Main.immediate) {
                if (gone === signal) gone = null
            }
        }
    }

    override fun close() {
        val activity = launcher?.get() ?: return
        activity.runOnUiThread {
            if (!covered) return@runOnUiThread
            // singleTask + CLEAR_TOP finishes the tab sitting above the activity in its task.
            activity.startActivity(
                Intent(activity, activity.javaClass)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }

    private companion object {
        const val EXTRA_SESSION = "android.support.customtabs.extra.SESSION"
    }
}
