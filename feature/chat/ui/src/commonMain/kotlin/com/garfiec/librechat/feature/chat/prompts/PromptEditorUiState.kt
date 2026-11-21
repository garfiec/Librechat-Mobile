package com.garfiec.librechat.feature.chat.prompts

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.Prompt

@Immutable
data class PromptEditorUiState(
    val groupId: String? = null,
    val name: String = "",
    val oneliner: String = "",
    val promptText: String = "",
    val variableValues: Map<String, String> = emptyMap(),
    val prompts: List<Prompt> = emptyList(),
    val productionId: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val showVersionsSheet: Boolean = false,
    /** The saved fields as the group was loaded (blank for create). */
    val loadedFields: PromptFields = PromptFields(),
    val showDiscardConfirm: Boolean = false,
    val exitRequested: Boolean = false,
) {
    val isNewPrompt: Boolean get() = groupId == null

    /**
     * Whether Save has anything to write. [command] is passed in because it lives in a
     * `TextFieldState` rather than in this state; the variable values only fill the preview and are
     * never saved, so they are left out.
     */
    fun hasUnsavedChanges(command: String): Boolean =
        !isLoading && PromptFields(name, oneliner, promptText, command) != loadedFields
}

/** The fields a save writes. */
@Immutable
data class PromptFields(
    val name: String = "",
    val oneliner: String = "",
    val promptText: String = "",
    val command: String = "",
)
