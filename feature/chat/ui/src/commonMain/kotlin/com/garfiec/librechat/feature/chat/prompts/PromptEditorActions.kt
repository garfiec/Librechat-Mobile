package com.garfiec.librechat.feature.chat.prompts

interface PromptEditorActions {
    fun updateName(name: String)

    fun updateOneliner(oneliner: String)

    fun updatePromptText(text: String)

    fun updateVariable(name: String, value: String)

    fun showVersionsSheet()

    fun hideVersionsSheet()

    fun setProductionTag(promptId: String)

    fun save()

    fun onBackRequested()

    fun discardChanges()

    fun dismissDiscardConfirmation()

    fun consumeSaved()

    fun dismissError()
}
