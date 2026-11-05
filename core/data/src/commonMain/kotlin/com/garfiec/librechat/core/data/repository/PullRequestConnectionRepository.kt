package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.mcp.McpOAuthLaunch
import com.garfiec.librechat.core.model.pullrequest.PullRequestConnection
import com.garfiec.librechat.core.network.api.PullRequestsApi
import kotlinx.coroutines.flow.first

/** Why a connection could not be started. */
enum class PullRequestConnectFailure {
    /** 404 `NOT_CONFIGURED`: this server offers no account to connect (for this user). */
    NOT_CONFIGURED,

    /** Anything else, including a response that set no binding cookie (the callback would refuse it). */
    FAILED,
}

/** A started connection: the provider page to open in the in-app sign-in, with its binding cookies. */
data class PullRequestConnectLaunch(val launch: McpOAuthLaunch.InApp, val flowId: String?)

/**
 * The user's own account for pull request lookups (v0.8.9-rc1, upstream `ConnectedAccounts`):
 * status, connect, disconnect. Network-direct, never cached: the grant lives on the server, and a
 * connect made on the web counts here too.
 *
 * Every status read is compared with the credential the active account's pull requests were last
 * looked up under (upstream `syncPullRequestConnection`); a change, a disconnect included, makes
 * [PullRequestRepository] ask them again.
 */
class PullRequestConnectionRepository(
    private val api: PullRequestsApi,
    private val pullRequests: PullRequestRepository,
    private val configRepository: ConfigRepository,
    private val activeAccountProvider: ActiveAccountProvider,
) {
    /** Per account, the credential its pull requests were last refreshed under. Main-confined. */
    private val credentialByAccount = mutableMapOf<String, String>()

    /** Per account, the provider its last status named, for the credential a disconnect leaves. Main-confined. */
    private val providerByAccount = mutableMapOf<String, String?>()

    suspend fun status(): Result<PullRequestConnection> {
        val account = activeAccountProvider.currentAccountId()?.value
        val result = safeApiCall { api.connectionStatus() }
        if (result is Result.Success && account != null && activeAccountProvider.currentAccountId()?.value == account) {
            providerByAccount[account] = result.data.provider
            sync(account, credentialOf(result.data))
        }
        return result
    }

    /**
     * Asks the server for the provider page. Opened in the in-app WebView only: the callback needs
     * the cookies this response set, which no other browser holds, so a response without them is a
     * failure rather than a page that is bound to fail.
     */
    suspend fun connect(providerName: String): Result<PullRequestConnectLaunch> =
        when (val result = safeApiCall { api.connect() }) {
            is Result.Success -> {
                val (start, cookies) = result.data
                if (cookies.isEmpty() || start.url.isBlank()) {
                    Result.Error(message = null)
                } else {
                    Result.Success(
                        PullRequestConnectLaunch(
                            launch = McpOAuthLaunch.InApp(
                                serverName = providerName,
                                authorizationUrl = start.url,
                                serverUrl = configRepository.serverUrl.first(),
                                seedCookies = cookies,
                            ),
                            flowId = start.flowId,
                        ),
                    )
                }
            }
            is Result.Error -> result
            Result.Loading -> Result.Error(message = null)
        }

    /** Removes every grant. A disconnect always leaves the server's own token, so lookups are asked again. */
    suspend fun disconnect(): Result<Unit> {
        val account = activeAccountProvider.currentAccountId()?.value
        val result = safeApiCall { api.disconnect() }
        if (result is Result.Success && account != null && activeAccountProvider.currentAccountId()?.value == account) {
            // Upstream: the server's token for the provider last seen, and always a refetch.
            credentialByAccount[account] = serverCredential(providerByAccount[account])
            pullRequests.invalidate()
        }
        return result
    }

    private fun sync(account: String, credential: String) {
        val previous = credentialByAccount.put(account, credential)
        if (previous != credential) pullRequests.invalidate()
    }

    companion object {
        /** The connect failure a [Result.Error] stands for. */
        fun failureOf(error: Result.Error): PullRequestConnectFailure =
            if ((error.exception as? ApiException)?.statusCode == HTTP_NOT_FOUND) {
                PullRequestConnectFailure.NOT_CONFIGURED
            } else {
                PullRequestConnectFailure.FAILED
            }

        private const val HTTP_NOT_FOUND = 404

        /**
         * Upstream `credentialOf`: the user's grant (by the flow that made it), or the server's token
         * for the provider, so a server moving to another provider counts as a change.
         */
        private fun credentialOf(status: PullRequestConnection): String =
            if (status.connected) {
                "user:${status.provider.orEmpty()}:${status.flowId.orEmpty()}"
            } else {
                serverCredential(status.provider)
            }

        private fun serverCredential(provider: String?): String = "server:${provider.orEmpty()}"
    }
}
