package com.garfiec.librechat.core.common.identity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PkceTest {

    @Test
    fun challengeMatchesTheRfc7636AppendixBVector() {
        val pkce = Pkce.of("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", pkce.challenge)
    }

    @Test
    fun verifierUsesOnlyUnreservedCharactersWithinTheAllowedLength() {
        val verifier = Pkce.create().verifier
        assertTrue(verifier.length in 43..128)
        assertTrue(verifier.all { it.isLetterOrDigit() || it in "-._~" })
    }

    @Test
    fun eachAttemptGetsItsOwnVerifierAndState() {
        assertNotEquals(Pkce.create().verifier, Pkce.create().verifier)
        assertNotEquals(Pkce.newState(), Pkce.newState())
    }

    @Test
    fun toStringNeverPrintsTheVerifier() {
        val pkce = Pkce.create()
        assertFalse(pkce.toString().contains(pkce.verifier))
    }
}
