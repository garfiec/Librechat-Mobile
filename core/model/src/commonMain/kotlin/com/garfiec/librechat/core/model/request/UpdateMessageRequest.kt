package com.garfiec.librechat.core.model.request

import kotlinx.serialization.Serializable

/**
 * Body of `PUT /api/messages/:conversationId/:messageId`. Flat, not `arg`-wrapped — the message
 * routes are the exception to the convention the convo mutations follow.
 *
 * The server branches on whether [index] arrived, and the two branches write different fields:
 * without one it updates the message's flat `text` and leaves `content[]` untouched; with one it
 * rewrites `content[index]`, deriving the part's type from what is stored (so there is no `type`
 * to send) and refusing anything that is not a TEXT or THINK part. A reply rendered from content
 * parts therefore needs the index — sending only `text` writes a field nothing displays, and the
 * edit reappears as the old text on the next fetch.
 */
@Serializable
data class UpdateMessageRequest(
    val text: String? = null,
    /**
     * Index into the message's `content` array, or null to edit its flat `text`. Omitted from the
     * body when null (`explicitNulls = false`), which is what selects the server's text branch.
     */
    val index: Int? = null,
)
