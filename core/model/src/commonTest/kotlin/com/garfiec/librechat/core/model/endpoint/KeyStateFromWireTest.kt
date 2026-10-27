package com.garfiec.librechat.core.model.endpoint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Behavior contract for [KeyState.fromWire] — the canonical wire-string -> KeyState
 * mapper used by both the chat-side `EndpointKeyStatusDelegate` and the
 * settings-side provider-keys ViewModels. Each test pins a fixed `now` so the
 * past/future comparison is deterministic.
 */
class KeyStateFromWireTest {

    private val now: Instant = Instant.parse("2026-05-09T00:00:00Z")

    @Test
    fun nullRawMapsToUnset() {
        val result = KeyState.fromWire(raw = null, now = now)
        assertEquals(KeyState.Unset, result.state)
        assertNull(result.malformedSource)
    }

    @Test
    fun emptyRawMapsToUnset() {
        val result = KeyState.fromWire(raw = "", now = now)
        assertEquals(KeyState.Unset, result.state)
        assertNull(result.malformedSource)
    }

    @Test
    fun neverLiteralMapsToSetWithNeverExpiresTrue() {
        val result = KeyState.fromWire(raw = "never", now = now)
        val set = assertIs<KeyState.Set>(result.state)
        assertTrue(set.neverExpires)
        assertNull(set.expiresAt)
        assertEquals("never", set.wire)
        assertNull(result.malformedSource)
    }

    @Test
    fun futureIsoTimestampMapsToSetWithExpiresAt() {
        val future = Instant.parse("2026-06-01T00:00:00Z")
        val rawWire = future.toString()
        val result = KeyState.fromWire(raw = rawWire, now = now)
        val set = assertIs<KeyState.Set>(result.state)
        assertEquals(false, set.neverExpires)
        assertEquals(future, set.expiresAt)
        assertEquals(rawWire, set.wire)
        assertNull(result.malformedSource)
    }

    @Test
    fun pastIsoTimestampMapsToExpired() {
        val past = Instant.parse("2026-04-01T00:00:00Z")
        val rawWire = past.toString()
        val result = KeyState.fromWire(raw = rawWire, now = now)
        assertEquals(KeyState.Expired(expiresAt = past, wire = rawWire), result.state)
        assertNull(result.malformedSource)
    }

    @Test
    fun timestampEqualToNowMapsToSet() {
        // Strict-less-than boundary: parsed < now -> Expired, otherwise Set.
        // An instant equal to `now` is therefore still considered live at the
        // exact threshold instant. Mirrors the original three call sites'
        // `parsed < Clock.System.now()` check.
        val rawWire = now.toString()
        val result = KeyState.fromWire(raw = rawWire, now = now)
        val set = assertIs<KeyState.Set>(result.state)
        assertEquals(false, set.neverExpires)
        assertEquals(now, set.expiresAt)
        assertEquals(rawWire, set.wire)
        assertNull(result.malformedSource)
    }

    @Test
    fun malformedStringFailsClosedToUnsetAndReportsSource() {
        // Fail-closed: corrupted server output is treated as no-key-set rather than
        // silently rendering as "set, expiry unknown". Real backends emit either ISO
        // 8601 or the literal `"never"`, so this is an edge case for adversarial input.
        val result = KeyState.fromWire(raw = "not-a-timestamp", now = now)
        assertEquals(KeyState.Unset, result.state)
        assertEquals("not-a-timestamp", result.malformedSource)
    }

    @Test
    fun nonIsoNumericStringFailsClosedToUnsetAndReportsSource() {
        val result = KeyState.fromWire(raw = "1700000000", now = now)
        assertEquals(KeyState.Unset, result.state)
        assertEquals("1700000000", result.malformedSource)
    }
}
