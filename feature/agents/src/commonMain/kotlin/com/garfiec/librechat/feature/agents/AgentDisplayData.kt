package com.garfiec.librechat.feature.agents

import androidx.compose.runtime.Immutable

/** One marketplace card. [name] null = the agent has none; the card says so in the user's language. */
@Immutable
data class AgentCardDisplayData(
    val id: String,
    val name: String?,
    /** Plain text: an HTML description arrives with its markup stripped. */
    val description: String?,
    val avatarUrl: String?,
    /** The agent's category value ("hr", "general", an admin's own), labelled at render time. */
    val categoryValue: String? = null,
    /** The server's `is_promoted`: a Top pick. */
    val isPromoted: Boolean = false,
    /** Support contact's name (or email), else the owner's name. Never the raw author id. */
    val contactName: String? = null,
)

/** The agent page. Holds only what the server sent; `AgentDetailUiState.isFullRecord` says how much that was. */
@Immutable
data class AgentDetailDisplayData(
    val id: String,
    /** Null for an unnamed agent; the page says so in the user's language. */
    val name: String?,
    /** As written: plain text, or HTML the page renders. Null when blank. */
    val description: String?,
    val avatarUrl: String?,
    val model: String?,
    /** The agent's category value, labelled at render time. */
    val categoryValue: String?,
    /** The server's `is_promoted`: a Top pick. */
    val isPromoted: Boolean = false,
    val isPublic: Boolean = false,
    /** ISO timestamp of the last change. */
    val updatedAt: String? = null,
    val tools: List<String> = emptyList(),
    val conversationStarters: List<String> = emptyList(),
    /**
     * Who to reach about this agent — its declared support contact, or the owner's contact when
     * it declares none (v0.8.8). Null when neither exists.
     */
    val contact: AgentContactDisplayData? = null,
)

/**
 * A resolved agent contact.
 *
 * At least one of [name] / [email] is non-blank — an entry with neither says nothing and is
 * dropped at mapping time rather than rendered as an empty row.
 */
@Immutable
data class AgentContactDisplayData(
    val name: String?,
    val email: String?,
)

@Immutable
data class AgentHandoffDisplayData(
    val id: String,
    val name: String,
)

@Immutable
data class AgentActionDisplayData(
    val actionId: String?,
    val domain: String?,
    val type: String?,
    val authType: String?,
    val rawSpec: String?,
    val functionCount: Int,
)

@Immutable
data class AgentToolDisplayData(
    val toolId: String?,
    val name: String?,
    val description: String?,
    val icon: String?,
    val isAvailable: Boolean,
)
