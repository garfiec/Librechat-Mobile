package com.garfiec.librechat.core.data.prefetch

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.garfiec.librechat.core.logging.Diag
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.minutes

/**
 * Runs one prefetch pass on the schedule set by [WorkManagerPrefetchScheduler].
 *
 * Resolves from Koin rather than taking constructor injection: the graph is started in
 * `Application.onCreate`, and a worker is only ever instantiated when its job runs, which is always
 * afterwards. That avoids a custom `WorkerFactory` and a `Configuration.Provider` on the Application
 * for a single worker.
 */
@Suppress("NoKoinComponentInterface", "NoInjectDelegate") // see KDoc: avoids a custom WorkerFactory for one worker
class PrefetchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val runner: PrefetchBackgroundRunner by inject()

    override suspend fun doWork(): Result {
        val outcome = runner.runOnce(BUDGET)
        Diag.d("Prefetch", attrs = mapOf("outcome" to outcome.name)) { "worker finished" }

        // Always success, never retry. `setRequiresDeviceIdle` suppresses WorkManager's backoff, so a
        // retry would just wait for the next period — which success already does, without the job
        // reading as failed in diagnostics. Nothing is lost by waiting: watermarks make a partial
        // pass resumable, and an unmet constraint is not an error.
        return Result.success()
    }

    private companion object {
        /**
         * Comfortably inside WorkManager's ~10-minute execution window, leaving room for the pass to
         * unwind. A pass that outruns it is not lost — it resumes from its watermarks next time.
         */
        val BUDGET = 8.minutes
    }
}
