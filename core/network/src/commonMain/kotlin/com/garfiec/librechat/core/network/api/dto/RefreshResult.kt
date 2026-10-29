package com.garfiec.librechat.core.network.api.dto

import com.garfiec.librechat.core.model.auth.AuthCookies
import com.garfiec.librechat.core.model.response.RefreshResponse

data class RefreshResult(
    val response: RefreshResponse,
    /** New refresh token from Set-Cookie header, if the backend rotated it. */
    val newRefreshToken: String?,
    val cookies: AuthCookies,
)
