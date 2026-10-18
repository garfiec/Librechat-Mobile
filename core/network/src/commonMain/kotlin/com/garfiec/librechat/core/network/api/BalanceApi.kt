package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.Balance
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.path

class BalanceApi constructor(
    private val client: HttpClient,
) {
    /** Null on a 204, which the server sends when balance tracking is off for this user. */
    suspend fun getBalance(): Balance? {
        val response = client.get {
            url { path("api/balance") }
        }
        return if (response.status == HttpStatusCode.NoContent) null else response.body()
    }
}
