package com.garfiec.librechat.core.model

/**
 * The key shape upstream's memory schema validates
 * (`packages/data-schemas/src/schema/memory.ts`), enforced by the CREATE route from v0.8.8-rc3.
 * Registered in `scripts/mirrors.json`; upstream labels its own client copy a mirror too.
 */
val MEMORY_KEY_PATTERN: Regex = Regex("^[a-z_]+\$")

/** Why a memory key would be refused. Null means it is usable. */
enum class MemoryKeyProblem {
    REQUIRED,
    PATTERN,
    DUPLICATE,
}

/**
 * Port of upstream's `getMemoryKeyError` (`client/src/utils/memory.ts`), in its order: blank, then
 * shape, then — for a key that actually changed — a duplicate in the same partition.
 *
 * [enforcePattern] is the version gate, and it is the caller's job because only the caller knows
 * the server. **False must not block**: rc1 and rc2 accept keys rc3 rejects, so refusing them
 * locally would turn a key those servers take into one the user cannot save. An unknown version
 * counts as "does not enforce" for the same reason — the server's own 400 text already reaches the
 * user, so failing open costs a round trip and failing closed costs a capability.
 *
 * The duplicate check carries no gate: a collision is a conflict on every version. It is
 * partition-aware because an agent-scoped memory and a personal one may share a key, and mobile
 * only ever creates personal ones ([agentId] null).
 */
fun memoryKeyProblem(
    key: String,
    memories: List<Memory>,
    agentId: String? = null,
    originalKey: String? = null,
    enforcePattern: Boolean,
): MemoryKeyProblem? {
    val trimmed = key.trim()
    if (trimmed.isEmpty()) return MemoryKeyProblem.REQUIRED
    if (enforcePattern && !MEMORY_KEY_PATTERN.matches(trimmed)) return MemoryKeyProblem.PATTERN
    if (trimmed == originalKey) return null
    val taken = memories.any { it.key == trimmed && it.agentId == agentId }
    return if (taken) MemoryKeyProblem.DUPLICATE else null
}
