package com.garfiec.librechat.feature.settings.screen.providerkeys

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Whether a status line reads its answer to a Retry out loud ([active], its `announceChanges`).
 *
 * On from the tap ([onRetry]) until [window] after the retry's answer is shown, then off again. Not
 * for good: a key in its last hour counts down every minute, and a line left a live region would be
 * read out on each tick for as long as the user stays.
 */
@Stable
internal class RetryAnnouncement(private val window: Duration = AnnounceWindow) {
    var active: Boolean by mutableStateOf(false)
        private set

    /** Bumped on every tap, so a tap restarts [follow]'s window even when `retrying` hasn't moved yet. */
    private val retries = MutableStateFlow(0)

    fun onRetry() {
        active = true
        retries.update { it + 1 }
    }

    /**
     * Follows the retry's progress for as long as the line is shown: while a retry runs the line
     * stays live; once it has answered, it stays live for [window], so the change is still inside
     * the live region when the screen reader picks it up, and then goes quiet. A new tap, or the
     * retry starting again, restarts the wait. Never returns; cancel it with the line.
     */
    suspend fun follow(retrying: Flow<Boolean>) {
        combine(retries, retrying) { _, isRetrying -> isRetrying }.collectLatest { isRetrying ->
            if (!active || isRetrying) return@collectLatest
            delay(window)
            active = false
        }
    }

    private companion object {
        /** Comfortably past the ~100 ms the semantics tree batches changes over. */
        val AnnounceWindow: Duration = 1.seconds
    }
}

/** A [RetryAnnouncement] for the retry whose progress is [retrying], reset when [key] changes. */
@Composable
internal fun rememberRetryAnnouncement(retrying: Boolean, key: Any? = Unit): RetryAnnouncement {
    val announcement = remember(key) { RetryAnnouncement() }
    val currentRetrying by rememberUpdatedState(retrying)
    LaunchedEffect(announcement) { announcement.follow(snapshotFlow { currentRetrying }) }
    return announcement
}
