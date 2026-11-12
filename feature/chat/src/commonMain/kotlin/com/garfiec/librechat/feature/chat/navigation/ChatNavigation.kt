package com.garfiec.librechat.feature.chat.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.core.data.datastore.AppPreferences
import com.garfiec.librechat.core.data.datastore.ArtifactDisplayMode
import com.garfiec.librechat.core.data.datastore.PreferenceStore
import com.garfiec.librechat.feature.chat.components.LocalArtifactFileActions
import com.garfiec.librechat.feature.chat.components.artifact.Artifact
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactOrigin
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactPresentationHost
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactPresentationState
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactSheetHalfOpenBelowWidth
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactViewerHandoff
import com.garfiec.librechat.feature.chat.components.artifact.LocalArtifactOrigin
import com.garfiec.librechat.feature.chat.components.artifact.LocalOpenArtifact
import com.garfiec.librechat.feature.chat.components.artifact.ProvideAddArtifactToHomeScreen
import com.garfiec.librechat.feature.chat.components.rememberAttachmentFileActions
import com.garfiec.librechat.feature.chat.prompts.PromptEditorScreen
import com.garfiec.librechat.feature.chat.prompts.PromptsLibraryScreen
import com.garfiec.librechat.feature.chat.screen.ArtifactFullscreenScreen
import com.garfiec.librechat.feature.chat.screen.ChatScreen
import com.garfiec.librechat.feature.chat.screen.ConversationMediaScreen
import com.garfiec.librechat.feature.chat.screen.NewChatScreen
import com.garfiec.librechat.feature.chat.screen.SharedChatScreen
import com.garfiec.librechat.feature.chat.viewmodel.ProjectLandingViewModel
import com.garfiec.librechat.feature.chat.viewmodel.PromptInsertionHandoff
import com.garfiec.librechat.feature.chat.viewmodel.chatHandoffKey
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable sealed interface ChatRoute : NavKey

/** [agentId] (when non-null) is the agent to pre-select for the new chat — set when
 *  starting a chat from an agent's detail/marketplace card so the new chat opens on
 *  that agent rather than falling back to last-used/first-agent/first-model.
 *  [endpoint]/[model] pre-select a concrete model instead (home-screen model shortcut /
 *  quick action), with [spec] the spec it was recorded from; they ride on the route so a
 *  payload-carrying NewChat replaces a bare landing NewChat (dedup-by-value) and re-seeds even
 *  when already on the landing. */
@Serializable data class NewChat(
    val agentId: String? = null,
    val endpoint: String? = null,
    val model: String? = null,
    val spec: String? = null,
    /**
     * The account this landing belongs to — set when an account flip resets the back stack, and
     * otherwise unread. Load-bearing for identity alone: Nav3 keeps an entry's ViewModelStore until
     * that entry's key leaves the back stack, and the flip's `clear()` + `add(NewChat())` put the
     * SAME key straight back, so the outgoing account's landing ChatViewModel would survive with its
     * model selection and agent list (another server's agents in the selector, until a cold start).
     * A different value here is a different entry, and its store is discarded.
     */
    val accountScope: String? = null,
    /**
     * The project this new chat is filed into, and its name for the landing's "In <project>" chip.
     * A project-scoped landing is pushed over the project rather than made the root, and is replaced
     * by the chat once it starts, so Back from the chat returns to the project.
     */
    val chatProjectId: String? = null,
    val chatProjectName: String? = null,
) : ChatRoute

/** True for the landing a New chat tap would open: not filed into a project. */
val NewChat.isPlainLanding: Boolean get() = chatProjectId == null

/** The key this landing's server-file picks are addressed to; see [chatHandoffKey]. */
val NewChat.handoffKey: String? get() = chatHandoffKey(conversationId = null, chatProjectId = chatProjectId)

/**
 * [isTemporary] marks a conversation the user started as a temporary chat. It rides on the
 * route (not just the in-memory NewChatSelectionHandoff) so it survives process death: a restored
 * Chat(id) entry re-initializes temp-aware and never persists the server-hidden conversation to
 * Room. SECURITY: this is the temp-chat data-at-rest guard for the handed-off / restored Chat VM.
 */
@Serializable data class Chat(
    val conversationId: String? = null,
    val isTemporary: Boolean = false,
    /** Open at this message (a search hit): select its branch, scroll to it, highlight [highlightQuery]. */
    val targetMessageId: String? = null,
    val highlightQuery: String? = null,
    /** The hit matched in the message's reasoning, so the jump lands on a match there. */
    val targetInReasoning: Boolean = false,
) : ChatRoute

@Serializable data object PromptsLibrary : ChatRoute

@Serializable data class PromptEditor(val groupId: String? = null) : ChatRoute

/** Telegram-style "Show all media" gallery for a single conversation. */
@Serializable data class ConversationMedia(val conversationId: String) : ChatRoute

/**
 * Full-screen artifact viewer. Carries only the lightweight identity; the artifact's
 * (potentially large) content is handed off in-memory via [ArtifactViewerHandoff].
 */
@Serializable data class ArtifactFullscreen(val identifier: String, val version: Int) : ChatRoute

/**
 * The in-app viewer for one of the ACTIVE account's shared links, by [shareId]. A link on another
 * signed-in account's server is pushed only after the switch to that account has reset the stack.
 */
@Serializable data class SharedChat(val shareId: String) : ChatRoute

fun EntryProviderScope<NavKey>.chatEntries(
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    /** Removes the given entry wherever it sits; for a screen closing itself, which may not be on top. */
    onRemove: (NavKey) -> Unit,
    onNavigateToChat: (conversationId: String, isTemporary: Boolean) -> Unit,
    onOpenDrawer: (() -> Unit)? = null,
    /**
     * Deep-link target for the user-provided-key error CTA snackbar and
     * the model-selector "Add key" action on greyed endpoint groups. The host navigates
     * to Settings → Provider API Keys. When [endpointName] is non-null, the destination
     * screen auto-opens the Set Key bottom-sheet for that endpoint.
     */
    onNavigateToProviderKeys: (endpointName: String?) -> Unit,
    /** Navigates to the server-file picker; wired by the host to `navigator.navigate(FilesPicker)`. */
    onAttachFromServer: () -> Unit,
    /**
     * Opens the Memories page (owned by settings) from a memory card's Manage action; wired by the
     * host to `navigator.navigate(Memories)`. The chat hides the action from a role that may not
     * read memories.
     */
    onOpenMemories: () -> Unit,
    /** Opens What's new (owned by settings) from the update banner on an empty chat. */
    onOpenWhatsNew: () -> Unit = {},
) {
    entry<NewChat> { key ->
        val projectName = key.chatProjectId?.let { projectId ->
            val landing = koinViewModel<ProjectLandingViewModel> {
                parametersOf(projectId, key.chatProjectName.orEmpty())
            }
            val project by landing.state.collectAsStateWithLifecycle()
            val currentOnRemove by rememberUpdatedState(onRemove)
            LaunchedEffect(project.deleted) {
                if (project.deleted) currentOnRemove(key)
            }
            project.name
        }
        NewChatScreen(
            initialAgentId = key.agentId,
            initialEndpoint = key.endpoint,
            initialModel = key.model,
            initialSpec = key.spec,
            chatProjectId = key.chatProjectId,
            chatProjectName = projectName,
            onConversationStart = { conversationId, isTemporary ->
                onNavigateToChat(conversationId, isTemporary)
            },
            onOpenDrawer = onOpenDrawer,
            onNavigateToPromptsLibrary = { onNavigate(PromptsLibrary) },
            onNavigateToProviderKeys = onNavigateToProviderKeys,
            onAttachFromServer = onAttachFromServer,
            onOpenWhatsNew = onOpenWhatsNew,
            onOpenMemories = onOpenMemories,
        )
    }
    entry<Chat> { key ->
        ProvideOpenArtifact(onNavigate) {
            ChatScreen(
                conversationId = key.conversationId,
                // A restored temp Chat(id) entry carries isTemporary=true so the VM re-initializes
                // temp-aware (never persists the server-hidden conversation). See ChatViewModel.init.
                isTemporaryRoute = key.isTemporary,
                targetMessageId = key.targetMessageId,
                highlightQuery = key.highlightQuery,
                targetInReasoning = key.targetInReasoning,
                onOpenDrawer = onOpenDrawer,
                onNavigateToPromptsLibrary = { onNavigate(PromptsLibrary) },
                onNavigateBack = onBack,
                // Fork/duplicate produce real (non-temp) conversations.
                onNavigateToConversation = { conversationId -> onNavigateToChat(conversationId, false) },
                // Null on a new chat with no id yet, which hides the overflow menu item.
                onShowAllMedia = key.conversationId?.let { id -> { onNavigate(ConversationMedia(id)) } },
                onNavigateToProviderKeys = onNavigateToProviderKeys,
                onAttachFromServer = onAttachFromServer,
                onOpenWhatsNew = onOpenWhatsNew,
                onOpenMemories = onOpenMemories,
            )
        }
    }
    entry<ConversationMedia> { key ->
        ProvideOpenArtifact(onNavigate) {
            ConversationMediaScreen(
                conversationId = key.conversationId,
                onNavigateBack = onBack,
            )
        }
    }
    sharedChatEntry(onNavigate = onNavigate, onBack = onBack, onRemove = onRemove)
    entry<ArtifactFullscreen> { key ->
        // Provider present so the fullscreen viewer's toolbar can pin the artifact too.
        ProvideAddArtifactToHomeScreen {
            ProvideAttachmentDownloader {
                ArtifactFullscreenScreen(
                    identifier = key.identifier,
                    version = key.version,
                    onBack = onBack,
                )
            }
        }
    }
    entry<PromptsLibrary> {
        // The chat entry is off-composition while the library is up, so the text is staged here and
        // collected when the chat screen re-enters on pop.
        val promptInsertionHandoff = koinInject<PromptInsertionHandoff>()
        PromptsLibraryScreen(
            onNavigateBack = onBack,
            onUseInChat = { text ->
                promptInsertionHandoff.put(text)
                onBack()
            },
            onNavigateToEditor = { groupId ->
                onNavigate(PromptEditor(groupId = groupId))
            },
        )
    }
    entry<PromptEditor> { key ->
        PromptEditorScreen(
            onBack = onBack,
            groupId = key.groupId,
        )
    }
}

/**
 * The shared-chat viewer. Continue's copy takes its place, so Back from the copy returns to where the
 * link was opened.
 */
private fun EntryProviderScope<NavKey>.sharedChatEntry(
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    onRemove: (NavKey) -> Unit,
) {
    entry<SharedChat> { key ->
        ProvideOpenArtifact(onNavigate) {
            SharedChatScreen(
                shareId = key.shareId,
                onNavigateBack = onBack,
                onOpenConversation = { conversationId ->
                    onRemove(key)
                    onNavigate(Chat(conversationId))
                },
            )
        }
    }
}

/**
 * Owns the screen-level artifact presentation and provides [LocalOpenArtifact] so artifacts tapped
 * deep in the message list just fire an event. Under the Full screen choice it pushes the
 * [ArtifactFullscreen] route (payload staged in [ArtifactViewerHandoff]); otherwise it opens the
 * artifact in an [ArtifactPresentationHost] around [content], which survives the tapped item
 * scrolling off-screen and keeps the page across rotation and folding.
 */
@Composable
internal fun ProvideOpenArtifact(
    onNavigate: (NavKey) -> Unit,
    content: @Composable () -> Unit,
) {
    val handoff = koinInject<ArtifactViewerHandoff>()
    val preferences = koinInject<PreferenceStore>()
    val displayMode by preferences.observe(AppPreferences.ArtifactDisplay)
        .collectAsStateWithLifecycle(preferences.initial(AppPreferences.ArtifactDisplay))
    val splitFraction by preferences.observe(AppPreferences.ArtifactSplitFraction)
        .collectAsStateWithLifecycle(preferences.initial(AppPreferences.ArtifactSplitFraction))
    val presentation = remember { ArtifactPresentationState() }
    val origin = remember { ArtifactOrigin() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val windowWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    // Read at tap time: the mode and the window only matter as the artifact opens.
    val currentMode by rememberUpdatedState(displayMode)
    val currentWindowWidth by rememberUpdatedState(windowWidth)

    val openArtifact = remember(onNavigate, handoff, presentation) {
        { artifact: Artifact, versions: List<Artifact> ->
            if (currentMode == ArtifactDisplayMode.FULLSCREEN) {
                handoff.put(artifact, versions)
                onNavigate(ArtifactFullscreen(artifact.identifier, artifact.version))
            } else {
                presentation.show(
                    ArtifactViewerHandoff.Entry(artifact, versions),
                    opensFull = currentWindowWidth >= ArtifactSheetHalfOpenBelowWidth,
                )
            }
        }
    }

    // Wraps the content and the viewer alike, so the pin action reaches inline cards and the viewer
    // (no-op on platforms without a pin action).
    ProvideAddArtifactToHomeScreen {
        CompositionLocalProvider(LocalOpenArtifact provides openArtifact, LocalArtifactOrigin provides origin) {
            ArtifactPresentationHost(
                state = presentation,
                automatic = displayMode == ArtifactDisplayMode.AUTOMATIC,
                splitFraction = splitFraction,
                onSplitFractionChange = { fraction ->
                    scope.launch { preferences.set(AppPreferences.ArtifactSplitFraction, fraction) }
                },
                origin = origin,
                panelScope = { panel ->
                    ProvideAttachmentDownloader {
                        // The host's lifetime, not one opening's: a save picker's result or a
                        // download can land after the viewer closed.
                        val fileActions = rememberAttachmentFileActions()
                        CompositionLocalProvider(LocalArtifactFileActions provides fileActions) { panel() }
                    }
                },
                content = content,
            )
        }
    }
}

val chatSerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(NewChat::class, NewChat.serializer())
        subclass(Chat::class, Chat.serializer())
        subclass(PromptsLibrary::class, PromptsLibrary.serializer())
        subclass(PromptEditor::class, PromptEditor.serializer())
        subclass(ConversationMedia::class, ConversationMedia.serializer())
        subclass(ArtifactFullscreen::class, ArtifactFullscreen.serializer())
        subclass(SharedChat::class, SharedChat.serializer())
    }
}
