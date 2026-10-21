package com.garfiec.librechat.core.model.request

import kotlinx.serialization.Serializable

/**
 * Body of `POST /api/share/:conversationId`.
 *
 * Null fields are omitted on the wire, and omitted is not a value: the route 400s on a
 * [targetMessageId] that isn't a non-empty string, and reads an absent [snapshotFiles] as
 * "include files" (`requested !== false`, ANDed with the server's own snapshot setting).
 *
 * @param targetMessageId the branch tail to share up to; omitted shares the newest branch.
 */
@Serializable
data class CreateShareRequest(
    val targetMessageId: String? = null,
    val snapshotFiles: Boolean? = null,
)

/**
 * Body of `PATCH /api/share/:shareId`.
 *
 * The route re-derives the link's file choice from this body on every update (`requested !==
 * false`), so an omitted [snapshotFiles] turns files back on for a link that had them off.
 * Callers must send the link's stored choice, or the user's new one.
 */
@Serializable
data class UpdateShareRequest(
    val targetMessageId: String? = null,
    val snapshotFiles: Boolean? = null,
)
