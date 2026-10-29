package com.garfiec.librechat.core.data.repository

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.data.datastore.AccountRoster
import com.garfiec.librechat.core.model.auth.RequestAuth
import com.garfiec.librechat.core.network.api.AuthApi
import com.garfiec.librechat.core.network.client.PendingRequestIdentity
import com.garfiec.librechat.core.network.client.RefreshCookieCredentials
import com.garfiec.librechat.core.network.client.RequestIdentity
import com.garfiec.librechat.core.network.client.ServerHeadersProvider
import com.garfiec.librechat.core.network.client.TokenManager
import kotlinx.coroutines.withContext

/**
 * Deletes one account's session on its server, for logout and account removal.
 *
 * The server scopes logout to the session its `refreshToken` cookie names and, with none, deletes an
 * arbitrary session of the same user, so an account with no stored refresh token sends nothing.
 * Logout sits behind JWT auth and a pinned identity is never refreshed on a 401, so a stale bearer is
 * renewed first, and the refresh token is read after that because the renewal rotates it.
 */
class ServerSessionRevoker(
    private val authApi: AuthApi,
    private val tokenManager: TokenManager,
    private val refreshCookieCredentials: RefreshCookieCredentials,
    private val roster: AccountRoster,
    private val serverHeadersProvider: ServerHeadersProvider,
) {

    /** Revokes [identity]'s session. Throws the request's failure; callers run inside an API-call wrapper. */
    suspend fun revoke(identity: RequestIdentity) {
        val accountId = identity.accountId
        if (accountId == null || refreshCookieCredentials.refreshTokenFor(accountId) == null) {
            Logger.i { "No refresh token for the signed-out account; skipping server-side revocation" }
            return
        }
        val bearer = tokenManager.ensureFreshAccessToken(
            accountId = accountId,
            baseUrl = identity.baseUrl,
            currentAccessToken = identity.bearer,
        )
        val session = refreshCookieCredentials.sessionFor(accountId)
        val cookies = session?.cookies ?: refreshCookieCredentials.authCookiesFor(accountId) ?: return
        val currentBearer = session?.accessToken ?: bearer
        withContext(
            PendingRequestIdentity(
                baseUrl = identity.baseUrl,
                headers = { identity.customHeaders },
                auth = { RequestAuth(currentBearer, cookies.openId) },
            ),
        ) { authApi.logout(cookies) }
    }

    /**
     * Revokes the session of [accountId] as the roster records it, for an account that is not the
     * active one: its own server, bearer and gateway headers rather than the live identity's.
     */
    suspend fun revokeStored(accountId: String) {
        val entry = roster.snapshot().entries.firstOrNull { it.accountId == accountId } ?: return
        serverHeadersProvider.awaitWarm()
        revoke(
            RequestIdentity(
                baseUrl = entry.serverUrl,
                accountId = accountId,
                bearer = tokenManager.getAccessTokenFor(accountId),
                customHeaders = serverHeadersProvider.headersFor(entry.serverUrl),
            ),
        )
    }
}
