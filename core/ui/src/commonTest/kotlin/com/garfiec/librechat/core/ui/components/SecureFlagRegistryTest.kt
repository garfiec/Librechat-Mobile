package com.garfiec.librechat.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecureFlagRegistryTest {

    private class FakeWindow(initial: Boolean = false) : SecureFlagTarget {
        val writes = mutableListOf<Boolean>()
        override var isSecure: Boolean = initial
            set(value) {
                writes += value
                field = value
            }
    }

    private fun registryOf(window: FakeWindow) = SecureFlagRegistry<String> { window }

    @Test
    fun oneHolderSetsTheFlagAndClearsItOnRelease() {
        val window = FakeWindow()
        val registry = registryOf(window)

        registry.acquire(W)
        assertTrue(window.isSecure)
        registry.release(W)
        assertFalse(window.isSecure)
        assertEquals(listOf(true, false), window.writes)
    }

    @Test
    fun theFlagStaysUntilTheLastHolderLetsGo() {
        val window = FakeWindow()
        val registry = registryOf(window)

        registry.acquire(W)
        registry.acquire(W)
        registry.release(W)
        assertTrue(window.isSecure)
        assertEquals(1, registry.size)
        registry.release(W)
        assertFalse(window.isSecure)
        assertEquals(0, registry.size)
        // Set once, cleared once: a second holder doesn't write again.
        assertEquals(listOf(true, false), window.writes)
    }

    @Test
    fun aFlagSomeoneElseSetSurvivesRelease() {
        val window = FakeWindow(initial = true)
        val registry = registryOf(window)

        registry.acquire(W)
        registry.release(W)
        assertTrue(window.isSecure)
        assertEquals(emptyList(), window.writes)
    }

    @Test
    fun releaseWithNoHolderDoesNothing() {
        val window = FakeWindow()
        val registry = registryOf(window)

        registry.release(W)
        assertFalse(window.isSecure)
        // An extra release must not leave the count negative and swallow the next acquire.
        registry.acquire(W)
        assertTrue(window.isSecure)
        registry.release(W)
        assertFalse(window.isSecure)
    }

    @Test
    fun aLaterRoundJudgesTheFlagAfresh() {
        val window = FakeWindow()
        val registry = registryOf(window)

        registry.acquire(W)
        registry.release(W)
        registry.release(W)
        // Someone else turns it on between rounds: this round didn't set it, so it leaves it on.
        window.isSecure = true
        registry.acquire(W)
        registry.release(W)
        assertTrue(window.isSecure)
        assertEquals(0, registry.size)
    }

    @Test
    fun theRegistryKeepsAWindowOnlyWhileItIsHeld() {
        val windows = mapOf("a" to FakeWindow(), "b" to FakeWindow())
        val registry = SecureFlagRegistry<String> { windows.getValue(it) }

        registry.acquire("a")
        registry.acquire("a")
        registry.acquire("b")
        assertEquals(2, registry.size)
        registry.release("a")
        assertTrue(windows.getValue("a").isSecure)
        registry.release("a")
        registry.release("b")
        assertFalse(windows.getValue("a").isSecure)
        assertFalse(windows.getValue("b").isSecure)
        // Nothing left that refers to a window: a destroyed activity's window can be collected.
        assertEquals(0, registry.size)
    }

    private companion object {
        const val W = "w"
    }
}
