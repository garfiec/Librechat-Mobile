package com.garfiec.librechat.feature.conversations.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.search.MessageSearchHit
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Full message search: every hit grouped by conversation, title matches, and on a wide layout a
 * preview of the selected hit. With a hardware keyboard, ↑/↓ move the selection and Enter opens it.
 */
@Composable
fun SearchScreen(
    initialQuery: String,
    onNavigateBack: () -> Unit,
    onOpenMessage: (conversationId: String, messageId: String, query: String, inReasoning: Boolean) -> Unit,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = koinViewModel { parametersOf(initialQuery) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Open a hit only while the account it was found under is still active. A pattern's hit is found
    // in the chat by the text it matched, since the chat's find can't run a pattern.
    val open: (MessageSearchHit) -> Unit = { hit ->
        viewModel.openable(hit)?.let {
            // A regex hit carries the text its pattern matched, quoted so find takes it as one phrase.
            onOpenMessage(it.conversationId, it.messageId, it.findText?.let { text -> "\"$text\"" } ?: state.query.trim(), it.inReasoning)
        }
    }
    val openConversation: (String) -> Unit = { id -> viewModel.openableConversation(id)?.let(onOpenConversation) }
    val actions = remember(viewModel) {
        AdvancedActions(
            onOptionsChange = viewModel::onOptionsChange,
            onDateRangeChange = viewModel::onDateRangeChange,
            onGroupingChange = viewModel::onGroupingChange,
            onOpenFilters = viewModel::openFilters,
            onResetFilters = viewModel::resetFilters,
        )
    }
    SearchScreenContent(
        uiState = state,
        showBackButton = searchShowsBackButton,
        actions = actions,
        onNavigateBack = onNavigateBack,
        onQueryChange = viewModel::onQueryChange,
        onScopeChange = viewModel::onScopeChange,
        onOpenHit = open,
        onOpenConversation = openConversation,
        onSelectHit = viewModel::select,
        onSelectConversation = viewModel::selectConversation,
        onMoveSelection = viewModel::moveSelection,
        onToggleGroup = viewModel::toggleCollapsed,
        onScanMore = viewModel::scanMore,
        onEnsureSelection = viewModel::ensureSelection,
        onStepInConversation = viewModel::stepInConversation,
        onCloseFilters = viewModel::closeFilters,
        modifier = modifier,
    )
}
