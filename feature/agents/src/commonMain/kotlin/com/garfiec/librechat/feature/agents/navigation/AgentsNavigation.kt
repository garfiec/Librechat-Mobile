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

fun EntryProviderScope<NavKey>.agentsEntries(
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    /** Swaps the first route for the second only while the first is on top (see Navigator.replace). */
    onReplace: (route: NavKey, replacement: NavKey) -> Unit,
    /** Removes a screen that closes itself, wherever it sits, without popping whatever is on top. */
    onRemove: (NavKey) -> Unit,
    onPopWhile: ((NavKey) -> Boolean) -> Unit,
    onStartChat: (String) -> Unit,
    onStartChatWithPrompt: (agentId: String, prompt: String) -> Unit,
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
            onStartChatWithPrompt = onStartChatWithPrompt,
            onEdit = { agentId -> onNavigate(AgentEditorEdit(agentId = agentId)) },
            // The copy's page takes this one's place, as Duplicate in the editor does: back from
            // the copy returns to where the original was opened from, not to the original. Both
            // results can land after the user has backed out (the page stays composed for its exit
            // animation), so they act on this entry only, never on whatever is on top by then.
            onDuplicate = { agentId -> onReplace(key, AgentDetail(agentId = agentId)) },
            onClose = { onRemove(key) },
            agentId = key.agentId,
        )
    }
    entry<AgentEditorCreate> { key ->
        AgentEditorScreen(
            onBack = onBack,
            onSave = { agentId -> onReplace(key, AgentDetail(agentId = agentId)) },
            agentId = null,
        )
    }
    entry<AgentEditorEdit> { key ->
        val deletedDetail = AgentDetail(agentId = key.agentId)
        AgentEditorScreen(
            onBack = onBack,
            // The editor is opened from the agent's detail page, which is still underneath.
            onSave = { onBack() },
            onDuplicate = { agentId -> onReplace(key, AgentDetail(agentId = agentId)) },
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
