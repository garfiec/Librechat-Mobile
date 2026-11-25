package com.garfiec.librechat.core.model.response

import kotlinx.serialization.Serializable

/**
 * `GET /api/prompts/groups/:groupId/revisions` (v0.8.9): the revisions an agent's linked
 * instructions may pin, metadata only. [revisions] has no default, so a 200 without it fails to
 * decode and reads as a load failure rather than an empty list.
 */
@Serializable
data class PromptRevisionChoices(
    val source: String,
    val groupId: String,
    val revisions: List<PromptRevisionChoice>,
)

/**
 * One revision: a native one by [promptId], with [isProduction] marking the group's production
 * revision; a Langfuse one by [version] alone, its production resolved live.
 */
@Serializable
data class PromptRevisionChoice(
    val source: String,
    val version: Int,
    val promptId: String? = null,
    val isProduction: Boolean = false,
)
