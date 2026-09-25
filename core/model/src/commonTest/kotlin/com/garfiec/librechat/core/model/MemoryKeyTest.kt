package com.garfiec.librechat.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The memory-key rule, which is a version gate as much as a pattern: the same key is legal on rc2
 * and refused on rc3, so the decision cannot be read off the string alone.
 */
class MemoryKeyTest {

    private fun memory(key: String, agentId: String? = null) =
        Memory(key = key, value = "v", agentId = agentId)

    @Test
    fun blankIsRefusedWhateverTheServer() {
        assertEquals(MemoryKeyProblem.REQUIRED, problem("", enforcePattern = true))
        assertEquals(MemoryKeyProblem.REQUIRED, problem("   ", enforcePattern = false))
    }

    @Test
    fun theShapeIsOnlyRefusedWhereTheServerEnforcesIt() {
        // rc3 rejects these; rc1 and rc2 accept them, so refusing locally would withhold a key
        // those servers take.
        assertEquals(MemoryKeyProblem.PATTERN, problem("Likes Tea", enforcePattern = true))
        assertEquals(MemoryKeyProblem.PATTERN, problem("likes-tea", enforcePattern = true))
        assertEquals(MemoryKeyProblem.PATTERN, problem("likes_tea_2", enforcePattern = true))
        assertNull(problem("Likes Tea", enforcePattern = false))
        assertNull(problem("likes_tea_2", enforcePattern = false))
    }

    @Test
    fun lowercaseAndUnderscoresPass() {
        assertNull(problem("likes_tea", enforcePattern = true))
        assertNull(problem("tea", enforcePattern = true))
    }

    /** A collision is a conflict on every version, so this arm carries no gate. */
    @Test
    fun aDuplicateIsRefusedOnEveryServer() {
        val existing = listOf(memory("likes_tea"))
        assertEquals(
            MemoryKeyProblem.DUPLICATE,
            memoryKeyProblem("likes_tea", existing, enforcePattern = true),
        )
        assertEquals(
            MemoryKeyProblem.DUPLICATE,
            memoryKeyProblem("likes_tea", existing, enforcePattern = false),
        )
    }

    /** An agent-scoped row and a personal one may share a key; only the same partition collides. */
    @Test
    fun theDuplicateCheckIsPartitionAware() {
        val agentScoped = listOf(memory("likes_tea", agentId = "agent_7"))
        assertNull(memoryKeyProblem("likes_tea", agentScoped, enforcePattern = true))
        assertEquals(
            MemoryKeyProblem.DUPLICATE,
            memoryKeyProblem("likes_tea", agentScoped, agentId = "agent_7", enforcePattern = true),
        )
    }

    /** Re-submitting the key a row already has is not a collision with itself. */
    @Test
    fun anUnchangedKeyIsNotItsOwnDuplicate() {
        val existing = listOf(memory("likes_tea"))
        assertNull(
            memoryKeyProblem("likes_tea", existing, originalKey = "likes_tea", enforcePattern = true),
        )
    }

    /** The shape is checked before the duplicate, so a malformed key reports the shape. */
    @Test
    fun theShapeIsReportedBeforeTheDuplicate() {
        val existing = listOf(memory("Likes Tea"))
        assertEquals(
            MemoryKeyProblem.PATTERN,
            memoryKeyProblem("Likes Tea", existing, enforcePattern = true),
        )
    }

    private fun problem(key: String, enforcePattern: Boolean) =
        memoryKeyProblem(key = key, memories = emptyList(), enforcePattern = enforcePattern)
}
