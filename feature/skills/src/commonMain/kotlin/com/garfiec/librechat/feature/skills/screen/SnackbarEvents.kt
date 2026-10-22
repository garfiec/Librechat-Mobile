package com.garfiec.librechat.feature.skills.screen

import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** How a screen's one-shot event uses the snackbar. */
internal enum class SnackbarSlot {
    /** Not a snackbar (a navigation): handled at once, never held behind one. */
    NONE,

    /** A failure the next event supersedes: a newer one replaces it on screen. */
    REPLACEABLE,

    /**
     * Sent once and never again (a save's warnings, a failed import with its Retry): nothing
     * replaces it, and what follows waits for it to go.
     */
    KEPT,
}

/**
 * Collects [this] screen's events into the snackbar. A newer event replaces a [SnackbarSlot.REPLACEABLE]
 * one on screen (or still waiting), as `collectLatest` would; a [SnackbarSlot.KEPT] one is never
 * cut short, since it can't be shown again, so the events after it wait their turn.
 * [SnackbarSlot.NONE] events run at once. [handle] shows the event, suspending while it is up.
 */
internal suspend fun <T> Flow<T>.collectSnackbarEvents(slot: (T) -> SnackbarSlot, handle: suspend (T) -> Unit) =
    coroutineScope {
        var kept: Job? = null
        var replaceable: Job? = null
        collect { event ->
            when (slot(event)) {
                SnackbarSlot.NONE -> handle(event)
                SnackbarSlot.REPLACEABLE -> {
                    replaceable?.cancel()
                    val ahead = kept
                    replaceable = launch {
                        ahead?.join()
                        handle(event)
                    }
                }
                SnackbarSlot.KEPT -> {
                    replaceable?.cancel()
                    replaceable = null
                    val ahead = kept
                    kept = launch {
                        ahead?.join()
                        handle(event)
                    }
                }
            }
        }
    }
