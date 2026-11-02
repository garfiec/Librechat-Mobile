package com.garfiec.librechat.core.model

import com.garfiec.librechat.core.model.media.ProviderId
import com.garfiec.librechat.core.model.media.isImageUrl
import com.garfiec.librechat.core.model.media.resolveIconSource
import com.garfiec.librechat.core.model.media.resolveProviderIcon
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
    /** Expiry for a temporary chat, computed server-side from the interface
     *  `temporaryChatRetention` setting. Null for permanent chats. */
    @Serializable(with = LenientInstantSerializer::class)
    val expiredAt: Instant? = null,
    @Serializable(with = LenientInstantSerializer::class)
    val createdAt: Instant? = null,
    @Serializable(with = LenientInstantSerializer::class)
    val updatedAt: Instant? = null,
    /**
     * Local only: the [updatedAt] this conversation had when its cached parameters were read off
     * the server. The list route sends no parameters, so a row it refreshed keeps older ones.
     */
    @Transient
    val parametersAsOf: Instant? = null,
)

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
 * The endpoint whose glyph stands for this conversation, as web's `getIconEndpoint`: a spec may store
 * an endpoint's key in [iconURL], which wins over [endpoint] only when [endpointConfigs] lists that
 * key. Any other [iconURL] (an image, a bare provider name like `"groq"`, blank) leaves [endpoint].
 *
 * MIRRORED from `client/src/components/Endpoints/EndpointIcon.tsx` (registered in
 * `scripts/mirrors.json` as `endpoint-icon-rule`).
 */
fun Conversation.iconEndpoint(endpointConfigs: Map<String, EndpointConfig>): String? =
    iconURL?.takeIf { it.isNotEmpty() && it in endpointConfigs } ?: endpoint

/**
 * The brand art for this conversation's row: [iconEndpoint]'s by [resolveProviderIcon] (its configured
 * `iconURL` naming a brand, its served `providerId`, its first-class brand, then its name). Null when
 * that endpoint's configured icon is an image, or nothing resolves.
 */
fun Conversation.iconProvider(endpointConfigs: Map<String, EndpointConfig>): ProviderId? =
    resolveProviderIcon(iconEndpoint(endpointConfigs), endpointConfigs).provider
