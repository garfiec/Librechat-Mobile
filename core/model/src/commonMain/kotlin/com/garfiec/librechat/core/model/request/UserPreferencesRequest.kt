package com.garfiec.librechat.core.model.request

import kotlinx.serialization.Serializable

/** `PATCH /api/user/preferences`: the default stateful workspace scope new agents get. */
@Serializable
data class UserPreferencesRequest(val statefulCodeEnvironment: String)
