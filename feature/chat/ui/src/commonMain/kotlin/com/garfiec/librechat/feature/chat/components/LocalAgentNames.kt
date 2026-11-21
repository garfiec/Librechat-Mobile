package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.compositionLocalOf
import com.garfiec.librechat.core.model.Agent

/**
 * Agent id to name, provided by [ChatRootContent] from the agents the chat loaded. Empty where the reader
 * has no agents list (the shared-chat viewer): an agent then reads as the generic "Agent".
 */
val LocalAgentNames = compositionLocalOf<Map<String, String>> { emptyMap() }

fun List<Agent>.namesById(): Map<String, String> =
    mapNotNull { agent -> agent.name?.takeIf { it.isNotBlank() }?.let { agent.id to it } }.toMap()
