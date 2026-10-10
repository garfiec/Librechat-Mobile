package com.garfiec.librechat.core.model

import kotlinx.serialization.Serializable

/**
 * Commit author and committer identity the agent's sandbox git tooling uses (v0.8.8-rc2).
 *
 * Round-trip only — mobile has no editor for it, and the standing policy for new agent fields is
 * to preserve rather than surface, so an edit made here cannot drop what an admin set on the web.
 * Both halves are nullable despite being required upstream: a partial row must decode rather than
 * fail the agent it rides on.
 */
@Serializable
data class AgentGitIdentity(
    val name: String? = null,
    val email: String? = null,
)
