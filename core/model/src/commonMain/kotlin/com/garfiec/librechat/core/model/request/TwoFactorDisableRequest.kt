package com.garfiec.librechat.core.model.request

import kotlinx.serialization.Serializable

@Serializable
data class TwoFactorDisableRequest(
    val token: String? = null,
    val backupCode: String? = null,
)
