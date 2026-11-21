package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.preferences.InlineArtifactPrefs
import com.garfiec.librechat.core.model.response.FilePreviewResponse
import com.garfiec.librechat.core.ui.media.MediaPreviewState
import com.garfiec.librechat.feature.chat.components.artifact.MermaidRenderCache
import com.garfiec.librechat.feature.chat.components.mcpapp.LegacyUiResources
import com.garfiec.librechat.feature.chat.components.mcpapp.LocalLegacyUiResources
import com.garfiec.librechat.feature.chat.components.mcpapp.LocalMcpApps
import com.garfiec.librechat.feature.chat.components.mcpapp.McpApps
import com.garfiec.librechat.feature.chat.util.PtcTrace
import com.garfiec.librechat.feature.chat.viewmodel.SiteIconsState
import com.garfiec.librechat.feature.chat.viewmodel.SubagentTrace

/**
 * Wraps the chat screen content in every chat-scoped CompositionLocal ([ChatRootContent]), plus the
 * MCP App providers and the child-thread viewer, which need ViewModels.
 */
@Composable
fun ChatRoot(
    inlineArtifactPrefs: InlineArtifactPrefs,
    mermaidRenderCache: MermaidRenderCache,
    parsedMarkdownCache: ParsedMarkdownCache,
    subagentProgress: Map<String, SubagentTrace>,
    /** Backs [LocalPtcTraces]. Required, so neither screen can drop the live code-card trace. */
    ptcTraces: Map<String, PtcTrace>,
    /** Deferred-preview results by `file_id`; see [LocalPreviewOverrides]. */
    previewOverrides: Map<String, FilePreviewResponse>,
    /** The conversation the child-thread viewer reads from. Null on the landing screen. */
    conversationId: String?,
    /**
     * Whether this server can serve child threads at all. Required, not defaulted, so neither
     * platform's `ChatScreen` can quietly drop it and offer a row that only ever says "unavailable"
     * — `on_subagent_update` predates these routes by two releases, so a 0.8.6/0.8.7 server draws
     * the trace cards the row would hang off.
     */
    subagentThreadsSupported: Boolean,
    mediaPreview: MediaPreviewState?,
    onOpenMedia: (url: String) -> Unit,
    onCloseMedia: () -> Unit,
    onDownloadAttachment: suspend (Attachment) -> ByteArray?,
    /** Backs [LocalFileNotice]: shows a short message on the chat screen's snackbar host. */
    onFileNotice: (message: String) -> Unit,
    /** Required, not defaulted, so neither platform's `ChatScreen` can quietly drop the wiring. */
    promptLibraryRevision: Long,
    onRefreshPrompts: () -> Unit,
    /** Required, not defaulted, so neither platform's `ChatScreen` can drop the icons prompt. */
    siteIcons: SiteIconsState,
    onSiteIconsChoice: (show: Boolean) -> Unit,
    onSiteIconsPromptDismiss: () -> Unit,
    /**
     * Backs [LocalOpenMemories]; null hides the memory card's Manage action. Required, not
     * defaulted, so neither platform's `ChatScreen` can quietly drop the wiring.
     */
    onOpenMemories: (() -> Unit)?,
    /** Backs [LocalUserKeyErrors]; null where the keys aren't the reader's (the shared-chat viewer). */
    userKeyErrors: UserKeyErrors?,
    /** Backs [LocalAgentNames]; empty where the reader has no agents list (the shared-chat viewer). */
    agentNames: Map<String, String>,
    /** Backs [LocalMcpSignIn]; null where nothing can sign in (the shared-chat viewer). */
    mcpSignIn: McpSignIn?,
    /**
     * Backs [LocalMcpApps]: the chat's App rows and panel, or [McpApps.ReadOnly] in the shared-chat
     * viewer. Required, not defaulted, so a screen cannot quietly hide every App row.
     */
    mcpApps: McpApps?,
    /**
     * Backs [LocalLegacyUiResources]: what reply `\ui{…}` markers draw. Null where nothing can (the
     * shared viewer, whose config never carries the policy): the markers are stripped all the same.
     */
    legacyUiResources: LegacyUiResources?,
    /** Required, not defaulted, so a screen cannot quietly leave the setting unread again (#586). */
    thinkingOpenByDefault: Boolean,
    /** Backs [LocalCollapseLongUserMessages]. Required for the same reason as [thinkingOpenByDefault]. */
    collapseLongUserMessages: Boolean,
    /** Backs [LocalToolContent]: fetches previewed tool calls in full. Null where nothing is previewed. */
    toolContent: ToolContentHost? = null,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalMcpApps provides mcpApps,
        LocalLegacyUiResources provides legacyUiResources,
    ) {
        ChatRootContent(
            inlineArtifactPrefs = inlineArtifactPrefs,
            mermaidRenderCache = mermaidRenderCache,
            parsedMarkdownCache = parsedMarkdownCache,
            subagentProgress = subagentProgress,
            ptcTraces = ptcTraces,
            previewOverrides = previewOverrides,
            conversationId = conversationId,
            subagentThreadsSupported = subagentThreadsSupported,
            mediaPreview = mediaPreview,
            onOpenMedia = onOpenMedia,
            onCloseMedia = onCloseMedia,
            onDownloadAttachment = onDownloadAttachment,
            onFileNotice = onFileNotice,
            promptLibraryRevision = promptLibraryRevision,
            onRefreshPrompts = onRefreshPrompts,
            siteIcons = siteIcons,
            onSiteIconsChoice = onSiteIconsChoice,
            onSiteIconsPromptDismiss = onSiteIconsPromptDismiss,
            onOpenMemories = onOpenMemories,
            userKeyErrors = userKeyErrors,
            agentNames = agentNames,
            mcpSignIn = mcpSignIn,
            thinkingOpenByDefault = thinkingOpenByDefault,
            collapseLongUserMessages = collapseLongUserMessages,
            toolContent = toolContent,
            subagentThreadsSheet = { parentConversationId, focusParentToolCallId, focusThreadId, onDismiss ->
                SubagentThreadsSheet(
                    parentConversationId = parentConversationId,
                    focusParentToolCallId = focusParentToolCallId,
                    focusThreadId = focusThreadId,
                    onDismiss = onDismiss,
                )
            },
            content = content,
        )
    }
}
