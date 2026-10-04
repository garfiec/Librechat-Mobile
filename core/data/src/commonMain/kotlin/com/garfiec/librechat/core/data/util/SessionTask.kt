package com.garfiec.librechat.core.data.util

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Work that runs whenever the app transitions into an authenticated session — either a
 * fresh login / OAuth / 2FA success, or a cold-start where a valid session is restored.
 * Implementations should be fire-and-forget; the caller does not block on completion.
 */
interface SessionTask {
    suspend fun run()
}

/**
 * Invokes each registered [SessionTask] on [applicationScope] so task work outlives the
 * caller's scope (short-lived auth VM or the one-shot NavHostViewModel init block).
 * Individual task failures are logged and swallowed so one bad task doesn't poison the
 * rest of the session-start path.
 */
class SessionTaskRunner(
    private val tasks: List<SessionTask>,
    private val applicationScope: CoroutineScope,
) {
    fun runAll() {
        tasks.forEach { task ->
            applicationScope.launch {
                try {
                    task.run()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.w(e) { "SessionTask ${task::class.simpleName} failed" }
                }
            }
        }
    }
}
