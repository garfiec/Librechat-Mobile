package com.garfiec.librechat.core.model.request

import kotlinx.serialization.Serializable

/**
 * Body of `PATCH /api/memories/:key` and `PATCH /api/memories/id/:id`.
 *
 * [key] renames the entry, and is OMITTED (the network `Json` drops a null default), never sent as
 * null or `""`, when the key is unchanged. The by-id route validates any key it receives: blank is a
 * 400, and so is one outside the key pattern, which would refuse an unchanged legacy key
 * (`packages/api/src/memory/handlers.ts` `updateById`). The key route takes the body's key, trimmed,
 * whenever one is present (`bodyKey === undefined ? urlKey : bodyKey.trim()`) and checks it only
 * when it differs from the URL's, so `""` there is a 400 too (`api/server/routes/memories.js`). A new
 * key that collides inside the entry's partition answers 409 on both.
 */
@Serializable
data class UpdateMemoryRequest(
    val value: String,
    val key: String? = null,
)
