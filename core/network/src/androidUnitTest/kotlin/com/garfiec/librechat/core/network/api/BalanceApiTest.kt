package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.Balance
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.network.di.librechatJson
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Pins `GET /api/balance` (issue #534). Upstream's balance is a plain JS number multiplied by
 * fractional token rates and never rounded, so after any spend it is fractional and must not be
 * decoded as an integer type.
 */
class BalanceApiTest {

    private suspend fun getBalanceOrNull(body: String, status: HttpStatusCode = HttpStatusCode.OK): Balance? {
        val engine = MockEngine {
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json(librechatJson) } }
        return BalanceApi(client).getBalance()
    }

    private suspend fun getBalance(body: String): Balance = checkNotNull(getBalanceOrNull(body))

    @Test
    fun `a 204 for a user whose balance tracking is off is null rather than a decode failure`() = runTest {
        assertThat(getBalanceOrNull("", HttpStatusCode.NoContent)).isNull()
    }

    @Test
    fun `a fractional balance decodes`() = runTest {
        val balance = getBalance(
            """{"user":"u1","tokenCredits":12345.678,"autoRefillEnabled":false,"tenantId":"t","__v":0}""",
        )

        assertThat(balance.tokenCredits).isEqualTo(12345.678)
        assertThat(balance.autoRefillEnabled).isFalse()
    }

    @Test
    fun `an integral balance decodes`() = runTest {
        assertThat(getBalance("""{"user":"u1","tokenCredits":20000,"autoRefillEnabled":false}""").tokenCredits)
            .isEqualTo(20000.0)
    }

    @Test
    fun `the auto-refill fields decode when present`() = runTest {
        val balance = getBalance(
            """
            {
              "user": "u1",
              "tokenCredits": 0.25,
              "autoRefillEnabled": true,
              "refillIntervalValue": 30,
              "refillIntervalUnit": "days",
              "lastRefill": "2026-10-12T09:04:11.000Z",
              "refillAmount": 10000.5,
              "__v": 0
            }
            """.trimIndent(),
        )

        assertThat(balance).isEqualTo(
            Balance(
                tokenCredits = 0.25,
                autoRefillEnabled = true,
                refillIntervalValue = 30.0,
                refillIntervalUnit = "days",
                lastRefill = "2026-10-12T09:04:11.000Z",
                refillAmount = 10000.5,
            ),
        )
    }

    @Test
    fun `a fractional startup balance config does not fail the whole config`() {
        val config = librechatJson.decodeFromString<StartupConfig>(
            """{"balance":{"enabled":true,"startBalance":1000.5,"refillAmount":2.5,"refillIntervalValue":1.5}}""",
        )

        assertThat(config.balance?.startBalance).isEqualTo(1000.5)
        assertThat(config.balance?.refillAmount).isEqualTo(2.5)
        assertThat(config.balance?.refillIntervalValue).isEqualTo(1.5)
    }
}
