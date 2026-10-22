package com.garfiec.librechat.core.model

/**
 * The key shape upstream's memory schema validates (`packages/data-schemas/src/schema/memory.ts`).
 * The schema validator has refused any other key on every server version, rc1 included; rc3 only
 * moved the refusal from a bare 500 to a 400 by checking in the route first. Registered in
 * `scripts/mirrors.json`; upstream labels its own client copy a mirror too.
 */
val MEMORY_KEY_PATTERN: Regex = Regex("^[a-z_]+\$")

/**
 * The longest key the memory routes accept (`api/server/routes/memories.js`, and the by-id
 * handlers in `packages/api/src/memory/handlers.ts`). Enforced on every version. Registered in
 * `scripts/mirrors.json`.
 */
const val MEMORY_KEY_MAX_LENGTH: Int = 1000

/**
 * A key the storage layer refuses to write, compared case-insensitively
 * (`packages/data-schemas/src/methods/memory.ts`: `createMemory`, `setMemory`, `setMemoryById`).
 * The methods answer `{ ok: false }` with no reason, so the routes word it as something else: a
 * 500 ("Failed to create memory." and the like) on create and on the key route, and on the by-id
 * PATCH a 404 `Memory not found.`, which reads as a row deleted elsewhere. It is caught here
 * instead. Registered in `scripts/mirrors.json`.
 */
const val MEMORY_RESERVED_KEY: String = "nothing"

/** Why a memory key would be refused. Null means it is usable. */
enum class MemoryKeyProblem {
    REQUIRED,
    TOO_LONG,
    PATTERN,
    RESERVED,
    DUPLICATE,
}

/**
 * Port of upstream's `getMemoryKeyError` (`client/src/utils/memory.ts`), widened to the server's
 * own checks: blank, then length, then shape, then the reserved key, then a duplicate in the same
 * partition.
 *
 * A key equal to [originalKey] passes once it is non-blank: an unchanged key is never sent
 * (`UpdateMemoryRequest.key` is omitted), so nothing about it can be refused, and a legacy key the
 * pattern now rejects must still let its value be edited.
 *
 * Nothing here is version-gated: every rule is one every server enforces, so a key refused here is
 * one the server would refuse too (older ones with a 500 that says nothing useful).
 *
 * The duplicate check is partition-aware because an agent-scoped memory and a personal one may
 * share a key, and mobile only ever creates personal ones ([agentId] null).
 */
fun memoryKeyProblem(
    key: String,
    memories: List<Memory>,
    agentId: String? = null,
    originalKey: String? = null,
): MemoryKeyProblem? {
    val trimmed = key.trim()
    if (trimmed.isEmpty()) return MemoryKeyProblem.REQUIRED
    if (trimmed == originalKey) return null
    if (trimmed.length > MEMORY_KEY_MAX_LENGTH) return MemoryKeyProblem.TOO_LONG
    if (!MEMORY_KEY_PATTERN.matches(trimmed)) return MemoryKeyProblem.PATTERN
    if (trimmed.equals(MEMORY_RESERVED_KEY, ignoreCase = true)) return MemoryKeyProblem.RESERVED
    val taken = memories.any { it.key == trimmed && it.agentId == agentId }
    return if (taken) MemoryKeyProblem.DUPLICATE else null
}
