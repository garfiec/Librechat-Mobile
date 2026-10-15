package com.garfiec.librechat.core.data.search

import com.garfiec.librechat.core.data.prefetch.PrefetchConditions
import com.garfiec.librechat.core.data.prefetch.PrefetchGate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface SearchIndexWindow {
    val isOpen: Flow<Boolean>
}

/**
 * The prefetch gate's conditions, minus the two that don't apply to local work: the prefetch
 * opt-in (gating on it would leave users who keep prefetch off without an index) and the network.
 */
fun PrefetchConditions.allowsIndexing(): Boolean = appAvailable && appIdle && powerAvailable

class PrefetchGateSearchIndexWindow(gate: PrefetchGate) : SearchIndexWindow {
    override val isOpen: Flow<Boolean> = gate.conditions().map { it.allowsIndexing() }.distinctUntilChanged()
}
