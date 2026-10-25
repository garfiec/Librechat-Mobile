package com.garfiec.librechat.core.model.request

import com.garfiec.librechat.core.model.FileReference
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Sentinel parentMessageId for root messages (no parent). */
const val NO_PARENT = "00000000-0000-0000-0000-000000000000"

@Serializable
data class ChatRequest(
    val text: String,
    val conversationId: String? = null,
    /** Client-minted id for the user message being sent. The server adopts it as the
     *  request message's id and echoes it back in the Final event, so the optimistic
     *  message reconciles by id (matters for temp chats, which merge in-memory and
     *  never round-trip through Room). Mirrors the web client's top-level `messageId`.
     *  Omitted on paths that don't create a new user message (regenerate/continue). */
    val messageId: String? = null,
    val parentMessageId: String,
    val endpoint: String,
    val endpointType: String? = null,
    val modelDisplayLabel: String? = null,
    val model: String? = null,
    @SerialName("agent_id") val agentId: String? = null,
    val isContinued: Boolean = false,
    val isEdited: Boolean = false,
    val isRegenerate: Boolean = false,
    /**
     * Manual context compaction (v0.8.8-rc3): summarize the branch up to [parentMessageId] and end
     * WITHOUT a reply. No user message is created, so the turn is regenerate-shaped **client-side
     * only**: `isRegenerate`, `isContinued`, `editedContent` and [responseMessageId] must all be
     * absent from the body, because `getCompactionRejection` 400s `INVALID_COMPACTION_REQUEST` on
     * any of them arriving beside this flag.
     *
     * Today that holds for two different reasons, and only one of them is a guard.
     * `ChatPayloadBuilder` drops `isRegenerate` and [editedContent] when this is set, the way
     * upstream drops `isRegenerate` (`isRegenerate: compact === true ? undefined : isRegenerate`).
     * `isContinued` and [responseMessageId] are simply never set on the one path that compacts
     * (`compactConversationNow`), so they stay at their defaults and are omitted. A caller that
     * sets one must drop it in the builder too — do not read those lines as covering them.
     *
     * Omitted rather than sent as `false`: a server that does not know the flag would answer an
     * ordinary empty-text turn.
     */
    val compact: Boolean? = null,
    val overrideParentMessageId: String? = null,
    val responseMessageId: String? = null,
    /**
     * An edited assistant content part the new reply continues from (see [EditedContent]). Set
     * only by the assistant-edit rerun, which also sets [responseMessageId] to the edited
     * message's id — the two are a pair and the server drops the prefix without both. Never set
     * beside [compact], which the server refuses.
     */
    val editedContent: EditedContent? = null,
    val temperature: Double? = null,
    @SerialName("top_p") val topP: Double? = null,
    val maxOutputTokens: Int? = null,
    val maxContextTokens: Int? = null,
    val system: String? = null,
    @SerialName("reasoning_effort") val reasoningEffort: String? = null,
    val effort: String? = null,
    @SerialName("thinkingLevel") val thinkingLevel: String? = null,
    val stop: List<String>? = null,
    val tools: List<String>? = null,
    val iconURL: String? = null,
    val greeting: String? = null,
    val spec: String? = null,
    val modelLabel: String? = null,
    val maxTokens: Int? = null,
    val promptPrefix: String? = null,
    val chatGptLabel: String? = null,
    val resendFiles: Boolean? = null,
    val imageDetail: String? = null,
    val key: String? = null,
    val extra: JsonObject? = null,
    @SerialName("web_search") val webSearch: Boolean? = null,
    /**
     * Verbatim excerpts the user referenced via "Add to chat" (v0.8.7, upstream #13868). The
     * server merges them into the user message as Markdown blockquotes for the model and
     * persists them on the message, echoing `message.quotes` for display. Fresh sends drain
     * the staged pending quotes; regenerate and edit-assistant replay the parent user
     * message's persisted quotes (web `overrideQuotes` parity — the server rebuilds the user
     * message from this field); continue and edit-user send none. Never sent to assistants
     * endpoints, which bypass the BaseClient merge.
     */
    val quotes: List<String>? = null,
    /**
     * Names of the skills the user picked for this turn (the `$`/`/` picker's chips). The server
     * primes each one before the model runs and persists the list on the user message. Top level,
     * not inside [ephemeralAgent]. Fresh sends take the pending chips; regenerate and edit replay
     * the user turn's persisted list; continue sends none. Never re-sent on later turns: the
     * server re-primes earlier picks from history. Never sent to assistants endpoints.
     */
    val manualSkills: List<String>? = null,
    val files: List<FileReference>? = null,
    val addedConvo: AddedConversation? = null,
    val ephemeralAgent: EphemeralAgent? = null,
    /** When true, the server marks this conversation temporary (v0.8.6): skips
     *  title generation, keeps it out of normal history, and sets an `expiredAt`
     *  from the interface `temporaryChatRetention`. Sent on the chat request. */
    val isTemporary: Boolean? = null,
    /** IANA timezone id (e.g. "America/New_York") of the user's device (v0.8.7 #13815).
     *  The server threads it into `replaceSpecialVars` so agent `{{current_date}}` /
     *  `{{current_datetime}}` resolve to the user's wall clock instead of the server's.
     *  Always sent (additive; older servers ignore the unknown key). */
    val timezone: String? = null,
    /** Idempotency key minted once per send (upstream #14344, 0.8.8 line). The server claims it
     *  before job creation, so a replayed generation POST (transport retry, reconnect race)
     *  attaches to the original run instead of double-billing a second generation. Distinct from
     *  [messageId] — this keys the *request*, not the user message. Must stay byte-stable across
     *  retries of the same send (minted in `ChatPayloadBuilder.build`, encoded once). Additive;
     *  a server that hasn't claimed it simply ignores the field. */
    val clientRequestId: String? = null,
    /** Chat Project (folder) a NEW conversation is filed into. The server saves it on every turn
     *  that carries it, so it must only ride the first send: on a later turn it would move the
     *  chat. A project the user does not own is dropped silently server-side (no error). */
    val chatProjectId: String? = null,
)
