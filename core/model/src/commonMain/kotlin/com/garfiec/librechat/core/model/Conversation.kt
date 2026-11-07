package com.garfiec.librechat.core.model

import com.garfiec.librechat.core.model.code.CodeWorkspaceSelection
import com.garfiec.librechat.core.model.media.ProviderId
import com.garfiec.librechat.core.model.media.iconEndpoint
import com.garfiec.librechat.core.model.media.isImageUrl
import com.garfiec.librechat.core.model.media.resolveIconSource
import com.garfiec.librechat.core.model.media.resolveProviderIcon
import com.garfiec.librechat.core.model.pullrequest.isCodeConversation
import com.garfiec.librechat.core.model.serializer.LenientInstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.time.Instant

@Serializable
data class Conversation(
    val conversationId: String? = null,
    val title: String? = "New Chat",
    val user: String? = null,
    val endpoint: String? = null,
    val endpointType: String? = null,
    val model: String? = null,
    @SerialName("agent_id") val agentId: String? = null,
    @SerialName("assistant_id") val assistantId: String? = null,
    val tags: List<String> = emptyList(),
    val isArchived: Boolean = false,
    /**
     * When the conversation was archived (v0.8.8-rc2), or null when it is not archived — the
     * server nulls it on unarchive and preserves the original stamp when an already-archived
     * conversation is archived again.
     *
     * Not cached: the archived list renders from Room, ordered by `updatedAt`, so this is present
     * only on rows read straight off the wire.
     */
    @Serializable(with = LenientInstantSerializer::class)
    val archivedAt: Instant? = null,
    /**
     * Whether a shared link currently exists for this conversation.
     *
     * Derived per **list** request from one batched SharedLink lookup and deliberately NOT
     * persisted, so it is absent from single-conversation payloads and from anything read back
     * out of the local cache. The whole pass is also skipped when `ALLOW_SHARED_LINKS` is off,
     * and a lookup failure drops the flag rather than failing the list — so null means "not
     * known here", never "not shared", and nothing may render an absence as a negative.
     */
    val isShared: Boolean? = null,
    val temperature: Double? = null,
    // One field per upstream spelling (`tConversationSchema`), as on [Preset].
    @SerialName("top_p") val topPSnake: Double? = null,
    @SerialName("topP") val topPCamel: Double? = null,
    val topK: Int? = null,
    @SerialName("frequency_penalty") val frequencyPenalty: Double? = null,
    @SerialName("presence_penalty") val presencePenalty: Double? = null,
    @SerialName("max_tokens") val maxTokensSnake: Int? = null,
    @SerialName("maxTokens") val maxTokensCamel: Int? = null,
    val maxOutputTokens: Int? = null,
    val maxContextTokens: Int? = null,
    val system: String? = null,
    @SerialName("reasoning_effort") val reasoningEffort: String? = null,
    /** Reasoning mode for Responses-API models (sibling of [reasoningEffort]). */
    @SerialName("reasoning_mode") val reasoningMode: String? = null,
    /** Reasoning context carried alongside [reasoningMode] for Responses-API models. */
    @SerialName("reasoning_context") val reasoningContext: String? = null,
    val effort: String? = null,
    @SerialName("thinkingLevel") val thinkingLevel: String? = null,
    val thinkingDisplay: String? = null,
    val stop: List<String>? = null,
    val iconURL: String? = null,
    val greeting: String? = null,
    val spec: String? = null,
    val tools: List<String>? = null,
    @SerialName("web_search") val webSearch: Boolean? = null,
    /** Google Gemini "URL Context" grounding (v0.8.7). Google-only, sibling of [webSearch]. */
    @SerialName("url_context") val urlContext: Boolean? = null,
    /** Anthropic prompt-cache duration: `"5m"` | `"1h"` (v0.8.7). Persisted per-conversation. */
    val promptCacheTtl: String? = null,
    val promptPrefix: String? = null,
    val modelLabel: String? = null,
    val chatGptLabel: String? = null,
    @SerialName("reasoning_summary") val reasoningSummary: String? = null,
    val verbosity: String? = null,
    val useResponsesApi: Boolean? = null,
    val disableStreaming: Boolean? = null,
    val thinking: Boolean? = null,
    val thinkingBudget: Int? = null,
    val promptCache: Boolean? = null,
    val imageDetail: String? = null,
    val fileTokenLimit: Int? = null,
    val region: String? = null,
    val resendFiles: Boolean? = null,
    /** Whether the conversation is pinned to the top of the list (v0.8.7). */
    val pinned: Boolean? = null,
    /** Chat Project (folder) this conversation is assigned to, or null if unassigned (v0.8.7). */
    val chatProjectId: String? = null,
    /** True for a temporary chat (v0.8.6): not persisted to normal history and
     *  expired/cleaned up server-side after [expiredAt]. Server-derived. */
    val isTemporary: Boolean? = null,
    /**
     * The user set the title (v0.8.9-rc1 title ownership): a generated title no longer replaces it.
     * Server-derived; absent on older servers.
     */
    val titleSetByUser: Boolean? = null,
    /** Bumped on every title write (v0.8.9-rc1); orders competing title saves server-side. */
    val titleRevision: Int? = null,
    /** Expiry for a temporary chat, computed server-side from the interface
     *  `temporaryChatRetention` setting. Null for permanent chats. */
    @Serializable(with = LenientInstantSerializer::class)
    val expiredAt: Instant? = null,
    @Serializable(with = LenientInstantSerializer::class)
    val createdAt: Instant? = null,
    @Serializable(with = LenientInstantSerializer::class)
    val updatedAt: Instant? = null,
    /**
     * Read state (v0.8.9-rc1), server-owned. When the newest reply was stamped, or the synthetic
     * marker "Mark as unread" stamped on a chat with no reply yet. See [isUnseen].
     */
    @Serializable(with = LenientInstantSerializer::class)
    val lastResponseAt: Instant? = null,
    /** The reply [lastResponseAt] names; absent for a manual marker. */
    val lastResponseMessageId: String? = null,
    /** True only while [lastResponseAt] is the synthetic "Mark as unread" marker. */
    val lastResponseIsManual: Boolean? = null,
    /**
     * The chat's sealed code-workspace decision: `attached` or `without_attached`, recorded with its
     * first coding turn. Server-owned; read off the single-conversation GET, not cached.
     */
    val codeEnvironmentMode: String? = null,
    /** The attached workspaces that decision selected, one per machine. */
    val codeWorkspaces: List<CodeWorkspaceSelection>? = null,
    /** The code approval mode the chat's last turn ran under (`ask`, `acceptEdits`, `fullAccess`). */
    val codeApprovalMode: String? = null,
    /**
     * Set on `GET /api/convos` list rows (v0.8.9-rc1) when the replica that listed them projected the
     * code fields: a row with this and neither field is an ordinary chat. Absent = not known.
     */
    val codeDecisionListed: Boolean? = null,
    /** Tri-state: false when a reply was stamped, true when hand-marked unread, null on legacy rows. */
    val isMarkedUnread: Boolean? = null,
    /** When the user caught up; [UNSEEN_REPLY_WATERMARK] when a reply landed unseen. */
    @Serializable(with = LenientInstantSerializer::class)
    val lastSeenAt: Instant? = null,
    /**
     * Local only: the [updatedAt] this conversation had when its cached parameters were read off
     * the server. The list route sends no parameters, so a row it refreshed keeps older ones.
     */
    @Transient
    val parametersAsOf: Instant? = null,
    /**
     * Local only: the [isCodeConversation] verdict the cache holds (`conversations.isCodeConversation`),
     * or the one a full single-conversation read settled. Null = not known.
     */
    @Transient
    val cachedIsCodeConversation: Boolean? = null,
)

/**
 * Whether this chat ran against an attached code workspace, so it can have a pull request: true or
 * false when known, null when nothing says (an older list row, a row cached before the column).
 * The code fields win, then a list row's [Conversation.codeDecisionListed] stamp, then the cache.
 */
val Conversation.isCodeConversation: Boolean?
    get() = when {
        isCodeConversation(codeEnvironmentMode, codeWorkspaces?.size ?: 0) -> true
        codeDecisionListed == true -> false
        else -> cachedIsCodeConversation
    }

/**
 * This conversation as the single-conversation GET returned it: the full document, so the absence of
 * the code fields means an ordinary chat and [isCodeConversation] is settled either way.
 */
fun Conversation.asFullDocument(): Conversation =
    copy(cachedIsCodeConversation = isCodeConversation(codeEnvironmentMode, codeWorkspaces?.size ?: 0))

/**
 * Resolves the icon URL to display for this conversation, as web's `EndpointIcon`: an image
 * [iconURL] (a spec's or an agent thread's) wins; otherwise the image [EndpointConfig.iconURL] of
 * the endpoint [iconEndpoint] names. Either is joined to [serverUrl] when it is a rooted, bare or
 * protocol-relative path, as the picker's icons are ([resolveIconSource]). Returns null when no
 * image is available (a configured `iconURL` naming a brand reaches the row as [iconProvider]):
 * callers fall through to the brand glyph or Material fallback.
 */
fun Conversation.resolveEndpointIconUrl(
    endpointConfigs: Map<String, EndpointConfig>,
    serverUrl: String,
): String? {
    val icon = iconURL?.takeIf(::isImageUrl)
        ?: iconEndpoint(endpointConfigs)?.let { endpointConfigs[it]?.iconURL }?.takeIf(::isImageUrl)
    return icon?.let { resolveIconSource(it, endpoint, serverUrl).imageUrl }
}

/**
 * The endpoint whose glyph stands for this conversation, by [com.garfiec.librechat.core.model.media.iconEndpoint].
 *
 * MIRRORED from `client/src/components/Endpoints/EndpointIcon.tsx` (registered in
 * `scripts/mirrors.json` as `endpoint-icon-rule`).
 */
fun Conversation.iconEndpoint(endpointConfigs: Map<String, EndpointConfig>): String? =
    iconEndpoint(iconURL, endpoint, endpointConfigs)

/**
 * The brand art for this conversation's row: [iconEndpoint]'s by [resolveProviderIcon] (its configured
 * `iconURL` naming a brand, its served `providerId`, its first-class brand, then its name). Null when
 * that endpoint's configured icon is an image, or nothing resolves.
 */
fun Conversation.iconProvider(endpointConfigs: Map<String, EndpointConfig>): ProviderId? =
    resolveProviderIcon(iconEndpoint(endpointConfigs), endpointConfigs).provider

/**
 * MIRRORED from upstream `UNSEEN_REPLY_WATERMARK` (`packages/data-provider/src/config.ts`): the
 * [Conversation.lastSeenAt] a server stamps beside a real reply, which tells a reply that finished
 * unseen from a chat the user marked unread by hand.
 */
val UNSEEN_REPLY_WATERMARK: Instant = Instant.fromEpochMilliseconds(0)

/**
 * MIRRORED from upstream `isConversationUnseen` (`client/src/utils/convos.ts`): the chat has a reply
 * newer than the user's catch-up. A row without [Conversation.lastResponseAt] (an older server, or a
 * chat stamped before read state existed) reads as seen.
 */
val Conversation.isUnseen: Boolean
    get() {
        val response = lastResponseAt ?: return false
        val seen = lastSeenAt ?: return true
        return seen < response
    }

/**
 * MIRRORED from upstream `isFinishedUnseen` (`client/src/components/Conversations/running.ts`): an
 * unseen reply the server stamped, as opposed to one the user marked unread by hand. A missing
 * [Conversation.isMarkedUnread] is legacy, not evidence of a new reply.
 */
val Conversation.isFinishedUnseen: Boolean
    get() = isMarkedUnread == false &&
        lastSeenAt == UNSEEN_REPLY_WATERMARK &&
        lastResponseIsManual != true &&
        isUnseen
