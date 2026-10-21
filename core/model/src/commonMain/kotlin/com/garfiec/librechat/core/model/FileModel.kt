package com.garfiec.librechat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FileObject(
    @SerialName("file_id") val fileId: String,
    @SerialName("temp_file_id") val tempFileId: String? = null,
    val filename: String,
    val filepath: String,
    val type: String,
    val bytes: Long,
    val source: String? = null,
    val user: String? = null,
    val conversationId: String? = null,
    val messageId: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    // Deferred office-doc preview + storage/tenant metadata (v0.8.6). All
    // optional/forward-compat; surfaced by the office-preview poll.
    /** Extracted inline preview content (office-doc HTML or plain text). */
    val text: String? = null,
    /**
     * Format of [text]. `"html"` = sanitized full-document HTML safe to inject
     * into the office-artifact iframe; `"text"`/null = plain text that MUST be
     * escaped (rendered via the markdown/escaping path), never injected as HTML.
     */
    val textFormat: String? = null,
    /**
     * Preview lifecycle: `"pending"` (extraction in flight), `"ready"`
     * (text/textFormat populated, or binary/oversized with no text), `"failed"`.
     * null ⇒ treat as `"ready"` (legacy records / files with no preview).
     */
    val status: String? = null,
    /** Short machine-readable failure reason when [status] == `"failed"`. */
    val previewError: String? = null,
    val storageKey: String? = null,
    val storageRegion: String? = null,
    val tenantId: String? = null,
    val metadata: FileMetadata? = null,
)

/** `TFile.metadata` (v0.8.6) — code-sandbox file-cache references. */
@Serializable
data class FileMetadata(
    val fileIdentifier: String? = null,
    val codeEnvRef: CodeEnvRef? = null,
)

/**
 * `CodeEnvRef` (v0.8.6) — typed reference to a file in the code-execution
 * sandbox. Upstream is a discriminated union on [kind] (`skill`/`agent`/`user`)
 * where `version` is required only for `skill`; modeled flat here with
 * everything optional for forward-compat round-trip.
 */
@Serializable
data class CodeEnvRef(
    val kind: String? = null,
    val id: String? = null,
    @SerialName("storage_session_id") val storageSessionId: String? = null,
    @SerialName("file_id") val fileId: String? = null,
    val version: Int? = null,
)

@Serializable
data class FileReference(
    @SerialName("file_id") val fileId: String? = null,
    val filename: String? = null,
    val filepath: String? = null,
    val type: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val bytes: Long? = null,
    val source: String? = null,
)

/**
 * The `source` of a file that exists only as extracted text (upstream `FileSources.text`). It has
 * no stored bytes; a shared link serves its text as `text/plain`.
 */
const val FILE_SOURCE_TEXT = "text"

@Serializable
data class Attachment(
    @SerialName("file_id") val fileId: String? = null,
    val filename: String? = null,
    val filepath: String? = null,
    val type: String? = null,
    val conversationId: String? = null,
    val messageId: String? = null,
    val toolCallId: String? = null,
    /** Epoch millis after which the code sandbox drops the file. Set only on a code-output
     *  download fallback (a record with no [fileId] whose [filepath] points at
     *  `/api/files/code/download/…`); stored records carry `expiredAt`, a different key. */
    val expiresAt: Long? = null,
    /** The agent whose tool call produced this file. The server stamps it on every code output;
     *  null on older servers and on uploads. */
    val agentId: String? = null,
    /** What a stateful code session did to this path in its workspace. Null unless the run used
     *  the stateful execution profile and the tool authored the file. */
    val workspaceChange: WorkspaceChange? = null,
    val width: Int? = null,
    val height: Int? = null,
    /** File size; null means the server did not report it, which is not the same as zero — a
     *  zero-byte file is an empty placeholder and sorts last (see `attachmentSalience`). */
    val bytes: Long? = null,
    /** Deferred office-doc preview lifecycle (v0.8.6): `pending` while the server
     *  extracts HTML, `ready` once [text]/[textFormat] are set, `failed` on error.
     *  Null for ordinary attachments (treated as already-ready). */
    val status: String? = null,
    /** Extracted preview content for a ready office-doc attachment. */
    val text: String? = null,
    /** `"html"` (inject as sanitized HTML) or `"text"`/null (escape — never inject). */
    val textFormat: String? = null,
    /** Short machine-readable reason when [status] == `failed`. */
    val previewError: String? = null,
    /** Web-search sources, present only when [type] == `web_search` (no file_id/filename).
     *  Null for ordinary file attachments. */
    @SerialName("web_search") val webSearch: WebSearchData? = null,
    /** Retrieval citations, present only when [type] == `file_search`. */
    @SerialName("file_search") val fileSearch: FileSearchData? = null,
    /** One memory write, present only when [type] == `memory`. */
    val memory: MemoryArtifactData? = null,
    /** MCP UI resources, present only when [type] == `ui_resources`. Carried, not yet rendered —
     *  see [UiResources]. */
    @SerialName("ui_resources") val uiResources: UiResources? = null,
    /** Where the file is stored; [FILE_SOURCE_TEXT] marks a text-only file. Null when the server omits it. */
    val source: String? = null,
)

/**
 * A stateful code session's change to one workspace path (upstream `WorkspaceChange`,
 * `packages/data-provider/src/schemas.ts`). [operation] is `created` or `updated` on the wire but
 * stays a String, and [path] stays optional, because this decodes inside every `GET /messages`:
 * one record a newer server shapes differently must not fail the whole fetch.
 */
@Serializable
data class WorkspaceChange(
    val profile: String? = null,
    val operation: String? = null,
    val path: String? = null,
)
