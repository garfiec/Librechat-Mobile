package com.garfiec.librechat.core.notifications

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.garfiec.librechat.core.common.identity.AccountId
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

private const val KEY_ACCOUNT = "account"
private const val KEY_CONVERSATION = "conversation"

/**
 * Plain WorkManager jobs, no foreground service: in Doze the system may defer one, so a reply that
 * ends with the screen long off can be announced late.
 */
class WorkManagerReplyWatchScheduler(context: Context) : ReplyWatchScheduler {
    private val workManager = WorkManager.getInstance(context)

    override fun watch(watch: ReplyWatch) {
        val request = OneTimeWorkRequestBuilder<ReplyWatchWorker>()
            .setInputData(workDataOf(KEY_ACCOUNT to watch.accountId.value, KEY_CONVERSATION to watch.conversationId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, RETRY_DELAY_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(workName(watch.conversationId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun stop(conversationId: String) {
        workManager.cancelUniqueWork(workName(conversationId))
    }

    private fun workName(conversationId: String) = "reply-watch:$conversationId"

    private companion object {
        const val RETRY_DELAY_SECONDS = 30L
    }
}

/** Resolves from Koin like `PrefetchWorker`, to avoid a custom `WorkerFactory`. */
@Suppress("NoKoinComponentInterface", "NoInjectDelegate") // see KDoc
class ReplyWatchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val watcher: ReplyWatcher by inject()

    override suspend fun doWork(): Result {
        val account = inputData.getString(KEY_ACCOUNT)?.takeIf { it.isNotBlank() } ?: return Result.success()
        val conversationId = inputData.getString(KEY_CONVERSATION) ?: return Result.success()
        return when (watcher.watch(ReplyWatch(AccountId(account), conversationId), BUDGET)) {
            ReplyWatchOutcome.StillRunning -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
            ReplyWatchOutcome.Ended, ReplyWatchOutcome.Abandoned -> Result.success()
        }
    }

    private companion object {
        /** Inside WorkManager's ten-minute window. */
        val BUDGET = 9.minutes

        /** About two hours of watching, past which a run is not one the user is waiting on. */
        const val MAX_ATTEMPTS = 12
    }
}
