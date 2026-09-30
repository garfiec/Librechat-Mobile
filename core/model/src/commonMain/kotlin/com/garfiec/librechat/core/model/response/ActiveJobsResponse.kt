package com.garfiec.librechat.core.model.response

import kotlinx.serialization.Serializable

/**
 * `GET /api/agents/chat/active` — the caller's conversations with a live generation job. A job
 * paused for human review counts as active. The route predates every supported server (v0.8.4).
 */
@Serializable
data class ActiveJobsResponse(
    val activeJobIds: List<String> = emptyList(),
)
