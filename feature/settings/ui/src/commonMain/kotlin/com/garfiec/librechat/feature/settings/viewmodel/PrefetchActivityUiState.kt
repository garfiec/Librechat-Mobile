package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.model.prefetch.PrefetchConversationStatus
import com.garfiec.librechat.core.model.prefetch.PrefetchRunOutcome
import com.garfiec.librechat.feature.settings.state.PrefetchDisplayStatus

data class PrefetchActivityUiState(
    val status: PrefetchDisplayStatus = PrefetchDisplayStatus.Off,
    val warmedCount: Int = 0,
    val eligibleCount: Int = 0,
    val lastWarmedAt: Long? = null,
    /** False on platforms with no scheduler, which hides the row rather than showing "Never". */
    val scheduledRunsSupported: Boolean = false,
    val lastScheduledRunAt: Long? = null,
    val lastScheduledRunOutcome: PrefetchRunOutcome? = null,
    val cachedMessageCount: Int = 0,
    val warmed: List<PrefetchConversationStatus> = emptyList(),
    val pending: List<PrefetchConversationStatus> = emptyList(),
    val conditions: List<PrefetchConditionRow> = emptyList(),
    /** Null until the walk of the cache directory finishes. */
    val imageCacheBytes: Long? = null,
) {
    /**
     * A manual pass is only offered when the gate would admit one. Anything else would either
     * override conditions the user set — spending mobile data they declined — or silently do
     * nothing, and a dead button is worse than a disabled one that says why.
     *
     * [PrefetchCondition.SERVER] is deliberately excluded. It reports the breaker, and clearing the
     * breaker is precisely what this button does — gating on it would disable the control in the one
     * situation it exists for, leaving a killed process as the only way back.
     *
     * Empty conditions mean the first status has not arrived, which is not the same as the gate
     * being open — so the control starts disabled rather than briefly offering a run that would be
     * dropped.
     */
    val canWarmNow: Boolean
        get() = conditions.isNotEmpty() && conditions.none { it.condition.gatesManualRun && !it.met }
}

data class PrefetchConditionRow(
    val condition: PrefetchCondition,
    val met: Boolean,
    /** Set on the network row when the device has no connection, rather than a declined one. */
    val offline: Boolean = false,
)

enum class PrefetchCondition(
    /** Whether an unmet value should block the manual run. False only for the breaker. */
    val gatesManualRun: Boolean,
) {
    ENABLED(gatesManualRun = true),
    APP_AVAILABLE(gatesManualRun = true),
    NETWORK(gatesManualRun = true),
    POWER(gatesManualRun = true),
    IDLE(gatesManualRun = true),
    SERVER(gatesManualRun = false),
}
