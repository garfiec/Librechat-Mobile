package com.garfiec.librechat.core.network.api.dto

import kotlinx.serialization.Serializable

/** Body of `POST /api/auth/2fa/setup`, and of `/setup/confirm` with [token] (the 6-digit code). */
@Serializable
data class TwoFactorSetupRequest(
    val tempToken: String,
    val token: String? = null,
)

@Serializable
data class TwoFactorSetupAcknowledgeRequest(
    val acknowledgementToken: String,
)

@Serializable
data class TwoFactorSetupFinalizeRequest(
    val finalizationToken: String,
)
