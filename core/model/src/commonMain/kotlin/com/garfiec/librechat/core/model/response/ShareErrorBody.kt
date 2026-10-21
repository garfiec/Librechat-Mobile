package com.garfiec.librechat.core.model.response

import kotlinx.serialization.Serializable

/**
 * An error body from the `/api/share` routes. Two shapes share it:
 * - the share service's `{message, code?}`, where [code] names the failure (`SHARE_EXISTS`,
 *   `TARGET_MESSAGE_NOT_FOUND`, …) and is absent on a 500;
 * - the content filter's `{error, message, source, field}` (400, no `code`), where [error] is
 *   `content_filter_block` or `content_filter_uninspectable` and [message] is written to be shown.
 *
 * Several 404s (an expired conversation, an update of a link that is gone) have no body at all.
 */
@Serializable
data class ShareErrorBody(
    val message: String? = null,
    val code: String? = null,
    val error: String? = null,
    val source: String? = null,
    val field: String? = null,
)
