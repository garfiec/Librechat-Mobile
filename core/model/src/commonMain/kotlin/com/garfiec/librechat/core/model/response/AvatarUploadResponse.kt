package com.garfiec.librechat.core.model.response

import kotlinx.serialization.Serializable

/**
 * Response of `POST /api/files/images/avatar`. [url] is what the server also wrote to the user's
 * `avatar` field: a relative `/images/<userId>/avatar-<ts>.<ext>?manual=true` under local storage, or
 * an absolute URL under S3/Firebase/Azure.
 */
@Serializable
data class AvatarUploadResponse(
    val url: String,
)
