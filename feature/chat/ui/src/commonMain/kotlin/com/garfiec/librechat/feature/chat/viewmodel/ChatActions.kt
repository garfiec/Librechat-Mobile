package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.model.ArtifactsMode
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.MinimalFeedback
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.preferences.DuringRunAction
import com.garfiec.librechat.core.model.request.ToolApprovalResolution
import com.garfiec.librechat.core.model.response.UploadRoute
import com.garfiec.librechat.core.ui.components.ModelParameters
import com.garfiec.librechat.core.ui.media.MediaPreviewState
import com.garfiec.librechat.feature.chat.components.AttachedFile
import com.garfiec.librechat.feature.chat.components.PausePanelAutoAdvance
import com.garfiec.librechat.feature.chat.model.ComposerPickerItem
import com.garfiec.librechat.feature.chat.model.PresetDisplayData
import com.garfiec.librechat.feature.chat.util.AskAnswerDraft
import com.garfiec.librechat.feature.chat.util.DraftItem
import com.garfiec.librechat.feature.chat.util.SettingChip
import com.garfiec.librechat.feature.chat.util.ToolDecisionDraft
import com.garfiec.librechat.feature.chat.util.ToolPartIdentity
import com.garfiec.librechat.feature.chat.util.ToolPartKey

@Suppress("TooManyFunctions")
interface ChatActions {
    fun switchBranch(parentMessageId: String, siblingIndex: Int)

    fun onInputChanged(text: String)

    fun sendMessage()

    fun sendAppMessage(text: String): Boolean

    fun queueMessage()

    fun sendDuringRun()

    fun steerMessage(interrupt: Boolean = false)

    fun interruptMessage()

    fun cancelSteer(steerId: String)

    fun armSteer(steerId: String)

    fun setShowSiteIcons(show: Boolean)

    fun setCompareSyncScroll(enabled: Boolean)

    fun dismissSiteIconPrompt()

    fun setDuringRunAction(action: DuringRunAction)

    fun regenerateMessage(messageId: String)

    fun retryWithoutFiles(failedReplyId: String)

    fun retryOwnerTexts()

    fun branchWithoutFiles(failedReplyId: String)

    fun copyMessage(messageId: String, write: (String) -> Unit)

    fun requestToolPart(key: ToolPartKey)

    fun retainToolPart(identity: ToolPartIdentity)

    fun releaseToolPart(identity: ToolPartIdentity)

    fun stopGeneration()

    fun resolveToolApproval(decisions: List<ToolApprovalResolution>)

    fun answerPendingQuestion(answer: String)

    fun answerPendingQuestions(answers: Map<String, String>)

    fun updateAskAnswerDraft(questionId: String, draft: AskAnswerDraft)

    fun selectAskQuestion(questionId: String)

    fun setAskPanelCollapsed(collapsed: Boolean)

    fun updateToolDecisionDraft(toolCallId: String, draft: ToolDecisionDraft)

    fun selectToolCall(toolCallId: String)

    fun setToolPanelCollapsed(collapsed: Boolean)

    fun pickAskOption(questionId: String, draft: AskAnswerDraft, advance: PausePanelAutoAdvance)

    fun pickToolDecision(toolCallId: String, draft: ToolDecisionDraft, advance: PausePanelAutoAdvance)

    fun cancelPausePanelAutoAdvance()

    fun continueGeneration()

    fun compactConversation()

    fun snoozeCompactNudge()

    fun refreshPromptsIfStale()

    fun retrySkillCatalog()

    fun submitFeedback(messageId: String, feedback: MinimalFeedback?)

    fun startEditing(messageId: String)

    fun onEditTextChanged(text: String)

    fun onEditPartTextChanged(index: Int, text: String)

    fun cancelEditing()

    fun rerunEdit()

    fun saveEdit()

    fun toggleTemporaryChat()

    fun refreshMessages()

    fun openModelSheet()

    fun prepareModelSelector()

    fun dismissModelSheet()

    suspend fun downloadFileBytes(attachment: Attachment): ByteArray?

    fun dismissError()

    fun openCodeWorkspaceSheet(machineId: String? = null)

    fun dismissCodeWorkspaceSheet()

    fun selectCodeWorkspace(environmentId: String, workspaceId: String)

    fun setCodeCheckout(environmentId: String, mode: CodeCheckoutMode)

    fun selectNoCodeWorkspace()

    fun refreshCodeWorkspaces()

    fun selectTransitionCodeWorkspace(environmentId: String, workspaceId: String)

    fun setTransitionCodeCheckout(environmentId: String, mode: CodeCheckoutMode)

    fun confirmCodeWorkspaceTransition()

    fun detachCodeWorkspace()

    fun retryCodeWorkspaceCheck()

    fun selectCodeApprovalMode(option: CodeApprovalOption)

    fun retryPullRequest()

    fun openPullRequestSheet()

    fun dismissPullRequestSheet()

    fun markPullRequestSeen()

    fun dismissSendBlockReason()

    fun openMedia(url: String)

    fun openDraftMedia(preview: MediaPreviewState)

    fun closeMedia()

    fun openSearch()

    fun closeSearch()

    fun onSearchQueryChanged(query: String)

    fun nextSearchMatch()

    fun previousSearchMatch()

    fun onSearchScrollHandled()

    fun showRenameDialog()

    fun dismissRenameDialog()

    fun renameConversation(newTitle: String)

    fun showDeleteConfirmation()

    fun dismissDeleteConfirmation()

    fun deleteConversation()

    fun archiveConversation()

    fun duplicateConversation()

    fun shareConversation()

    fun openBookmarks()

    fun showForkOptions(messageId: String)

    fun readAloud(messageId: String)

    fun stopRecording()

    fun onFilesSelected(platformRefs: List<Any>)

    fun onCodeFilesSelected(platformRefs: List<Any>)

    fun setPendingUploadRoute(index: Int, route: UploadRoute)

    fun setAllPendingUploadRoutes(route: UploadRoute)

    fun confirmPendingUploadRouting()

    fun cancelPendingUploadRouting()

    fun cancelPendingUploadSend()

    fun retryUpload(file: AttachedFile)

    fun submitPdfPassword(password: String)

    fun dismissPdfPassword()

    fun addPendingQuote(text: String)

    fun removeDraftItem(item: DraftItem)

    fun undoDraftRemoval(id: Long)

    fun dropDraftUndo(id: Long)

    fun selectThinkingLevel(value: String)

    fun editQueued(localId: String)

    fun commitQueuedEdit()

    fun cancelQueuedEdit()

    fun cancelQueued(localId: String)

    fun reorderQueue(fromIndex: Int, toIndex: Int)

    fun sendQueuedNow()

    fun savePreset(name: String)

    fun loadPreset(displayData: PresetDisplayData)

    fun reloadPresetsIfFailed()

    fun deletePreset(presetId: String)

    fun selectPickerItem(item: ComposerPickerItem)

    fun consumePendingPromptInsertion()

    fun sendStarter(text: String)

    fun editStarter(text: String)

    fun confirmVariablePrompt(interpolated: String)

    fun dismissVariablePrompt()

    fun toggleAgentFavorite(agentId: String)

    fun toggleModelFavorite(endpoint: String, model: String)

    fun toggleSpecFavorite(name: String)

    fun onSpecSelected(spec: ModelSpec)

    fun onModelSelected(endpoint: String, model: String)

    fun beginComparisonSetup()

    fun cancelComparisonSetup()

    fun stopComparison()

    fun setSecondarySpec(spec: ModelSpec)

    fun setSecondaryModel(endpoint: String, model: String)

    fun toggleMcpServer(serverName: String)

    fun signInMcpServer(
        serverName: String,
        openSignIn: (serverName: String, authorizationUrl: String) -> Boolean,
        onFailed: () -> Unit,
        selectWhenConnected: Boolean = false,
    )

    fun cancelMcpSignIn(serverName: String, onFailed: () -> Unit)

    fun onMcpSignInEnded()

    suspend fun mcpVariableFlags(serverName: String): Map<String, Boolean>?

    suspend fun saveMcpVariables(serverName: String, values: Map<String, String>): Boolean

    suspend fun removeMcpVariables(serverName: String): Boolean

    fun setArtifactsMode(mode: ArtifactsMode)

    fun toggleTool(toolName: String)

    suspend fun saveWebSearchKeys(auth: Map<String, String>): Boolean

    suspend fun removeWebSearchKeys(): Boolean

    fun toggleSettingChip(chip: SettingChip)

    fun updateModelParameters(parameters: ModelParameters)

    fun branchFromComparison(agentId: String)

    fun keepAndContinue(agentId: String)

    fun setContextGaugeExpanded(expanded: Boolean)

    fun startComparison(endpoint: String, model: String, spec: String? = null, primary: ComparisonPick? = null)
}
