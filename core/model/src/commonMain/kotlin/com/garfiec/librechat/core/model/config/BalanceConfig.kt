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
)
