package com.garfiec.librechat.core.model.response

import kotlinx.serialization.Serializable

/**
 * `POST /api/auth/2fa/setup/confirm` (v0.8.9-rc1): the code matched the staged secret. Nothing is
 * enabled yet; [backupCodes] are the codes the user must keep and [acknowledgementToken] is the
 * single-use credential that says they were shown.
 */
@Serializable
data class TwoFactorSetupConfirmResponse(
    val backupCodes: List<String> = emptyList(),
    val acknowledgementToken: String? = null,
)

/** `POST /api/auth/2fa/setup/acknowledge`: the single-use credential `finalize` consumes. */
@Serializable
data class TwoFactorSetupAcknowledgeResponse(
    val finalizationToken: String? = null,
)
