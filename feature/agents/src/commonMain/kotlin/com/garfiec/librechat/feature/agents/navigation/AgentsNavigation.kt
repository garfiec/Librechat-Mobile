package com.garfiec.librechat.feature.agents.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.agents.screen.AgentDetailScreen
import com.garfiec.librechat.feature.agents.screen.AgentEditorScreen
import com.garfiec.librechat.feature.agents.screen.AgentMarketplaceScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable sealed interface AgentsRoute : NavKey

@Serializable data object AgentMarketplace : AgentsRoute

@Serializable data class AgentDetail(val agentId: String) : AgentsRoute

@Serializable data object AgentEditorCreate : AgentsRoute

@Serializable data class AgentEditorEdit(val agentId: String) : AgentsRoute

/**
 * @param onReplace swaps the top entry for a route, so a finished editor doesn't stay under the
 *   page it opened.
 * @param onPopWhile pops entries while the predicate holds for the top one, never the last entry.
 */
fun EntryProviderScope<NavKey>.agentsEntries(
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    onReplace: (NavKey) -> Unit,
    onPopWhile: ((NavKey) -> Boolean) -> Unit,
    onStartChat: (String) -> Unit,
) {
    entry<AgentMarketplace> {
        AgentMarketplaceScreen(
            onAgentClick = { agentId -> onNavigate(AgentDetail(agentId = agentId)) },
            onCreateAgent = { onNavigate(AgentEditorCreate) },
            onBack = onBack,
        )
    }
    entry<AgentDetail> { key ->
        AgentDetailScreen(
            onBack = onBack,
            onStartChat = onStartChat,
            onEdit = { agentId -> onNavigate(AgentEditorEdit(agentId = agentId)) },
            onDuplicate = { agentId -> onNavigate(AgentDetail(agentId = agentId)) },
            agentId = key.agentId,
        )
    }
    entry<AgentEditorCreate> {
        AgentEditorScreen(
            onBack = onBack,
            onSave = { agentId -> onReplace(AgentDetail(agentId = agentId)) },
            agentId = null,
        )
    }
    entry<AgentEditorEdit> { key ->
        val deletedDetail = AgentDetail(agentId = key.agentId)
        AgentEditorScreen(
            // The editor is opened from the agent's detail page, which is still underneath.
            onBack = onBack,
            onSave = { onBack() },
            onDuplicate = { agentId -> onReplace(AgentDetail(agentId = agentId)) },
            onDelete = { onPopWhile { it == key || it == deletedDetail } },
            agentId = key.agentId,
        )
    }
}

val agentsSerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(AgentMarketplace::class, AgentMarketplace.serializer())
        subclass(AgentDetail::class, AgentDetail.serializer())
        subclass(AgentEditorCreate::class, AgentEditorCreate.serializer())
        subclass(AgentEditorEdit::class, AgentEditorEdit.serializer())
    }
}
