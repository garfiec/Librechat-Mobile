package com.garfiec.librechat.feature.chat.prompts

import com.garfiec.librechat.feature.chat.prompts.components.PromptSortOrder

interface PromptsLibraryActions {
    fun loadGroups()

    fun refresh()

    fun selectGroup(groupId: String)

    fun retryPromptText()

    fun clearSelectedGroup()

    fun onCategorySelected(category: String?)

    fun onSortOrderChanged(sortOrder: PromptSortOrder)

    fun showFilterSheet()

    fun dismissFilterSheet()

    fun requestDeleteGroup(groupId: String)

    fun dismissDeleteGroup()

    fun confirmDeleteGroup()

    fun dismissError()

    fun showShareDialog(groupId: String)

    fun dismissShareDialog()

    fun showVariableDialog(promptTemplate: String)

    fun dismissVariableDialog()
}
