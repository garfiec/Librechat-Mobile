package com.garfiec.librechat.feature.settings.model

import androidx.compose.runtime.Immutable

@Immutable
data class UserDisplayData(
    val name: String,
    val email: String,
    val username: String,
    val avatar: String?,
    /** `local`, `ldap` or an identity provider; decides whether enforced 2FA applies. */
    val provider: String? = null,
)
