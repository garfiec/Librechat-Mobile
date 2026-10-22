package com.garfiec.librechat.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The memory-key rule. Every arm is a server rule on every version, so the decision reads off the
 * key, the list and the original key alone.
 */
class MemoryKeyTest {

    private fun memory(key: String, agentId: String? = null) =
        Memory(key = key, value = "v", agentId = agentId)

    @Test
    fun blankIsRefused() {
        assertEquals(MemoryKeyProblem.REQUIRED, problem(""))
        assertEquals(MemoryKeyProblem.REQUIRED, problem("   "))
    }

    /** The schema validator refuses these on every version; rc3 only answers 400 instead of 500. */
    @Test
    fun theShapeIsRefusedWhateverTheServer() {
        assertEquals(MemoryKeyProblem.PATTERN, problem("Likes Tea"))
        assertEquals(MemoryKeyProblem.PATTERN, problem("likes-tea"))
        assertEquals(MemoryKeyProblem.PATTERN, problem("likes_tea_2"))
    }

    @Test
    fun lowercaseAndUnderscoresPass() {
        assertNull(problem("likes_tea"))
        assertNull(problem("tea"))
    }

    @Test
    fun aDuplicateIsRefused() {
        val existing = listOf(memory("likes_tea"))
        assertEquals(MemoryKeyProblem.DUPLICATE, memoryKeyProblem("likes_tea", existing))
    }

    /** An agent-scoped row and a personal one may share a key; only the same partition collides. */
    @Test
    fun theDuplicateCheckIsPartitionAware() {
        val agentScoped = listOf(memory("likes_tea", agentId = "agent_7"))
        assertNull(memoryKeyProblem("likes_tea", agentScoped))
        assertEquals(
            MemoryKeyProblem.DUPLICATE,
            memoryKeyProblem("likes_tea", agentScoped, agentId = "agent_7"),
        )
    }

    /** Re-submitting the key a row already has is not a collision with itself. */
    @Test
    fun anUnchangedKeyIsNotItsOwnDuplicate() {
        val existing = listOf(memory("likes_tea"))
        assertNull(memoryKeyProblem("likes_tea", existing, originalKey = "likes_tea"))
    }

    /** The shape is checked before the duplicate, so a malformed key reports the shape. */
    @Test
    fun theShapeIsReportedBeforeTheDuplicate() {
        val existing = listOf(memory("Likes Tea"))
        assertEquals(MemoryKeyProblem.PATTERN, memoryKeyProblem("Likes Tea", existing))
    }

    @Test
    fun anOverlongKeyIsRefused() {
        val overlong = "a".repeat(MEMORY_KEY_MAX_LENGTH + 1)
        assertEquals(MemoryKeyProblem.TOO_LONG, problem(overlong))
        assertNull(problem("a".repeat(MEMORY_KEY_MAX_LENGTH)))
    }

    /** `nothing` passes the pattern, so the reserved check is its own arm. */
    @Test
    fun theReservedKeyIsRefusedInAnyCase() {
        assertEquals(MemoryKeyProblem.RESERVED, problem("nothing"))
        // Upper case also fails the shape, which is checked first.
        assertEquals(MemoryKeyProblem.PATTERN, problem("Nothing"))
        assertEquals(MemoryKeyProblem.PATTERN, problem("  NOTHING "))
        assertNull(problem("nothing_else"))
    }

    /** An unchanged key is never sent, so a legacy key the pattern rejects stays editable. */
    @Test
    fun anUnchangedLegacyKeyIsNotCheckedAgainstThePattern() {
        val existing = listOf(memory("Likes Tea"))
        assertNull(memoryKeyProblem("Likes Tea", existing, originalKey = "Likes Tea"))
        assertEquals(
            MemoryKeyProblem.PATTERN,
            memoryKeyProblem("Likes Coffee", existing, originalKey = "Likes Tea"),
        )
    }

    /** A rename into a key its own partition holds collides; another partition's does not. */
    @Test
    fun aRenameChecksOnlyItsOwnPartition() {
        val existing = listOf(memory("likes_tea"), memory("likes_coffee", agentId = "agent_7"))
        assertEquals(
            MemoryKeyProblem.DUPLICATE,
            memoryKeyProblem("likes_tea", existing, originalKey = "likes_milk"),
        )
        assertNull(memoryKeyProblem("likes_coffee", existing, originalKey = "likes_milk"))
    }

    private fun problem(key: String) = memoryKeyProblem(key = key, memories = emptyList())
}
