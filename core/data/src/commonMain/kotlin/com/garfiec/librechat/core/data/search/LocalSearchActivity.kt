package com.garfiec.librechat.core.data.search

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Whether a local message search is running, so the index's background work steps aside for it.
 * The prefetch gate's idle signal counts only HTTP requests, and a search is pure database work.
 */
class LocalSearchActivity {
    private val running = MutableStateFlow(0)

    val inFlight: StateFlow<Int> = running.asStateFlow()

    suspend fun <T> track(block: suspend () -> T): T {
        running.update { it + 1 }
        try {
            return block()
        } finally {
            running.update { it - 1 }
        }
    }
}
