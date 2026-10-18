package com.garfiec.librechat.core.model

import kotlinx.serialization.Serializable

/**
 * `GET /api/balance`. Every amount is a plain JS number upstream: spends are multiplied by
 * fractional rates and never rounded, so [tokenCredits] is fractional after ordinary use.
 *
 * The refill fields are present only when auto-refill is enabled for the user.
 */
@Serializable
data class Balance(
    val tokenCredits: Double = 0.0,
    val autoRefillEnabled: Boolean = false,
    val refillIntervalValue: Double? = null,
    /** Upstream `RefillIntervalUnit` (`seconds` … `months`), kept raw so a new unit still decodes. */
    val refillIntervalUnit: String? = null,
    /** ISO-8601 date of the last refill. */
    val lastRefill: String? = null,
    val refillAmount: Double? = null,
)
