package com.garfiec.librechat.feature.agents.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.ui.platform.rememberClipboardWriter
import com.garfiec.librechat.feature.agents.resources.*
import com.garfiec.librechat.feature.agents.resources.Res
import com.garfiec.librechat.feature.agents.viewmodel.AgentDetailEvent
import com.garfiec.librechat.feature.agents.viewmodel.AgentDetailViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AgentDetailScreen(
    onBack: () -> Unit,
    onStartChat: (String) -> Unit,
    onStartChatWithPrompt: (agentId: String, prompt: String) -> Unit,
    onEdit: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    agentId: String? = null,
    viewModel: AgentDetailViewModel = koinViewModel { parametersOf(agentId) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnDuplicated by rememberUpdatedState(onDuplicate)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AgentDetailEvent.Deleted -> currentOnClose()
                is AgentDetailEvent.Duplicated -> currentOnDuplicated(event.agentId)
            }
        }
    }

    val clipboard = rememberClipboardWriter()
    val linkLabel = stringResource(Res.string.agent_copy_link)
    AgentDetailContent(
        uiState = uiState,
        actions = AgentDetailActions(
            onBack = onBack,
            onStartChat = { id -> onStartChat(id) },
            onStartChatWithPrompt = { id, prompt -> onStartChatWithPrompt(id, prompt) },
            onEdit = onEdit,
            onCopyLink = { clipboard.copy(viewModel.chatLink(), linkLabel) },
            onTogglePin = viewModel::togglePin,
            onDuplicate = viewModel::duplicateAgent,
            onDelete = viewModel::showDeleteConfirmation,
            onConfirmDelete = viewModel::deleteAgent,
            onDismissDelete = viewModel::dismissDeleteConfirmation,
            onRetry = viewModel::loadAgent,
            onDismissActionError = viewModel::dismissActionError,
            onResetApprovals = viewModel::resetApprovals,
            onDismissApprovalReset = viewModel::dismissApprovalResetNotice,
        ),
        modifier = modifier,
    )
}
