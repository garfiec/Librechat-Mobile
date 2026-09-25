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
     * It is the only handle that survives [contentFilterBlocked]: a redaction blanks [key], and a
     * key-addressed route cannot then address the row at all. See `MemoriesApi`'s by-id calls.
     */
    @SerialName("_id") val id: String? = null,
    /**
     * True when the deployment's `filters.memories.pii` policy matched this entry and the server
     * BLANKED the content fields it matched — [key], [value] and [summary] come back as empty
     * strings, not omitted.
     *
     * So an empty [value] here means "withheld", not "the user stored nothing", and rendering it
     * as an empty row with no explanation is the bug this field exists to prevent. The projection
     * runs on the list route and on the PATCH response alike, so an edit can come back redacted
     * too.
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
)

/** The `preferences` object of `PATCH /api/memories/preferences`, whose sole key is `memories`. */
@Serializable
data class MemoryPreferences(
    @SerialName("memories") val enabled: Boolean = true,
)
