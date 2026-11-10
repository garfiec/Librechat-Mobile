package com.garfiec.librechat.core.notifications

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid
import kotlin.time.Duration.Companion.hours

/**
 * Watches in-process under a background task assertion, so a watch survives leaving the app only
 * for the short time iOS grants (about 30 seconds): a reply that ends after that is not announced.
 * In front, the watch runs until the reply ends. Main-thread confined, like its callers.
 */
class IosReplyWatchScheduler(
    private val watcher: ReplyWatcher,
    private val scope: CoroutineScope,
) : ReplyWatchScheduler {

    private val jobs = mutableMapOf<String, Job>()

    override fun watch(watch: ReplyWatch) {
        stop(watch.conversationId)
        val app = UIApplication.sharedApplication
        var task: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid
        fun endTask() {
            if (task != UIBackgroundTaskInvalid) {
                app.endBackgroundTask(task)
                task = UIBackgroundTaskInvalid
            }
        }
        val job = scope.launch(Dispatchers.Main, start = CoroutineStart.LAZY) {
            try {
                watcher.watch(watch, BUDGET)
            } finally {
                endTask()
                if (jobs[watch.conversationId] === coroutineContext[Job]) jobs.remove(watch.conversationId)
            }
        }
        // iOS requires the expiration handler to end the task itself, before it returns.
        task = app.beginBackgroundTaskWithName("reply-watch") {
            job.cancel()
            endTask()
        }
        jobs[watch.conversationId] = job
        job.start()
    }

    override fun stop(conversationId: String) {
        jobs.remove(conversationId)?.cancel()
    }

    private companion object {
        val BUDGET = 2.hours
    }
}
