package com.garfiec.librechat.web.demo

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
import com.garfiec.librechat.feature.chat.viewmodel.ChatActions
import com.garfiec.librechat.feature.chat.viewmodel.CodeApprovalOption
import com.garfiec.librechat.feature.chat.viewmodel.CodeCheckoutMode
import com.garfiec.librechat.feature.chat.viewmodel.ComparisonPick

/**
 * Every [ChatActions] member doing nothing, for the demo to override the ones it acts on. A member added
 * to [ChatActions] fails the demo build until it gets a line here.
 */
@Suppress("TooManyFunctions", "EmptyFunctionBlock")
internal open class NoOpChatActions : ChatActions {
    override fun switchBranch(parentMessageId: String, siblingIndex: Int) {}
    override fun onInputChanged(text: String) {}
    override fun sendMessage() {}
    override fun sendAppMessage(text: String): Boolean = false
    override fun queueMessage() {}
    override fun sendDuringRun() {}
    override fun steerMessage(interrupt: Boolean) {}
    override fun interruptMessage() {}
    override fun cancelSteer(steerId: String) {}
    override fun armSteer(steerId: String) {}
    override fun setShowSiteIcons(show: Boolean) {}
    override fun setCompareSyncScroll(enabled: Boolean) {}
    override fun dismissSiteIconPrompt() {}
    override fun setDuringRunAction(action: DuringRunAction) {}
    override fun regenerateMessage(messageId: String) {}
    override fun retryWithoutFiles(failedReplyId: String) {}
    override fun retryOwnerTexts() {}
    override fun branchWithoutFiles(failedReplyId: String) {}
    override fun copyMessage(messageId: String, write: (String) -> Unit) {}
    override fun requestToolPart(key: ToolPartKey) {}
    override fun retainToolPart(identity: ToolPartIdentity) {}
    override fun releaseToolPart(identity: ToolPartIdentity) {}
    override fun stopGeneration() {}
    override fun resolveToolApproval(decisions: List<ToolApprovalResolution>) {}
    override fun answerPendingQuestion(answer: String) {}
    override fun answerPendingQuestions(answers: Map<String, String>) {}
    override fun updateAskAnswerDraft(questionId: String, draft: AskAnswerDraft) {}
    override fun selectAskQuestion(questionId: String) {}
    override fun setAskPanelCollapsed(collapsed: Boolean) {}
    override fun updateToolDecisionDraft(toolCallId: String, draft: ToolDecisionDraft) {}
    override fun selectToolCall(toolCallId: String) {}
    override fun setToolPanelCollapsed(collapsed: Boolean) {}
    override fun pickAskOption(questionId: String, draft: AskAnswerDraft, advance: PausePanelAutoAdvance) {}
    override fun pickToolDecision(toolCallId: String, draft: ToolDecisionDraft, advance: PausePanelAutoAdvance) {}
    override fun cancelPausePanelAutoAdvance() {}
    override fun continueGeneration() {}
    override fun compactConversation() {}
    override fun snoozeCompactNudge() {}
    override fun refreshPromptsIfStale() {}
    override fun retrySkillCatalog() {}
    override fun submitFeedback(messageId: String, feedback: MinimalFeedback?) {}
    override fun startEditing(messageId: String) {}
    override fun onEditTextChanged(text: String) {}
    override fun onEditPartTextChanged(index: Int, text: String) {}
    override fun cancelEditing() {}
    override fun rerunEdit() {}
    override fun saveEdit() {}
    override fun toggleTemporaryChat() {}
    override fun refreshMessages() {}
    override fun openModelSheet() {}
    override fun prepareModelSelector() {}
    override fun dismissModelSheet() {}
    override suspend fun downloadFileBytes(attachment: Attachment): ByteArray? = null
    override fun dismissError() {}
    override fun openCodeWorkspaceSheet(machineId: String?) {}
    override fun dismissCodeWorkspaceSheet() {}
    override fun selectCodeWorkspace(environmentId: String, workspaceId: String) {}
    override fun setCodeCheckout(environmentId: String, mode: CodeCheckoutMode) {}
    override fun selectNoCodeWorkspace() {}
    override fun refreshCodeWorkspaces() {}
    override fun selectTransitionCodeWorkspace(environmentId: String, workspaceId: String) {}
    override fun setTransitionCodeCheckout(environmentId: String, mode: CodeCheckoutMode) {}
    override fun confirmCodeWorkspaceTransition() {}
    override fun detachCodeWorkspace() {}
    override fun retryCodeWorkspaceCheck() {}
    override fun selectCodeApprovalMode(option: CodeApprovalOption) {}
    override fun retryPullRequest() {}
    override fun openPullRequestSheet() {}
    override fun dismissPullRequestSheet() {}
    override fun markPullRequestSeen() {}
    override fun dismissSendBlockReason() {}
    override fun openMedia(url: String) {}
    override fun openDraftMedia(preview: MediaPreviewState) {}
    override fun closeMedia() {}
    override fun openSearch() {}
    override fun closeSearch() {}
    override fun onSearchQueryChanged(query: String) {}
    override fun nextSearchMatch() {}
    override fun previousSearchMatch() {}
    override fun onSearchScrollHandled() {}
    override fun showRenameDialog() {}
    override fun dismissRenameDialog() {}
    override fun renameConversation(newTitle: String) {}
    override fun showDeleteConfirmation() {}
    override fun dismissDeleteConfirmation() {}
    override fun deleteConversation() {}
    override fun archiveConversation() {}
    override fun duplicateConversation() {}
    override fun shareConversation() {}
    override fun openBookmarks() {}
    override fun showForkOptions(messageId: String) {}
    override fun readAloud(messageId: String) {}
    override fun stopRecording() {}
    override fun onFilesSelected(platformRefs: List<Any>) {}
    override fun onCodeFilesSelected(platformRefs: List<Any>) {}
    override fun setPendingUploadRoute(index: Int, route: UploadRoute) {}
    override fun setAllPendingUploadRoutes(route: UploadRoute) {}
    override fun confirmPendingUploadRouting() {}
    override fun cancelPendingUploadRouting() {}
    override fun cancelPendingUploadSend() {}
    override fun retryUpload(file: AttachedFile) {}
    override fun submitPdfPassword(password: String) {}
    override fun dismissPdfPassword() {}
    override fun addPendingQuote(text: String) {}
    override fun removeDraftItem(item: DraftItem) {}
    override fun undoDraftRemoval(id: Long) {}
    override fun dropDraftUndo(id: Long) {}
    override fun selectThinkingLevel(value: String) {}
    override fun editQueued(localId: String) {}
    override fun commitQueuedEdit() {}
    override fun cancelQueuedEdit() {}
    override fun cancelQueued(localId: String) {}
    override fun reorderQueue(fromIndex: Int, toIndex: Int) {}
    override fun sendQueuedNow() {}
    override fun savePreset(name: String) {}
    override fun loadPreset(displayData: PresetDisplayData) {}
    override fun reloadPresetsIfFailed() {}
    override fun deletePreset(presetId: String) {}
    override fun selectPickerItem(item: ComposerPickerItem) {}
    override fun consumePendingPromptInsertion() {}
    override fun sendStarter(text: String) {}
    override fun editStarter(text: String) {}
    override fun confirmVariablePrompt(interpolated: String) {}
    override fun dismissVariablePrompt() {}
    override fun toggleAgentFavorite(agentId: String) {}
    override fun toggleModelFavorite(endpoint: String, model: String) {}
    override fun toggleSpecFavorite(name: String) {}
    override fun onSpecSelected(spec: ModelSpec) {}
    override fun onModelSelected(endpoint: String, model: String) {}
    override fun beginComparisonSetup() {}
    override fun cancelComparisonSetup() {}
    override fun stopComparison() {}
    override fun setSecondarySpec(spec: ModelSpec) {}
    override fun setSecondaryModel(endpoint: String, model: String) {}
    override fun toggleMcpServer(serverName: String) {}
    override fun signInMcpServer(serverName: String, openSignIn: (serverName: String, authorizationUrl: String) -> Boolean, onFailed: () -> Unit, selectWhenConnected: Boolean) {}
    override fun cancelMcpSignIn(serverName: String, onFailed: () -> Unit) {}
    override fun onMcpSignInEnded() {}
    override suspend fun mcpVariableFlags(serverName: String): Map<String, Boolean>? = null
    override suspend fun saveMcpVariables(serverName: String, values: Map<String, String>): Boolean = false
    override suspend fun removeMcpVariables(serverName: String): Boolean = false
    override fun setArtifactsMode(mode: ArtifactsMode) {}
    override fun toggleTool(toolName: String) {}
    override suspend fun saveWebSearchKeys(auth: Map<String, String>): Boolean = false
    override suspend fun removeWebSearchKeys(): Boolean = false
    override fun toggleSettingChip(chip: SettingChip) {}
    override fun updateModelParameters(parameters: ModelParameters) {}
    override fun branchFromComparison(agentId: String) {}
    override fun keepAndContinue(agentId: String) {}
    override fun setContextGaugeExpanded(expanded: Boolean) {}
    override fun startComparison(endpoint: String, model: String, spec: String?, primary: ComparisonPick?) {}
}
