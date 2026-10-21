package com.garfiec.librechat.core.model.response

import com.garfiec.librechat.core.model.Agent
import kotlinx.serialization.Serializable

/**
 * `POST /api/agents/:id/duplicate` answers `201 {agent, actions}` (upstream `duplicateAgentHandler`).
 * Only the copy is read: `actions` has no reader, and decoding it could fail the call after the
 * server has already made the copy.
 */
@Serializable
data class DuplicateAgentResponse(
    val agent: Agent,
)
