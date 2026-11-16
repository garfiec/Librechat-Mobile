package com.garfiec.librechat.core.data.gate

import com.garfiec.librechat.core.common.identity.deriveServerId
import com.garfiec.librechat.core.data.datastore.SecureValueStore
import com.garfiec.librechat.core.logging.Diag
import com.garfiec.librechat.core.network.api.GateOAuthApi
import com.garfiec.librechat.core.network.api.GateOAuthResult
import com.garfiec.librechat.core.network.api.GateTokens
import com.garfiec.librechat.core.network.client.GateCredential
import com.garfiec.librechat.core.network.client.GateCredentialProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Per-server access-gate credentials (Authelia), in the token store's encrypted storage under
 * `srv:<serverId>:gate`. The HTTP clients read it as a [GateCredentialProvider].
 *
 * **Refresh is single-flight.** Authelia revokes the previous access token on every refresh and
 * revokes the whole token family when an old refresh token is replayed, so two concurrent refreshes
 * would sign the user out of the gate. [lock] is held across the refresh POST, and a caller that
 * finds the token already rotated past the one it used takes the new one instead of refreshing
 * again. One lock for all servers: it also guards [cache], and refreshes are rare.
 */
class GateCredentialStore(
    private val secure: SecureValueStore,
    private val api: GateOAuthApi,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : GateCredentialProvider {

    private val lock = Mutex()
    private val cache = mutableMapOf<String, StoredGate?>()

    private val _signInRequired = MutableStateFlow<Set<String>>(emptySet())

    /** Server ids whose gate credential can't be renewed: the user has to sign in to the gate again. */
    val signInRequired: StateFlow<Set<String>> = _signInRequired.asStateFlow()

    override suspend fun credentialFor(baseUrl: String): GateCredential? {
        val serverId = serverIdOf(baseUrl) ?: return null
        val stored = lock.withLock { load(serverId) } ?: return null
        return when (stored.kind) {
            StoredGate.COOKIE -> stored.cookies?.let(GateCredential::Cookie)
            else -> {
                val token = stored.accessToken ?: return null
                if (stored.expiresSoon()) {
                    refresh(serverId, token) ?: GateCredential.Bearer(token)
                } else {
                    GateCredential.Bearer(token)
                }
            }
        }
    }

    override suspend fun onRejected(baseUrl: String, used: GateCredential): GateCredential? {
        val serverId = serverIdOf(baseUrl) ?: return null
        return when (used) {
            is GateCredential.Cookie -> {
                markSignInRequired(serverId)
                null
            }
            is GateCredential.Bearer -> refresh(serverId, used.accessToken)
        }
    }

    /** What a repeat sign-in needs: where the gate signs in and which client the app used. */
    suspend fun signInDetails(baseUrl: String): GateSignInDetails? {
        val serverId = serverIdOf(baseUrl) ?: return null
        val stored = lock.withLock { load(serverId) } ?: return null
        return GateSignInDetails(
            portalUrl = stored.portalUrl,
            clientId = stored.clientId,
            usesBrowserSignIn = stored.kind != StoredGate.COOKIE,
        )
    }

    suspend fun saveBearer(baseUrl: String, portalUrl: String, clientId: String, tokenEndpoint: String, tokens: GateTokens) {
        val serverId = serverIdOf(baseUrl) ?: return
        lock.withLock {
            save(
                serverId,
                StoredGate(
                    kind = StoredGate.BEARER,
                    portalUrl = portalUrl,
                    clientId = clientId,
                    tokenEndpoint = tokenEndpoint,
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken,
                    expiresAtMillis = tokens.expiresInSeconds?.let { now() + it * MILLIS_PER_SECOND },
                    lifetimeMillis = tokens.expiresInSeconds?.let { it * MILLIS_PER_SECOND },
                ),
            )
        }
    }

    suspend fun saveCookie(baseUrl: String, portalUrl: String, cookies: String) {
        val serverId = serverIdOf(baseUrl) ?: return
        lock.withLock {
            save(serverId, StoredGate(kind = StoredGate.COOKIE, portalUrl = portalUrl, cookies = cookies))
        }
    }

    /** Reads [baseUrl]'s record so [signInRequired] reflects it after a restart. */
    suspend fun warm(baseUrl: String) {
        val serverId = serverIdOf(baseUrl) ?: return
        lock.withLock { load(serverId) }
    }

    // NonCancellable: once the POST is out, Authelia has rotated the refresh token; a cancelled caller
    // dropping the answer would leave a revoked token stored, and replaying it revokes the family.
    private suspend fun refresh(serverId: String, usedAccessToken: String): GateCredential.Bearer? =
        lock.withLock {
            withContext(NonCancellable) { refreshLocked(serverId, usedAccessToken) }
        }

    /** Callers hold [lock]. */
    private suspend fun refreshLocked(serverId: String, usedAccessToken: String): GateCredential.Bearer? {
        val stored = load(serverId) ?: return null
        val current = stored.accessToken
        if (current != null && current != usedAccessToken && !stored.expiresSoon()) {
            return GateCredential.Bearer(current)
        }
        val refreshToken = stored.refreshToken
        val endpoint = stored.tokenEndpoint
        val clientId = stored.clientId
        if (refreshToken == null || endpoint == null || clientId == null) {
            save(serverId, stored.signedOut())
            return null
        }
        val result = try {
            api.token(
                endpoint,
                mapOf(
                    "grant_type" to "refresh_token",
                    "refresh_token" to refreshToken,
                    "client_id" to clientId,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Offline or the portal is down: keep the tokens, the next request tries again.
            Diag.w("Gate", throwable = e) { "gate token refresh failed in transit" }
            return null
        }
        return when (result) {
            is GateOAuthResult.Ok -> {
                val tokens = result.value
                val renewed = stored.copy(
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken ?: refreshToken,
                    expiresAtMillis = tokens.expiresInSeconds?.let { now() + it * MILLIS_PER_SECOND },
                    lifetimeMillis = tokens.expiresInSeconds?.let { it * MILLIS_PER_SECOND },
                    needsSignIn = false,
                )
                save(serverId, renewed)
                GateCredential.Bearer(tokens.accessToken)
            }
            is GateOAuthResult.Refused -> {
                Diag.w("Gate", attrs = mapOf("status" to result.status.toString(), "error" to result.error)) {
                    "gate token refresh refused"
                }
                if (result.status in CLIENT_ERRORS) save(serverId, stored.signedOut())
                null
            }
        }
    }

    /** Callers hold [lock]. */
    private fun publish(serverId: String, stored: StoredGate) {
        cache[serverId] = stored
        _signInRequired.update { if (stored.needsSignIn) it + serverId else it - serverId }
    }

    private suspend fun markSignInRequired(serverId: String) {
        lock.withLock {
            val stored = load(serverId) ?: return@withLock
            save(serverId, stored.signedOut())
        }
    }

    /** Callers hold [lock]. */
    private suspend fun load(serverId: String): StoredGate? {
        if (cache.containsKey(serverId)) return cache[serverId]
        val stored = withContext(ioDispatcher) {
            secure.readSecure(keyFor(serverId))?.let { raw ->
                runCatching { json.decodeFromString<StoredGate>(raw) }.getOrNull()
            }
        }
        cache[serverId] = stored
        if (stored != null) publish(serverId, stored)
        return stored
    }

    private suspend fun save(serverId: String, stored: StoredGate) {
        withContext(ioDispatcher) { secure.writeSecure(keyFor(serverId), json.encodeToString(stored)) }
        publish(serverId, stored)
    }

    /**
     * Within the last quarter of the token's life, capped at a minute: a fixed minute would renew on
     * every request once an admin sets access tokens that short.
     */
    private fun StoredGate.expiresSoon(): Boolean {
        val expiresAt = expiresAtMillis ?: return false
        val lifetime = lifetimeMillis ?: (RENEW_AHEAD_MILLIS * LIFETIME_QUARTERS)
        val ahead = minOf(RENEW_AHEAD_MILLIS, lifetime / LIFETIME_QUARTERS)
        return expiresAt - now() <= ahead
    }

    private fun serverIdOf(baseUrl: String): String? =
        baseUrl.takeIf { it.isNotBlank() }?.let { runCatching { deriveServerId(it).value }.getOrNull() }

    private fun keyFor(serverId: String) = "srv:$serverId:gate"

    private companion object {
        const val RENEW_AHEAD_MILLIS = 60_000L
        const val LIFETIME_QUARTERS = 4L
        const val MILLIS_PER_SECOND = 1_000L
        val CLIENT_ERRORS = 400..499
    }
}

/** What a repeat gate sign-in needs, without the secrets. */
data class GateSignInDetails(val portalUrl: String, val clientId: String?, val usesBrowserSignIn: Boolean)

@Serializable
internal data class StoredGate(
    val kind: String,
    val portalUrl: String,
    val clientId: String? = null,
    val tokenEndpoint: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresAtMillis: Long? = null,
    val lifetimeMillis: Long? = null,
    val cookies: String? = null,
    val needsSignIn: Boolean = false,
) {
    /** The tokens are dead: keep where and how to sign in again, drop the secrets. */
    fun signedOut(): StoredGate =
        copy(accessToken = null, refreshToken = null, expiresAtMillis = null, cookies = null, needsSignIn = true)

    override fun toString(): String = "StoredGate(kind=$kind, portalUrl=$portalUrl, needsSignIn=$needsSignIn)"

    companion object {
        const val BEARER = "bearer"
        const val COOKIE = "cookie"
    }
}
