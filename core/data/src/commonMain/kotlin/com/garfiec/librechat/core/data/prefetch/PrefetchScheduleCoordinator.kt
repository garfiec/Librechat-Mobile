package com.garfiec.librechat.core.data.prefetch

import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.data.datastore.AppPreferences
import com.garfiec.librechat.core.data.datastore.PreferenceStore
import com.garfiec.librechat.core.logging.Diag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/**
 * Keeps the platform's periodic job in step with the settings it depends on.
 *
 * Separate from [PrefetchController] because it answers a different question: the controller decides
 * when a pass may *run*, this decides whether the OS should be waking us up at all.
 *
 * Two inputs, both load-bearing:
 *
 * - **The metered override**, because a job's network constraint is fixed when it is registered.
 *   Without re-registering, changing that setting would appear to do nothing until the app was
 *   reinstalled.
 * - **Whether anyone is signed in**, because a job left registered after logout wakes the device
 *   every interval to discover there is no session and exit. Nothing warms, and the wake-ups are
 *   invisible to the user who thinks they have signed out of the app.
 */
class PrefetchScheduleCoordinator(
    preferenceStore: PreferenceStore,
    activeAccountProvider: ActiveAccountProvider,
    private val scheduler: PrefetchScheduler,
    appScope: CoroutineScope,
) {

    /**
     * Emits nothing until identity actually resolves.
     *
     * Must read [ActiveAccountProvider], not the session: a null session means both "still warming"
     * and "signed out", and treating the boot value as signed out cancels the periodic work at every
     * process start — restarting its interval, and in a process the job itself woke, stopping the
     * running worker before it warms anything.
     */
    private val signedIn = activeAccountProvider.state
        .mapNotNull { state -> (state as? AccountState.Resolved)?.let { it.id != null } }

    private data class ScheduleInputs(
        val enabled: Boolean,
        val allowMetered: Boolean,
        val signedIn: Boolean,
    )

    init {
        if (scheduler.isSupported) {
            appScope.launch {
                combine(
                    preferenceStore.observe(AppPreferences.PrefetchEnabled),
                    preferenceStore.observe(AppPreferences.PrefetchOnMetered),
                    signedIn,
                ) { enabled, allowMetered, signedIn ->
                    ScheduleInputs(enabled, allowMetered, signedIn)
                }.distinctUntilChanged().collect { inputs ->
                    if (inputs.enabled && inputs.signedIn) {
                        Diag.d("Prefetch") { "scheduling periodic warm" }
                        scheduler.ensureScheduled(inputs.allowMetered)
                    } else {
                        Diag.d("Prefetch") { "cancelling periodic warm" }
                        scheduler.cancel()
                    }
                }
            }
        }
    }
}
