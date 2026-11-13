package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.toSafeError
import com.garfiec.librechat.core.model.VerifyTwoFactorOutcome
import com.google.common.truth.Truth.assertThat
import io.ktor.serialization.JsonConvertException
import org.junit.Test
import java.io.IOException

/**
 * Pins [classifyVerifyFailure] to the backend's 2FA verify contract
 * (`upstream/api/server/controllers/auth/TwoFactorAuthController.js`): only a 401 means the
 * server evaluated the submitted code, and only an undecodable 2xx means it consumed the code
 * without yielding a session — everything else never judged the code at all. Only wire exceptions
 * reach the classifier; commit-path faults are covered by [AuthRepositoryImplVerifyTest].
 */
class VerifyFailureClassificationTest {

    private fun classify(e: Throwable?) = classifyVerifyFailure(e?.toSafeError() ?: Result.Error())

    @Test
    fun `a 401 is the server's verdict on the code`() {
        // Backend: wrong code and expired temp session both answer 401 — the entry is spent.
        val outcome = classify(ApiException(401, "Invalid 2FA code or backup code"))

        assertThat(outcome).isEqualTo(VerifyTwoFactorOutcome.CodeRejected("Invalid 2FA code or backup code"))
    }

    @Test
    fun `a 400 refused the request before evaluating the code`() {
        // Backend: missing tempToken / 2FA-not-enabled answer 400 without touching the code.
        val outcome = classify(ApiException(400, "2FA is not enabled for this user"))

        assertThat(outcome).isEqualTo(VerifyTwoFactorOutcome.NotEvaluated("2FA is not enabled for this user"))
    }

    @Test
    fun `a 429 rate limit never judged the code`() {
        val message = "Too many verification attempts, please try again after 5 minutes."
        val outcome = classify(ApiException(429, message))

        assertThat(outcome).isEqualTo(VerifyTwoFactorOutcome.NotEvaluated(message))
    }

    @Test
    fun `a coded 403 never judged the code and carries its code to the screen`() {
        val body = """{"message":"Tenant context required in strict isolation mode","code":"TENANT_CONTEXT_REQUIRED"}"""
        val exception = ApiException(403, "Tenant context required", body = body, serverAuthored = true)
        val outcome = classify(exception)

        assertThat(outcome).isEqualTo(
            VerifyTwoFactorOutcome.NotEvaluated("Tenant context required", code = "TENANT_CONTEXT_REQUIRED"),
        )
    }

    @Test
    fun `a code outside a 403 is not carried`() {
        val body = """{"code":"TENANT_CONTEXT_REQUIRED"}"""
        val outcome = classify(ApiException(400, "Bad request", body = body))

        assertThat(outcome).isEqualTo(VerifyTwoFactorOutcome.NotEvaluated("Bad request"))
    }

    @Test
    fun `a 5xx is a server fault, not a verdict`() {
        val outcome = classify(ApiException(500, "Something went wrong"))

        assertThat(outcome).isEqualTo(VerifyTwoFactorOutcome.NotEvaluated("Something went wrong"))
    }

    @Test
    fun `an undecodable 2xx body consumed the code without a session`() {
        // Ktor's ContentNegotiation wraps every 2xx body decode failure in JsonConvertException
        // (a ContentConvertException) — this is the actual runtime type, not a bare kotlinx
        // SerializationException. AuthApiLoginTest pins that this is what the wire really throws.
        val outcome = classify(JsonConvertException("Illegal input: Unexpected JSON token"))

        assertThat(outcome).isEqualTo(VerifyTwoFactorOutcome.SessionIncomplete(INCOMPLETE_AUTH_MESSAGE))
    }

    @Test
    fun `a transport failure maps to ConnectionFailure with the message email sign-in shows`() {
        val failure = IOException("connection reset")
        assertThat(classify(failure))
            .isEqualTo(VerifyTwoFactorOutcome.ConnectionFailure(failure.toSafeError().message.orEmpty()))
    }

    @Test
    fun `a missing exception maps to ConnectionFailure`() {
        assertThat(classify(null)).isInstanceOf(VerifyTwoFactorOutcome.ConnectionFailure::class.java)
    }

    @Test
    fun `server text that fails the screen never reaches the code boxes`() {
        listOf("<html><body>Bad Request</body></html>", "See https://gateway.example.com/help").forEach { text ->
            val outcome = classify(ApiException(400, text, serverAuthored = true))
            assertThat((outcome as VerifyTwoFactorOutcome.NotEvaluated).message).doesNotContain(text)
            val rejected = classify(ApiException(401, text, serverAuthored = true))
            assertThat((rejected as VerifyTwoFactorOutcome.CodeRejected).message).doesNotContain(text)
        }
    }

    @Test
    fun `a cross-site 403 carries its code`() {
        val body = """{"message":"Cross-site request rejected","code":"auth_cross_origin"}"""
        val outcome = classify(ApiException(403, "Cross-site request rejected", body = body, serverAuthored = true))
        assertThat(outcome).isEqualTo(
            VerifyTwoFactorOutcome.NotEvaluated("Cross-site request rejected", code = "auth_cross_origin"),
        )
    }
}
