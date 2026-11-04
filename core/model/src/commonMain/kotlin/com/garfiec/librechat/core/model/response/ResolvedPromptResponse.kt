package com.garfiec.librechat.core.model.response

import kotlinx.serialization.Serializable

/**
 * `GET /api/prompts/groups/:groupId/resolve` (v0.8.9-rc1): a group's live text. An externally
 * managed group (Langfuse) stores no content of its own, so this is the only place its text comes
 * from. [version] and [labels] are the Langfuse revision read; `sourceUrl` is admin-only and unread.
 */
@Serializable
data class ResolvedPromptResponse(
    val source: String? = null,
    val groupId: String? = null,
    val prompt: String = "",
    val type: String? = null,
    val version: Int? = null,
    val labels: List<String> = emptyList(),
)
