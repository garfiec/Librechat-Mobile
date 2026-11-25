package com.garfiec.librechat.feature.chat.prompts

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.feature.chat.prompts.components.PromptSortOrder

@Immutable
data class PromptsUiState(
    val groups: List<PromptGroupDisplayData> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val selectedGroup: PromptGroupDetailDisplayData? = null,
    // Filter and sort state
    val selectedCategory: String? = null,
    val sortOrder: PromptSortOrder = PromptSortOrder.RECENT,
    val availableCategories: List<String> = emptyList(),
    val showFilterSheet: Boolean = false,
    val filteredGroups: List<PromptGroupDisplayData> = emptyList(),
    // Share state
    val showShareDialog: Boolean = false,
    val shareGroupId: String? = null,
    // Variable dialog state
    val showVariableDialog: Boolean = false,
    val variablePromptTemplate: String = "",
    val variableNames: List<String> = emptyList(),
    val pendingDeleteGroupId: String? = null,
    val isDeleting: Boolean = false,
    // Role-permission gates — default permissive.
    val promptsCreateEnabled: Boolean = true,
    val promptsShareEnabled: Boolean = true,
)
