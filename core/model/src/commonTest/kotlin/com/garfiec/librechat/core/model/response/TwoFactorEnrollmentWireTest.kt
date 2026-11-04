package com.garfiec.librechat.core.model.response

import com.garfiec.librechat.core.model.error.ServerErrorCode
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The three 200 shapes login and refresh answer from v0.8.9-rc1, and the gate bodies. */
class TwoFactorEnrollmentWireTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val enrollment =
        """{"code":"TWO_FACTOR_ENROLLMENT_REQUIRED","twoFAPending":true,"twoFASetupRequired":true,"tempToken":"setup"}"""

    @Test
    fun loginShapes() {
        val normal = json.decodeFromString<LoginResponse>("""{"token":"jwt","user":{"email":"a@b.c"}}""")
        val challenge = json.decodeFromString<LoginResponse>("""{"twoFAPending":true,"tempToken":"challenge"}""")
        val setup = json.decodeFromString<LoginResponse>(enrollment)

        assertFalse(normal.isTwoFactorSetupRequired)
        assertFalse(challenge.isTwoFactorSetupRequired)
        assertTrue(challenge.twoFAPending)
        // The enrollment answer sets twoFAPending too; isTwoFactorSetupRequired is what tells it apart.
        assertTrue(setup.twoFAPending)
        assertTrue(setup.isTwoFactorSetupRequired)
        assertEquals("setup", setup.tempToken)
    }

    @Test
    fun refreshEnrollmentDecodesWithoutAToken() {
        val setup = json.decodeFromString<RefreshResponse>(enrollment)
        val normal = json.decodeFromString<RefreshResponse>("""{"token":"jwt"}""")

        assertNull(setup.token)
        assertTrue(setup.isTwoFactorSetupRequired)
        assertEquals("setup", setup.tempToken)
        assertFalse(normal.isTwoFactorSetupRequired)
        assertEquals("jwt", normal.token)
    }

    @Test
    fun codeAloneMarksSetup() {
        assertTrue(json.decodeFromString<RefreshResponse>("""{"code":"TWO_FACTOR_ENROLLMENT_REQUIRED"}""").isTwoFactorSetupRequired)
    }

    @Test
    fun setupStepResponses() {
        val confirm = json.decodeFromString<TwoFactorSetupConfirmResponse>(
            """{"backupCodes":["0123456789abcdef0123456789abcdef"],"acknowledgementToken":"ack"}""",
        )
        val ack = json.decodeFromString<TwoFactorSetupAcknowledgeResponse>("""{"finalizationToken":"fin"}""")

        assertEquals(listOf("0123456789abcdef0123456789abcdef"), confirm.backupCodes)
        assertEquals("ack", confirm.acknowledgementToken)
        assertEquals("fin", ack.finalizationToken)
    }

    @Test
    fun enrollmentTokenIsCodeKeyed() {
        assertEquals("setup", ServerErrorCode.twoFactorEnrollmentToken(enrollment))
        assertNull(ServerErrorCode.twoFactorEnrollmentToken("""{"tempToken":"x"}"""))
        assertNull(ServerErrorCode.twoFactorEnrollmentToken("""{"code":"TWO_FACTOR_ENROLLMENT_REQUIRED"}"""))
        assertNull(ServerErrorCode.twoFactorEnrollmentToken("<html>403</html>"))
    }

    @Test
    fun termsNotAcceptedReadsBothEnvelopes() {
        assertTrue(ServerErrorCode.isTermsNotAccepted("""{"code":"terms_not_accepted","message":"m"}"""))
        assertTrue(ServerErrorCode.isTermsNotAccepted("""{"error":{"code":"terms_not_accepted"}}"""))
        assertFalse(ServerErrorCode.isTermsNotAccepted("""{"error":"terms_not_accepted_but_not"}"""))
        assertFalse(ServerErrorCode.isTermsNotAccepted("""{"message":"Illegal request"}"""))
        assertFalse(ServerErrorCode.isTermsNotAccepted(null))
    }
}
