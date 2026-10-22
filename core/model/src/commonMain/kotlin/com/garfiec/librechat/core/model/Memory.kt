package com.garfiec.librechat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Memory(
    val key: String,
    val value: String,
    /**
     * The row's stable server id. Always present on `GET /api/memories` — the query is an
     * unprojected `.lean()` find, at v0.8.8-rc1 as much as rc3 — so its presence says nothing
     * about the server version and must not be used as one.
     *
     * It is the only handle that survives [contentFilterBlocked]: a redaction can blank [key], and a
     * key-addressed route cannot then address the row at all. See `MemoriesApi`'s by-id calls.
     */
    @SerialName("_id") val id: String? = null,
    /**
     * True when the deployment's `filters.memories.pii` policy matched this entry. The server
     * blanks ONLY the matched fields among [key], [value] and [summary] (upstream
     * `memory/protection.ts` `redactStoredMemory`): each matched one comes back as an empty string,
     * not omitted, and the others arrive intact. `pii.fields` may name any subset of the three, so
     * a flagged row can carry its real key with a blank value, or both intact when only the summary
     * matched.
     *
     * So an empty [value] here means "withheld", not "the user stored nothing", and rendering it
     * as an empty row with no explanation is the bug this field exists to prevent. The projection
     * runs on the list route and on the by-id PATCH response (`PATCH /api/memories/id/:id`), so an
     * edit through that route can come back redacted too. The key-addressed `PATCH /:key` and the
     * create route echo the stored record unprojected.
     */
    val contentFilterBlocked: Boolean? = null,
    /** Server-generated precis, when the deployment stores one. Subject to the same redaction. */
    val summary: String? = null,
    /**
     * ISO timestamp of the last write. The schema names this column `updated_at` and defines no
     * creation timestamp at all, so this is the only time a memory row carries.
     */
    @SerialName("updated_at") val updatedAt: String? = null,
    /**
     * Agent this memory is partitioned to, or null for the shared personal pool.
     * Server-side, `tokenLimit`/`totalTokens` usage totals count the shared pool only
     * (entries with a non-null [agentId] are excluded), because the limit applies per
     * partition.
     */
    val agentId: String? = null,
    /**
     * Display name resolved server-side for [agentId], present only when the requester
     * can VIEW that agent. Null for shared-pool entries and for agent-partitioned entries
     * whose agent is no longer visible.
     */
    val agentName: String? = null,
    /**
     * Tokens the server counted for [value] when it was written. Absent on rows written before the
     * column existed; the server's pool total counts a missing one as zero.
     */
    val tokenCount: Int? = null,
)

/** The `preferences` object of `PATCH /api/memories/preferences`, whose sole key is `memories`. */
@Serializable
data class MemoryPreferences(
    @SerialName("memories") val enabled: Boolean = true,
)
