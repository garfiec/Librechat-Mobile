package com.garfiec.librechat.core.model.config

import kotlinx.serialization.Serializable

/** `balance` in `/api/config`. Upstream validates every amount as `z.number()`, so none is guaranteed whole. */
@Serializable
data class BalanceConfig(
    val enabled: Boolean = false,
    val startBalance: Double? = null,
    val autoRefillEnabled: Boolean = false,
    val refillIntervalValue: Double? = null,
    val refillIntervalUnit: String? = null,
    val refillAmount: Double? = null,
    /** Upstream `BALANCE_REFILL_MODES` (v0.8.9-rc1): `add` (default) or `reset`. Kept raw. */
    val refillMode: String? = null,
    /**
     * How the balance is shown (v0.8.9-rc1): `credits` (default), `currency` or `percent`. Kept raw:
     * admin overrides reach the client unvalidated, and an unknown mode reads as `credits`.
     */
    val display: String? = null,
)
