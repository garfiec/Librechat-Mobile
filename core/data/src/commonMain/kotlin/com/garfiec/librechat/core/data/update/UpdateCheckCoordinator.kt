package com.garfiec.librechat.core.data.update

import com.garfiec.librechat.core.common.lifecycle.ForegroundSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/** Runs the opt-in daily update check when the app comes to the foreground. */
class UpdateCheckCoordinator(
    repository: AppUpdateRepository,
    store: UpdateCheckStore,
    foregroundSignal: ForegroundSignal,
    appScope: CoroutineScope,
    now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    init {
        if (repository.isSupported) {
            appScope.launch {
                combine(foregroundSignal.isForeground, store.autoCheckEnabled) { foreground, enabled ->
                    foreground && enabled
                }
                    .distinctUntilChanged()
                    .filter { it }
                    .collect {
                        val last = store.lastCheckedAt.first()
                        if (last == null || now() - last >= INTERVAL_MILLIS) repository.check()
                    }
            }
        }
    }

    companion object {
        val INTERVAL_MILLIS = 24.hours.inWholeMilliseconds
    }
}
