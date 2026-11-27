package com.garfiec.librechat.web.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.datetime.DateTimeFormatPrefs
import com.garfiec.librechat.core.model.AskUserQuestionRequest
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.PendingAction
import com.garfiec.librechat.core.model.PendingActionPayload
import com.garfiec.librechat.core.model.PendingActionTypes
import com.garfiec.librechat.feature.chat.components.PAUSE_PANEL_AUTO_ADVANCE_DELAY_MS
import com.garfiec.librechat.feature.chat.components.PausePanelAutoAdvance
import com.garfiec.librechat.feature.chat.components.pausePanelActiveIndex
import com.garfiec.librechat.feature.chat.util.AskAnswerDraft
import com.garfiec.librechat.feature.chat.util.composeAskAnswer
import com.garfiec.librechat.feature.chat.util.nextBlankQuestionIndex
import com.garfiec.librechat.core.model.Feedback
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.MinimalFeedback
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.core.model.request.NO_PARENT
import com.garfiec.librechat.core.ui.components.pressBounce
import com.garfiec.librechat.core.ui.components.rememberPressBounce
import com.garfiec.librechat.core.ui.datetime.ProvideDateTimeFormat
import com.garfiec.librechat.core.ui.theme.isLiquidGlass
import com.garfiec.librechat.feature.chat.components.BoxButtonSize
import com.garfiec.librechat.feature.chat.components.ChatAttachmentActions
import com.garfiec.librechat.feature.chat.components.ChatFloatingTopBarContent
import com.garfiec.librechat.feature.chat.components.ChatInputDefaults
import com.garfiec.librechat.feature.chat.components.ChatInputPlaceholder
import com.garfiec.librechat.feature.chat.components.ChatInputState
import com.garfiec.librechat.feature.chat.components.CodeRunHost
import com.garfiec.librechat.feature.chat.components.CodeRunsState
import com.garfiec.librechat.feature.chat.components.CommonChatInputCore
import com.garfiec.librechat.feature.chat.components.FloatingBarIconButton
import com.garfiec.librechat.feature.chat.components.McpSignIn
import com.garfiec.librechat.feature.chat.components.ParsedMarkdownCache
import com.garfiec.librechat.feature.chat.components.ToolsButtonDots
import com.garfiec.librechat.feature.chat.components.VoiceMicIndicator
import com.garfiec.librechat.feature.chat.components.artifact.MermaidRenderCache
import com.garfiec.librechat.feature.chat.components.countMessageOccurrences
import com.garfiec.librechat.feature.chat.components.occurrencesInReasoning
import com.garfiec.librechat.feature.chat.screen.ChatComposerParams
import com.garfiec.librechat.feature.chat.screen.ChatScreenContent
import com.garfiec.librechat.feature.chat.screen.ChatScreenSlots
import com.garfiec.librechat.feature.chat.screen.ChatTopBarParams
import com.garfiec.librechat.feature.chat.util.buildActiveMessagePath
import com.garfiec.librechat.feature.chat.util.collapseParallelToPrimary
import com.garfiec.librechat.feature.chat.util.isAddedAgentId
import com.garfiec.librechat.feature.chat.viewmodel.ChatPreferences
import com.garfiec.librechat.feature.chat.viewmodel.ChatScreenState
import com.garfiec.librechat.feature.chat.viewmodel.ChatSearchState
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ComparisonPick
import com.garfiec.librechat.feature.chat.viewmodel.ComparisonState
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMetaState
import com.garfiec.librechat.feature.chat.viewmodel.FeatureGatesState
import com.garfiec.librechat.feature.chat.viewmodel.JumpFlash
import com.garfiec.librechat.feature.chat.viewmodel.MessagesState
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
import com.garfiec.librechat.feature.chat.viewmodel.SearchFocusRequest
import com.garfiec.librechat.feature.chat.viewmodel.SearchMatch
import com.garfiec.librechat.feature.chat.viewmodel.VoiceState
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The app's chat screen, `ChatScreenContent`, for one sample conversation. [DemoChatSession] stands in
 * for the ViewModel: it holds the screen's state and streams a canned reply back to a message the
 * visitor sends. A [conversationId] with no sample transcript opens a new chat. [openDrawer] is the
 * bar's menu button, which opens the drawer or toggles the sidebar; [onBack] adds a back arrow to the
 * bar, which the app leaves to the system back. [jump] opens the chat at a search hit.
 */
@Composable
fun ChatDemo(
    conversationId: String,
    openDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    jump: ChatJump? = null,
) {
    val scope = rememberCoroutineScope()
    val session = remember(conversationId) { DemoChatSession(conversationId, scope) }
    DisposableEffect(session) { onDispose(session::cancel) }
    LaunchedEffect(session, jump) { jump?.let(session::jumpTo) }
    val currentOnBack by rememberUpdatedState(onBack)
    val slots = remember(session) { demoSlots(session) { currentOnBack } }
    val snackbarHostState = remember { SnackbarHostState() }
    val mermaidRenderCache = remember { MermaidRenderCache() }
    val parsedMarkdownCache = remember { ParsedMarkdownCache() }
    ProvideDateTimeFormat(DateTimeFormatPrefs()) {
        ProvideDemoArtifacts {
        ChatScreenContent(
            actions = session,
            uiStateFlow = session.state,
            attachedFilesFlow = MutableStateFlow(emptyList()),
            chatPreferences = DemoPreferences,
            promptLibraryRevisionFlow = MutableStateFlow(0L),
            toolPartStatusesFlow = MutableStateFlow(emptyMap()),
            pdfPasswordPromptsFlow = MutableStateFlow(emptyList()),
            compareSyncScroll = session.compareSyncScroll,
            mermaidRenderCache = mermaidRenderCache,
            parsedMarkdownCache = parsedMarkdownCache,
            codeRunHost = NoCodeRuns,
            updateVersion = null,
            onAcknowledgeUpdate = {},
            snackbarHostState = snackbarHostState,
            mcpSignIn = NoMcpSignIn,
            slots = slots,
            onNavigateToProviderKeys = {},
            modifier = modifier,
            onOpenDrawer = openDrawer,
        )
        }
    }
}

private val DemoPreferences = MutableStateFlow(ChatPreferences())
private val NoCodeRuns = CodeRunHost(runs = MutableStateFlow(CodeRunsState()), onRun = {}, onShown = {})
private val NoMcpSignIn = McpSignIn(preparingServer = null, signIn = { _, _ -> false }, signInAction = { _, _ -> false })
private val NoAttachments = ChatAttachmentActions(onAttachFiles = {}, onTakePhoto = {}, onPickPhotos = {}, onAttachToCode = {})

/** The screen's platform slots: the demo's composer, bar and dictation, and nothing for what needs a server. */
private fun demoSlots(session: DemoChatSession, onBack: () -> (() -> Unit)?) = ChatScreenSlots(
    replyNotificationAsk = {},
    startRecording = { _, _, _ -> session::startDictation },
    attachmentActions = { NoAttachments },
    effects = {},
    mcpProviders = { content -> content() },
    subagentThreadsSheet = { _, _, _, _ -> },
    selectionItem = { _, _ -> Modifier },
    conversationSeenEffect = { _, _, _, _ -> },
    composer = { params -> DemoComposer(params) },
    topBar = { params -> DemoTopBar(params, session, onBack()) },
)

/** The app's bar content, after a back arrow while there is a screen to go back to. */
@Composable
private fun DemoTopBar(params: ChatTopBarParams, session: DemoChatSession, onBack: (() -> Unit)?) {
    Row(params.modifier, verticalAlignment = Alignment.Top) {
        if (onBack != null) {
            FloatingBarIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp),
            )
        }
        ChatFloatingTopBarContent(
            uiState = params.uiState,
            actions = session,
            onLoadPreset = params.onLoadPreset,
            onSavePreset = params.onSavePreset,
            onRename = params.onRename,
            contextRingDialogs = params.contextRingDialogs,
            openApps = 0,
            onOpenApps = {},
            backgroundTasks = null,
            traceViewerSheet = { _, _, _ -> },
            modifier = Modifier.weight(1f),
            onOpenDrawer = params.onOpenDrawer,
            widePane = params.widePane,
            railMode = params.railMode,
        )
    }
}

/**
 * The composer both platforms share (`CommonChatInputCore`), dressed as the app's: "+" with its tool
 * dots, the field, and the mic. It folds to one line while idle and turns violet in a temporary chat.
 * Enter sends; Shift+Enter starts a new line.
 */
@Composable
private fun DemoComposer(params: ChatComposerParams) {
    CommonChatInputCore(
        state = ChatInputState(
            inputText = params.inputText,
            isStreaming = params.isStreaming,
            isRecording = params.isRecording,
            isTranscribing = params.isTranscribing,
            chrome = params.chrome,
            selectedModelDisplay = params.selectedModelDisplay,
            isCodeInterpreterAvailable = false,
            gates = params.gates,
        ),
        onSend = params.onSend,
        onStop = params.onStop,
        isTemporaryChat = params.isTemporaryChat,
        fontSizeMultiplier = params.fontSizeMultiplier,
        canFold = true,
        modifier = params.modifier,
        leadingButtons = {
            // "+" opens the app's tools sheet, where the model picker lives.
            val bounce = rememberPressBounce()
            Box(Modifier.pressBounce(bounce, enabled = !isLiquidGlass)) {
                IconButton(
                    onClick = params.onOpenTools,
                    colors = ChatInputDefaults.toolsButtonColors(),
                    modifier = Modifier.size(BoxButtonSize),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Files and tools")
                }
                ToolsButtonDots(
                    compactSuggested = params.chrome.compactSuggested,
                    enabledTools = params.chrome.enabledTools,
                    selectedMcpServerNames = params.chrome.selectedMcpServerNames,
                    showEphemeralTools = params.gates.showEphemeralTools,
                )
            }
        },
        trailingButtons = {
            val bounce = rememberPressBounce()
            IconButton(
                onClick = if (params.isRecording) params.onStopRecording else params.onStartRecording,
                enabled = !params.isTranscribing,
                modifier = Modifier
                    .size(BoxButtonSize)
                    .pressBounce(bounce, enabled = !isLiquidGlass && !params.isTranscribing),
            ) {
                VoiceMicIndicator(isRecording = params.isRecording, isTranscribing = params.isTranscribing)
            }
        },
        textFieldContent = {
            OutlinedTextField(
                value = params.inputText,
                onValueChange = params.onInputChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp, max = 160.dp)
                    .onPreviewKeyEvent { event ->
                        val send = event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed
                        if (send) params.onSend()
                        send
                    },
                placeholder = {
                    ChatInputPlaceholder(isRecording = params.isRecording, selectedModelDisplay = params.selectedModelDisplay)
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = ChatInputDefaults.textFieldColors(),
                maxLines = MAX_COMPOSER_LINES,
            )
        },
    )
}

/**
 * Stands in for `ChatViewModel`: the sample conversation's state, the composer's text, the model pick,
 * ratings, and a canned reply streamed a few words at a time after each send. With Compare on, both
 * models stream at once and save as one reply whose parts name their model, as the server saves a
 * comparison; keeping one answer narrows that reply to its parts.
 */
internal class DemoChatSession(
    private val conversationId: String,
    private val scope: CoroutineScope,
) : NoOpChatActions() {
    private val sample = sampleChat(conversationId)
    private val _state = MutableStateFlow(
        ChatUiState(
            selection = ModelSelectionState(
                choice = (sample?.model ?: Gpt51).choice,
                // The picker lists the chat's own model too, so it shows as selected.
                availableModels = (PickerModels + listOfNotNull(sample?.model)).distinct()
                    .groupBy({ it.endpoint }, { it.name }),
                // The new-chat screen offers what the demo can show off, one tap each.
                selectedAgentStarters = DemoStarters,
            ),
            content = MessagesState().withMessages(sample?.toMessages().orEmpty()),
            // Nothing here runs code, so code blocks offer no Run, as on a server without the permission.
            gates = FeatureGatesState(runCodeEnabled = false),
            conversation = ConversationMetaState(
                conversationId = sample?.id,
                conversationTitle = sample?.title,
            ),
        ),
    )
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    val compareSyncScroll = MutableStateFlow(true)
    private var job: Job? = null
    private var dictation: Job? = null
    private var autoAdvance: Job? = null

    private var asking: AskFlow? = null

    init {
        // A sample chat that ends on a question opens paused on it, its intro already streamed.
        sample?.asking?.let { flow ->
            val messages = _state.value.content.messages
            val sent = userMessage(conversationId, messages.size, messages.lastOrNull()?.messageId, flow.ask, now())
            updateContent { it.withMessages(messages + sent).copy(streamingContent = flow.intro) }
            pauseOn(flow)
        }
    }

    // Never repeats, so a second jump to the same message still scrolls.
    private var nextFocusRequestId = 0L

    fun cancel() {
        job?.cancel()
        dictation?.cancel()
        autoAdvance?.cancel()
    }

    /** The mic: "hears" a sample request for a few seconds, transcribes it, and adds it to the draft. */
    fun startDictation() {
        if (_state.value.isRecording || _state.value.isTranscribing) return
        updateVoice { it.copy(isRecording = true) }
        dictation = scope.launch {
            delay(DICTATION_MS)
            transcribe()
        }
    }

    override fun stopRecording() {
        if (!_state.value.isRecording) return
        dictation?.cancel()
        dictation = scope.launch { transcribe() }
    }

    private suspend fun transcribe() {
        updateVoice { it.copy(isRecording = false, isTranscribing = true) }
        delay(TRANSCRIBING_MS)
        _state.update {
            val draft = listOf(it.inputText.trimEnd(), DICTATED_TEXT).filter(String::isNotEmpty).joinToString(" ")
            it.copy(voice = it.voice.copy(isTranscribing = false), composer = it.composer.copy(inputText = draft))
        }
    }

    private fun updateVoice(change: (VoiceState) -> VoiceState) {
        _state.update { it.copy(voice = change(it.voice)) }
    }

    /** As in the app, only a chat that hasn't started switches; a started temporary chat stays one. */
    override fun toggleTemporaryChat() {
        _state.update {
            if (it.conversation.conversationId != null) {
                it
            } else {
                it.copy(conversation = it.conversation.copy(isTemporaryChat = !it.conversation.isTemporaryChat))
            }
        }
    }

    override fun onInputChanged(text: String) {
        _state.update { it.copy(composer = it.composer.copy(inputText = text)) }
    }

    override fun onModelSelected(endpoint: String, model: String) {
        _state.update { it.copy(selection = it.selection.copy(choice = ModelChoice.Raw(endpoint, model))) }
    }

    override fun submitFeedback(messageId: String, feedback: MinimalFeedback?) {
        updateMessages { messages ->
            messages.map { if (it.messageId == messageId) it.copy(feedback = feedback?.let { f -> Feedback(rating = f.rating) }) else it }
        }
    }

    override fun copyMessage(messageId: String, secondary: Boolean, write: (String) -> Unit) {
        _state.value.content.messages.firstOrNull { it.messageId == messageId }?.let { write(it.text) }
    }

    override fun sendMessage() {
        val current = _state.value
        val text = current.inputText.trim()
        if (text.isNotEmpty() && current.renderablePendingAction != null) {
            _state.update { it.copy(composer = it.composer.copy(inputText = "")) }
            answerActiveQuestion(AskAnswerDraft(freeText = text))
            return
        }
        if (text.isEmpty() || current.isStreaming) return
        val messages = current.content.messages
        val sent = userMessage(conversationId, messages.size, messages.lastOrNull()?.messageId, text, now())
        _state.update {
            it.copy(
                composer = it.composer.copy(inputText = ""),
                content = it.content.withMessages(messages + sent).copy(isStreaming = true),
                conversation = it.conversation.copy(conversationId = conversationId),
            )
        }
        val flow = askFlowFor(text)
        if (flow != null && !current.comparisonState.isEnabled) {
            job = scope.launch {
                delay(FIRST_TOKEN_DELAY_MS)
                streamWords(flow.intro) { shown -> updateContent { it.copy(streamingContent = shown) } }
                pauseOn(flow)
            }
            return
        }
        val canned = cannedReplyFor(text)
        if (current.comparisonState.isEnabled) {
            streamComparison(canned)
            return
        }
        job = scope.launch {
            delay(FIRST_TOKEN_DELAY_MS)
            canned.thinking?.let { thinking ->
                streamWords(thinking) { shown -> updateContent { it.copy(streamingThinking = shown) } }
            }
            streamWords(canned.answer) { shown -> updateContent { it.copy(streamingContent = shown) } }
            finish()
        }
    }

    override fun sendStarter(text: String) {
        _state.update { it.copy(composer = it.composer.copy(inputText = text)) }
        sendMessage()
    }

    override fun editStarter(text: String) {
        _state.update { it.copy(composer = it.composer.copy(inputText = text)) }
    }

    override fun stopGeneration() {
        job?.cancel()
        asking = null
        updateContent { it.copy(pendingAction = null) }
        if (_state.value.comparisonState.isEnabled) finishComparison() else finish()
    }

    // ─── Questions ────────────────────────────────────────────────────

    /** Pauses the streaming reply on [flow]'s questions, as an agent's `ask_user_question` does. */
    private fun pauseOn(flow: AskFlow) {
        asking = flow
        updateContent {
            it.copy(
                isStreaming = true,
                pendingAction = PendingAction(
                    actionId = "$conversationId-ask",
                    conversationId = conversationId,
                    payload = PendingActionPayload(
                        type = PendingActionTypes.ASK_USER_QUESTION,
                        question = flow.questions.first().let { q -> AskUserQuestionRequest(q.question, q.description, q.options, q.multiSelect) },
                        questions = flow.questions,
                    ),
                ),
                askAnswerDrafts = emptyMap(),
                askActiveQuestionId = null,
                askPanelCollapsed = false,
            )
        }
    }

    override fun answerPendingQuestion(answer: String) {
        answerPendingQuestions(mapOf(asking?.questions?.first()?.id.orEmpty() to answer))
    }

    /** Resumes the reply with the answers: the rest of it streams on after the intro. */
    override fun answerPendingQuestions(answers: Map<String, String>) {
        val flow = asking ?: return
        asking = null
        autoAdvance?.cancel()
        val intro = _state.value.content.streamingContent
        updateContent { it.copy(pendingAction = null, askAnswerDrafts = emptyMap(), askActiveQuestionId = null) }
        job = scope.launch {
            delay(FIRST_TOKEN_DELAY_MS)
            streamWords(flow.reply(answers)) { shown -> updateContent { it.copy(streamingContent = "$intro\n\n$shown") } }
            finish()
        }
    }

    override fun updateAskAnswerDraft(questionId: String, draft: AskAnswerDraft) {
        updateContent { it.copy(askAnswerDrafts = it.askAnswerDrafts + (questionId to draft)) }
    }

    override fun selectAskQuestion(questionId: String) {
        updateContent { it.copy(askActiveQuestionId = questionId) }
    }

    override fun setAskPanelCollapsed(collapsed: Boolean) {
        updateContent { it.copy(askPanelCollapsed = collapsed) }
    }

    override fun cancelPausePanelAutoAdvance() {
        autoAdvance?.cancel()
    }

    /**
     * A tapped option, as the app's panel handles it: a single-choice pick moves on after a beat (to
     * the next question still open, or submits on a phone; to the next tab on a wide screen, where
     * Continue submits). A multi-select pick waits for more.
     */
    override fun pickAskOption(questionId: String, draft: AskAnswerDraft, advance: PausePanelAutoAdvance) {
        autoAdvance?.cancel()
        updateAskAnswerDraft(questionId, draft)
        val question = asking?.questions?.firstOrNull { it.id == questionId } ?: return
        if (question.multiSelect || draft.selectedOptions.isEmpty()) return
        autoAdvance = scope.launch {
            delay(PAUSE_PANEL_AUTO_ADVANCE_DELAY_MS)
            moveOn(advance)
        }
    }

    /** The composer's send while paused answers the question on screen, then moves on. */
    private fun answerActiveQuestion(draft: AskAnswerDraft) {
        val flow = asking ?: return
        val ids = flow.questions.map { it.id }
        updateAskAnswerDraft(ids[pausePanelActiveIndex(ids, _state.value.content.askActiveQuestionId)], draft)
        moveOn(PausePanelAutoAdvance.AdvanceOrSubmit)
    }

    private fun moveOn(advance: PausePanelAutoAdvance) {
        val flow = asking ?: return
        val ids = flow.questions.map { it.id }
        val drafts = _state.value.content.askAnswerDrafts
        val answers = flow.questions.associate { it.id to composeAskAnswer(it.options, drafts[it.id] ?: AskAnswerDraft()) }
        val active = pausePanelActiveIndex(ids, _state.value.content.askActiveQuestionId)
        when (advance) {
            PausePanelAutoAdvance.NextTab -> ids.getOrNull(active + 1)?.let(::selectAskQuestion)
            PausePanelAutoAdvance.AdvanceOrSubmit ->
                nextBlankQuestionIndex(ids, answers, active)?.let { selectAskQuestion(ids[it]) } ?: answerPendingQuestions(answers)
        }
    }

    // ─── Find in chat ─────────────────────────────────────────────────

    override fun openSearch() = updateSearch { ChatSearchState(isSearchOpen = true) }

    override fun closeSearch() = updateSearch { ChatSearchState() }

    override fun onSearchQueryChanged(query: String) {
        val matches = if (query.isBlank()) emptyList() else matchesOf(query)
        updateSearch {
            it.copy(
                searchQuery = query,
                searchMatchIndices = matches,
                currentSearchMatchIndex = 0,
                searchFocusRequest = matches.firstOrNull()?.let(::focusRequestFor),
            )
        }
    }

    override fun nextSearchMatch() = stepSearchMatch(1)

    override fun previousSearchMatch() = stepSearchMatch(-1)

    override fun onSearchScrollHandled() = updateSearch { it.copy(searchFocusRequest = null) }

    /**
     * Opens find on [jump]'s query with the current match in its message, on the side (reply or
     * reasoning) the hit was on; with no literal match there, the message flashes instead.
     */
    fun jumpTo(jump: ChatJump) {
        val rendered = collapseParallelToPrimary(_state.value.displayMessages)
        val target = rendered.indexOfFirst { it.message.messageId == jump.messageId }
        if (target < 0) return
        val kinds = if (jump.query.isEmpty()) emptyList() else occurrencesInReasoning(rendered[target].message, jump.query)
        val first = kinds.indexOfFirst { it == jump.inReasoning }.takeIf { it >= 0 } ?: 0
        val matches = if (kinds.isEmpty()) emptyList() else matchesOf(jump.query)
        val current = matches.indexOfFirst { it.messageIndex == target && it.occurrenceInMessage == first }
        val request = SearchFocusRequest(messageIndex = target, requestId = nextFocusRequestId++)
        val found = current >= 0
        updateSearch {
            if (found) {
                ChatSearchState(
                    isSearchOpen = true,
                    searchQuery = jump.query,
                    searchMatchIndices = matches,
                    currentSearchMatchIndex = current,
                    searchFocusRequest = request,
                    jumpFlash = JumpFlash(target, request.requestId, fade = false),
                )
            } else {
                it.copy(searchFocusRequest = request, jumpFlash = JumpFlash(target, request.requestId))
            }
        }
    }

    // Numbered by the walk the list renders, a comparison turn by its primary parts only.
    private fun matchesOf(query: String): List<SearchMatch> =
        collapseParallelToPrimary(_state.value.displayMessages).flatMapIndexed { index, node ->
            List(countMessageOccurrences(node.message, query)) { SearchMatch(messageIndex = index, occurrenceInMessage = it) }
        }

    private fun stepSearchMatch(delta: Int) {
        val matches = _state.value.searchMatchIndices
        if (matches.isEmpty()) return
        val next = (_state.value.currentSearchMatchIndex + delta + matches.size) % matches.size
        updateSearch { it.copy(currentSearchMatchIndex = next, searchFocusRequest = focusRequestFor(matches[next])) }
    }

    private fun focusRequestFor(match: SearchMatch) = SearchFocusRequest(match.messageIndex, nextFocusRequestId++)

    private fun updateSearch(change: (ChatSearchState) -> ChatSearchState) {
        _state.update { it.copy(search = change(it.search)) }
    }

    // ─── Compare ──────────────────────────────────────────────────────

    override fun setCompareSyncScroll(enabled: Boolean) {
        compareSyncScroll.value = enabled
    }

    override fun beginComparisonSetup() {
        if (_state.value.comparisonState.isEnabled) return
        updateComparison { ComparisonState(isSettingUp = true) }
    }

    override fun cancelComparisonSetup() {
        if (!_state.value.comparisonState.isSettingUp) return
        updateComparison { ComparisonState() }
    }

    override fun startComparison(endpoint: String, model: String, spec: String?, primary: ComparisonPick?) {
        if (_state.value.isStreaming) return
        _state.update {
            it.copy(
                selection = primary?.let { pick -> it.selection.copy(choice = ModelChoice.Raw(pick.endpoint, pick.model)) }
                    ?: it.selection,
                comparisonState = ComparisonState(
                    isEnabled = true,
                    secondaryEndpoint = endpoint,
                    secondaryModel = model,
                    secondarySpec = spec,
                ),
                // A new chat leaves the landing, so the two panes show.
                content = it.content.copy(screenState = ChatScreenState.ACTIVE),
            )
        }
    }

    override fun stopComparison() {
        if (_state.value.isStreaming) return
        _state.update {
            it.copy(
                comparisonState = ComparisonState(),
                content = if (it.content.messages.isEmpty()) {
                    it.content.copy(screenState = ChatScreenState.LANDING)
                } else {
                    it.content
                },
            )
        }
    }

    override fun setSecondaryModel(endpoint: String, model: String) {
        if (!_state.value.comparisonState.isEnabled) return
        updateComparison { it.copy(secondaryEndpoint = endpoint, secondaryModel = model, secondarySpec = null) }
    }

    /** "Keep this answer": the comparison's reply keeps only that lane's parts, and its model carries on. */
    override fun branchFromComparison(agentId: String) {
        val comparison = _state.value.comparisonState
        val replyId = comparison.parallelMessageId ?: return
        val secondary = agentId == comparison.secondaryAgentId
        val lane = if (secondary) {
            ModelChoice.Raw(comparison.secondaryEndpoint.orEmpty(), comparison.secondaryModel)
        } else {
            _state.value.selection.choice
        }
        val laneModel = lane.model ?: lane.endpoint
        _state.update {
            val messages = it.content.messages.map { message ->
                if (message.messageId != replyId) return@map message
                val parts = message.content.orEmpty()
                    .filter { part -> isAddedAgentId(part.agentId) == secondary }
                    .map { part -> part.copy(agentId = null, groupId = null) }
                message.copy(
                    content = parts,
                    text = parts.mapNotNull { part -> part.text }.joinToString(""),
                    sender = laneModel,
                    model = laneModel,
                    endpoint = lane.endpoint,
                )
            }
            it.copy(
                selection = it.selection.copy(choice = ModelChoice.Raw(lane.endpoint, laneModel)),
                comparisonState = ComparisonState(),
                content = it.content.withMessages(messages),
            )
        }
    }

    // The demo's replies always run to the end, so continuing one is keeping it.
    override fun keepAndContinue(agentId: String) = branchFromComparison(agentId)

    /** Both lanes stream at once, each its own canned answer, as the server runs the two agents. */
    private fun streamComparison(canned: CannedReply) {
        updateComparison {
            it.copy(
                primaryIsStreaming = true,
                secondaryIsStreaming = true,
                primaryStreamingContent = "",
                secondaryStreamingContent = "",
                primaryAgentId = null,
                secondaryAgentId = null,
                parallelMessageId = null,
                primaryFinalContent = null,
                secondaryFinalContent = null,
            )
        }
        job = scope.launch {
            delay(FIRST_TOKEN_DELAY_MS)
            coroutineScope {
                launch {
                    streamWords(canned.answer) { shown ->
                        _state.update {
                            it.copy(
                                comparisonState = it.comparisonState.copy(primaryStreamingContent = shown),
                                content = it.content.copy(streamingContent = shown),
                            )
                        }
                    }
                }
                launch {
                    streamWords(canned.alternate) { shown ->
                        updateComparison { it.copy(secondaryStreamingContent = shown) }
                    }
                }
            }
            finishComparison()
        }
    }

    /**
     * Saves both lanes as one reply, each part named by its lane's agent id (lane B's suffixed
     * `____1`), and settles the panes on it in the same update.
     */
    private fun finishComparison() {
        _state.update { state ->
            if (!state.content.isStreaming) return@update state
            val comparison = state.comparisonState
            val choice = state.selection.choice
            val primaryModel = choice.model ?: choice.endpoint
            val primaryId = ephemeralAgentId(choice.endpoint, primaryModel)
            val secondaryId = ephemeralAgentId(comparison.secondaryEndpoint.orEmpty(), comparison.secondaryModel.orEmpty()) +
                ADDED_AGENT_SUFFIX
            val primaryText = comparison.primaryStreamingContent
            val secondaryText = comparison.secondaryStreamingContent
            val parts = listOfNotNull(
                laneText(primaryText, primaryId, group = 0),
                laneText(secondaryText, secondaryId, group = 1),
            )
            val messages = state.content.messages
            val reply = Message(
                messageId = "$conversationId-${messages.size}",
                conversationId = conversationId,
                parentMessageId = messages.lastOrNull()?.messageId ?: NO_PARENT,
                sender = primaryModel,
                model = primaryModel,
                endpoint = choice.endpoint,
                text = primaryText,
                content = parts,
                createdAt = now(),
            ).takeIf { parts.isNotEmpty() }
            state.copy(
                content = state.content.withMessages(messages + listOfNotNull(reply)).copy(
                    isStreaming = false,
                    streamingContent = "",
                    justSettledMessageId = reply?.messageId,
                ),
                comparisonState = comparison.copy(
                    primaryAgentId = primaryId.takeIf { primaryText.isNotBlank() },
                    secondaryAgentId = secondaryId.takeIf { secondaryText.isNotBlank() },
                    primaryIsStreaming = false,
                    secondaryIsStreaming = false,
                    primaryStreamingContent = "",
                    secondaryStreamingContent = "",
                    parallelMessageId = reply?.messageId,
                    primaryFinalContent = primaryText,
                    secondaryFinalContent = secondaryText,
                ),
            )
        }
    }

    private fun updateComparison(change: (ComparisonState) -> ComparisonState) {
        _state.update { it.copy(comparisonState = change(it.comparisonState)) }
    }

    /** Swaps the live reply for its saved message in one update, as the app's finalize does. */
    private fun finish() {
        updateContent { content ->
            if (!content.isStreaming) return@updateContent content
            val choice = _state.value.selection.choice
            val reply = assistantMessage(
                conversationId = conversationId,
                index = content.messages.size,
                parentId = content.messages.lastOrNull()?.messageId,
                endpoint = choice.endpoint,
                // The demo's choices always name a model; the endpoint stands in as the sender otherwise.
                model = choice.model ?: choice.endpoint,
                thinking = content.streamingThinking.ifBlank { null },
                answer = content.streamingContent,
                createdAt = now(),
            ).takeIf { content.streamingContent.isNotBlank() }
            content.withMessages(content.messages + listOfNotNull(reply)).copy(
                isStreaming = false,
                streamingContent = "",
                streamingThinking = "",
                justSettledMessageId = reply?.messageId,
            )
        }
    }

    private fun updateContent(change: (MessagesState) -> MessagesState) {
        _state.update { it.copy(content = change(it.content)) }
    }

    private fun updateMessages(change: (List<Message>) -> List<Message>) {
        updateContent { it.withMessages(change(it.messages)) }
    }
}

private fun MessagesState.withMessages(messages: List<Message>): MessagesState = copy(
    messages = messages,
    displayMessages = buildActiveMessagePath(messages),
    screenState = if (messages.isEmpty() && !isStreaming) ChatScreenState.LANDING else ChatScreenState.ACTIVE,
)

private fun now(): String = Clock.System.now().toString()

// ─── Streaming ────────────────────────────────────────────────────────

/** Reveals [text] a few words at a time, as a model's tokens arrive. */
private suspend fun streamWords(text: String, onText: (String) -> Unit) {
    val tokens = Regex("""\S+\s*|\s+""").findAll(text).map { it.value }.toList()
    var shown = 0
    while (shown < tokens.size) {
        shown = (shown + Random.nextInt(1, MAX_TOKENS_PER_CHUNK + 1)).coerceAtMost(tokens.size)
        onText(tokens.subList(0, shown).joinToString(""))
        delay(Random.nextLong(CHUNK_DELAY_MIN_MS, CHUNK_DELAY_MAX_MS + 1))
    }
}

private const val FIRST_TOKEN_DELAY_MS = 700L
private const val CHUNK_DELAY_MIN_MS = 20L
private const val CHUNK_DELAY_MAX_MS = 40L
private const val MAX_TOKENS_PER_CHUNK = 3
private const val MAX_COMPOSER_LINES = 6
private const val DICTATION_MS = 2500L
private const val TRANSCRIBING_MS = 900L
private const val DICTATED_TEXT = "Plan me a three-day trip to Lisbon in May."

// ─── Messages ─────────────────────────────────────────────────────────

private val SampleModel.choice: ModelChoice get() = ModelChoice.Raw(endpoint, name)

/** The id the server gives a model run as a throwaway agent: `endpoint__model`, a `:` in the model written `__`. */
private fun ephemeralAgentId(endpoint: String, model: String) = endpoint + "__" + model.replace(":", "__")

/** What the server appends to the second (added) lane's agent id. */
private const val ADDED_AGENT_SUFFIX = "____1"

private fun laneText(text: String, agentId: String, group: Int) = text.takeIf { it.isNotBlank() }?.let {
    MessageContentPart(type = ContentType.TEXT, text = it, agentId = agentId, groupId = group)
}

private fun userMessage(conversationId: String, index: Int, parentId: String?, text: String, createdAt: String) = Message(
    messageId = "$conversationId-$index",
    conversationId = conversationId,
    parentMessageId = parentId ?: NO_PARENT,
    sender = "User",
    text = text,
    isCreatedByUser = true,
    content = listOf(MessageContentPart(type = ContentType.TEXT, text = text)),
    createdAt = createdAt,
)

private fun assistantMessage(
    conversationId: String,
    index: Int,
    parentId: String?,
    endpoint: String,
    model: String,
    thinking: String?,
    answer: String,
    createdAt: String,
) = Message(
    messageId = "$conversationId-$index",
    conversationId = conversationId,
    parentMessageId = parentId ?: NO_PARENT,
    sender = model,
    model = model,
    endpoint = endpoint,
    text = answer,
    content = listOfNotNull(
        thinking?.let { MessageContentPart(type = ContentType.THINK, think = it) },
        MessageContentPart(type = ContentType.TEXT, text = answer),
    ),
    createdAt = createdAt,
)

/**
 * The chat's transcript as the server would send it, ids `<chat>-<n>`. The last reply is dated when
 * the drawer says the chat was updated, each turn a few minutes before the next.
 */
internal fun SampleChat.toMessages(): List<Message> =
    turns.flatMapIndexed { i, turn ->
        val answeredAt = PageLoadedAt - ago - TURN_SPACING * (turns.lastIndex - i)
        val ask = userMessage(
            id,
            i * 2,
            parentId = if (i == 0) null else "$id-${i * 2 - 1}",
            text = turn.ask,
            createdAt = (answeredAt - 1.minutes).toString(),
        )
        val reply = assistantMessage(
            id, i * 2 + 1, ask.messageId, model.endpoint, model.name, turn.thinking, turn.answer, answeredAt.toString(),
        )
        listOf(ask, reply)
    }

private val TURN_SPACING = 4.minutes

// ─── Canned replies ───────────────────────────────────────────────────

/** A reply the demo can give; with Compare on, the second model answers with [alternate]. */
private class CannedReply(
    val keywords: List<String>,
    val answer: String,
    val alternate: String,
    val thinking: String? = null,
)

private val CannedReplies = listOf(
    // Apps open live in the artifact panel. With Compare on, the second model answers in words.
    CannedReply(
        keywords = listOf("solar", "planet", "planets", "space", "3d"),
        answer = withArtifact(
            before = "Here's a solar system you can spin with a finger. The planets orbit at their real relative " +
                "speeds (just much faster), and the buttons show one fact about each.",
            artifact = reactArtifact("solar-system", "Solar system", SolarSystemSource),
            after = "Sizes and distances aren't to scale: at real scale the planets would be specks too small to see.",
        ),
        alternate = "A 3D model is the most fun for a class: the sun in the middle, planets on their orbits, " +
            "and something to drag so it spins. Want me to build it as an app you can open on the classroom screen?",
    ),
    CannedReply(
        keywords = listOf("app", "artifact", "tracker"),
        answer = StashTracker.single().answer,
        alternate = "A shared note works too: one line per flavour, and cross one off each time a bar goes. " +
            "But an app can do the maths on the agent's budget for you.",
    ),
    CannedReply(
        keywords = listOf("flowchart", "diagram", "mermaid"),
        answer = ShoppingRules.single().answer,
        alternate = "As a list: 1. Is it on the shopping list? Buy it. 2. Is it chocolate? Ask first. " +
            "3. Did I say no? Then no. A diagram makes the loop in step 3 easier to spot.",
    ),
    // The car wash riddle: with Compare on, one model sees the catch and the other walks.
    CannedReply(
        keywords = listOf("car", "wash"),
        thinking = "The car itself has to get to the car wash, so walking there doesn't help.",
        answer = "**Drive.** The car has to be at the car wash to get washed. Walking over would leave it " +
            "sitting in your driveway.",
        alternate = """
            For just 50 meters, **walking is the clear choice**:

            - It's about a 1-minute walk
            - No fuel spent on a tiny trip
            - No parking hassle

            Driving would only make sense if you were carrying something heavy.
        """.trimIndent(),
    ),
    CannedReply(
        keywords = listOf("hello", "hi", "hey", "morning"),
        answer = "Hi! This is the Switchboard demo, running right in your browser with sample chats and no " +
            "server. Try one of these:\n\n" + ThingsToTry,
        alternate = "Hello! You're trying Switchboard in a browser tab. Open the drawer to look around the " +
            "sample chats, or ask both of us for some code and compare the answers.",
    ),
    CannedReply(
        keywords = listOf("code", "kotlin", "function", "python", "script", "program"),
        thinking = "A small, self-contained example shows syntax highlighting best. FizzBuzz in Kotlin is short " +
            "and familiar.",
        answer = """
            Here is a small Kotlin example:

            ```kotlin
            fun fizzBuzz(n: Int): String = when {
                n % 15 == 0 -> "FizzBuzz"
                n % 3 == 0 -> "Fizz"
                n % 5 == 0 -> "Buzz"
                else -> n.toString()
            }

            fun main() {
                (1..15).map(::fizzBuzz).forEach(::println)
            }
            ```

            Code blocks get syntax highlighting and a copy button. On your own server, agents with the code interpreter can run it too.
        """.trimIndent(),
        alternate = """
            The same idea in Python, written as a generator:

            ```python
            def fizz_buzz(limit):
                for n in range(1, limit + 1):
                    yield "Fizz" * (n % 3 == 0) + "Buzz" * (n % 5 == 0) or str(n)

            print("\n".join(fizz_buzz(15)))
            ```

            Shorter, though the Kotlin `when` reads more plainly.
        """.trimIndent(),
    ),
    CannedReply(
        keywords = listOf("table", "compare", "versus", "vs", "difference"),
        answer = """
            Tables render natively, so they stay readable on a phone:

            | Feature | Web app | Switchboard |
            |---|---|---|
            | Streaming replies | Yes | Yes |
            | Native Markdown and code | Yes | Yes |
            | Works offline for reading | No | Yes |
            | Several servers and accounts | No | Yes |

            Tap a wide table in the app to open it full screen.
        """.trimIndent(),
        alternate = """
            In short, Switchboard does what the web app does and adds what a phone needs:

            - **Offline reading** of chats you've opened.
            - **Several servers and accounts**, switched from the drawer.
            - **Native Markdown, code and tables**, with no web view.
        """.trimIndent(),
    ),
    CannedReply(
        keywords = listOf("plan", "trip", "travel", "schedule", "week"),
        thinking = "Keep it short and skimmable: a heading per day, a few bullets each.",
        answer = """
            ## A simple plan

            **Day 1**
            - Arrive, settle in, and take a long walk to get your bearings.
            - Dinner somewhere close by.

            **Day 2**
            - The main sight in the morning, before the crowds.
            - A slow lunch, then a museum or a park.

            **Day 3**
            - Something you saved for last, then head home.

            Tell me where you are going and I can fill in the details.
        """.trimIndent(),
        alternate = """
            ## Three days, three themes

            1. **Old town.** Walk it end to end and get lost on purpose.
            2. **Food.** A market in the morning, a cooking class in the afternoon.
            3. **Out of town.** A day trip by train, back for a late dinner.

            Which city? I'll swap in real places.
        """.trimIndent(),
    ),
    CannedReply(
        keywords = listOf("think", "reason", "why", "explain"),
        thinking = "The user wants to see reasoning. Explain briefly that models with reasoning show their " +
            "thinking in a collapsible block above the answer, which they can open or ignore.",
        answer = "Models that reason before answering show their thinking in a block like the one above. " +
            "It starts collapsed so the answer stays front and center; tap it to read how the model got there.",
        alternate = "Some models think out loud before they answer. The app folds that reasoning into a block " +
            "you can open, so you can check how an answer was reached without wading through it.",
    ),
)

private const val FallbackAlternate = "I'm a canned stand-in too. Ask both of us for **some code**, " +
    "**a table** or **a trip plan** and compare how we answer side by side."

// Every request the demo has an answer for, each worded so it reaches that answer
// (cannedReplyFor and askFlowFor match on these words).
private const val ThingsToTry = """- **Build me an app** to track my snacks: it opens live beside the chat
- **Show me the solar system** in 3D
- **Draw a flowchart** of my agent's rules
- **Plan a dessert table** for Saturday: I'll ask a few questions first
- **Explain why** the sky is blue: see the reasoning
- **Write some code**, **make a table** or **plan a trip**"""

private val DemoStarters = listOf(
    "Build me an app to track my snacks",
    "Show me the solar system in 3D",
    "Plan a dessert table for Saturday",
    "Draw a flowchart of my agent's rules",
)

private const val FallbackReply = "This is a demo, so I can only give canned answers. Here's what I can show you:\n\n" +
    ThingsToTry + "\n\nTo chat for real, install Switchboard and connect it to your own LibreChat server."

private fun cannedReplyFor(text: String): CannedReply {
    val words = text.lowercase().split(Regex("[^a-z0-9]+")).toSet()
    return CannedReplies.firstOrNull { reply -> reply.keywords.any { it in words } }
        ?: CannedReply(keywords = emptyList(), answer = FallbackReply, alternate = FallbackAlternate)
}

/** A request the demo answers by asking questions first. */
private fun askFlowFor(text: String): AskFlow? {
    val words = text.lowercase().split(Regex("[^a-z0-9]+")).toSet()
    return DessertTable.takeIf { "dessert" in words || "desserts" in words || "party" in words }
}
