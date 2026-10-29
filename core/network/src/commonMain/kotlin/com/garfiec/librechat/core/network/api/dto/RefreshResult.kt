package com.garfiec.librechat.core.network.api.dto

import com.garfiec.librechat.core.model.auth.AuthCookies
import com.garfiec.librechat.core.model.response.RefreshResponse

data class RefreshResult(
    val response: RefreshResponse,
    /** The session's cookies after this response's Set-Cookie updates (rotated or retained). */
    val cookies: AuthCookies,
)
