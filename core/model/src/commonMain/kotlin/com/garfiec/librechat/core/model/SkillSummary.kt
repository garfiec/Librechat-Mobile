package com.garfiec.librechat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Summary view of a Skill (upstream `TSkillSummary` = `TSkill` minus body &
 * frontmatter). Returned by `GET /api/skills`. The agent-editor skills selector
 * only needs [id] (stored in `agent.skills`) and [name] / [displayTitle] to
 * render chips; the rest are optional so older servers and the agent editor's
 * fixtures still decode. The server may add fields at any time —
 * `ignoreUnknownKeys` covers that.
 *
 * [author] is the 24-hex ObjectId of the creator, compared against the current
 * user's `id ?: _id` for ownership. A deployment skill is marked by
 * [source] == `"deployment"`; the server keeps its own `deployment` flag internal.
 */
@Serializable
data class SkillSummary(
    @SerialName("_id") val id: String,
    val name: String,
    @SerialName("displayTitle") val displayTitle: String? = null,
    val description: String? = null,
    val category: String? = null,
    val author: String? = null,
    val authorName: String? = null,
    val fileCount: Int = 0,
    val isPublic: Boolean? = null,
    val alwaysApply: Boolean? = null,
    /** `"inline"` (the default when absent), `"deployment"`, or an external sync source. */
    val source: String? = null,
    val updatedAt: String? = null,
    val userInvocable: Boolean? = null,
)
