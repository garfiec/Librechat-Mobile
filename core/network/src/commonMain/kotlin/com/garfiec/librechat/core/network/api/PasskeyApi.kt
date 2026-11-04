package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.auth.DeletePasskeyRequest
import com.garfiec.librechat.core.model.auth.Passkey
import com.garfiec.librechat.core.model.auth.PasskeyListResponse
import com.garfiec.librechat.core.model.auth.PasskeyResponse
import com.garfiec.librechat.core.model.auth.RenamePasskeyRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.appendPathSegments
import io.ktor.http.path

/**
 * Passkey management (v0.8.9-rc1, `api/server/routes/auth.js`): list, rename, remove. All need the
 * bearer and answer 404 while `ALLOW_PASSKEY_LOGIN` is off. The registration and sign-in ceremonies
 * are deliberately absent: the server rejects a native app's origin, so they wait on upstream.
 */
class PasskeyApi(
    private val client: HttpClient,
) {
    suspend fun listPasskeys(): List<Passkey> =
        client.get {
            url { path("api/auth/passkey") }
        }.body<PasskeyListResponse>().passkeys

    suspend fun renamePasskey(id: String, name: String): Passkey =
        client.patch {
            url {
                path("api/auth/passkey")
                appendPathSegments(id, encodeSlash = true)
            }
            setBody(RenamePasskeyRequest(name))
        }.body<PasskeyResponse>().passkey

    /**
     * A DELETE with a JSON body. The server's step-up answers 403 (not 401) to a wrong or missing
     * password, so a mistype never triggers a token refresh.
     */
    suspend fun deletePasskey(id: String, password: String?) {
        client.delete {
            url {
                path("api/auth/passkey")
                appendPathSegments(id, encodeSlash = true)
            }
            setBody(DeletePasskeyRequest(password))
        }
    }
}
