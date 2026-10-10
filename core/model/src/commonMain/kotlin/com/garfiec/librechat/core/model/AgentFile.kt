package com.garfiec.librechat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Per-capability file attachment on an agent (code interpreter, file search, file context).
 * Loaded by parsing `tool_resources.<resource>.file_ids` from the Agent payload and
 * merging with `FileObject` metadata from `GET /api/files/agent/:id`.
 */
@Serializable
data class AgentFile(
    @SerialName("file_id") val fileId: String,
    val filename: String? = null,
    val bytes: Long? = null,
    val type: String? = null,
    /**
     * Storage path, from the upload response or the `GET /api/files/agent/:id` enrichment.
     *
     * `DELETE /api/files` filters out every entry with a falsy filepath before it even looks at
     * `agent_id`, so an unlink without one is not sent — it is dropped, answered 204, and reads as
     * a success. The agent payload carries only file_ids, which is why this has to be merged in.
     */
    val filepath: String? = null,
    /** Exact tool_resources sub-key this file was loaded from
     *  (`execute_code`, `file_search`, `context`, `ocr`). Required so that
     *  removeAgentFile can issue DELETE with the correct `tool_resource`
     *  form field — the Context slot's UI merges `context` and `ocr` files
     *  into one list, but the backend stores them separately. */
    @SerialName("origin_resource") val originResource: String? = null,
)
