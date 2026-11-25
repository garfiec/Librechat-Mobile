package com.garfiec.librechat.shared.navigation

import com.garfiec.librechat.core.common.extensions.serverHostLabel
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.deriveServerId
import com.garfiec.librechat.core.data.gate.GateAccess
import com.garfiec.librechat.core.model.auth.DEFAULT_GATE_CLIENT_ID
import com.garfiec.librechat.core.network.client.ServerUrlProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A sign-in on the gate's own page that the shell has to host. */
data class GatePageRequest(val portalUrl: String, val serverUrl: String)

/**
 * The active server's gate sign-in has lapsed (Authelia refused to renew it), and what the shell's
 * banner does about it. The LibreChat session is untouched: only the gate needs a new sign-in.
 */
internal class GateSessionStateHolder(
    private val gateAccess: GateAccess,
    private val serverUrlProvider: ServerUrlProvider,
    activeAccountProvider: ActiveAccountProvider,
    private val scope: CoroutineScope,
) {
    private val busy = MutableStateFlow(false)
    private val _pageRequest = MutableStateFlow<GatePageRequest?>(null)

    /** The gate's page to show over the shell, for a server signed in that way. */
    val pageRequest: StateFlow<GatePageRequest?> = _pageRequest.asStateFlow()

    /** The active server's host while its gate sign-in has lapsed; null otherwise. */
    val signInRequired: StateFlow<String?> =
        combine(
            gateAccess.signInRequired,
            activeAccountProvider.state.onEach { if (it is AccountState.Resolved) warmActive() },
            busy,
        ) { lapsed, account, signingIn ->
            val required = account is AccountState.Resolved && !signingIn && activeServerId() in lapsed
            if (required) serverUrlProvider.getBaseUrl().serverHostLabel() else null
        }.stateIn(scope, SharingStarted.Eagerly, null)

    fun signIn() {
        val serverUrl = serverUrlProvider.getBaseUrl()
        if (busy.value || serverUrl.isBlank()) return
        scope.launch {
            busy.value = true
            try {
                val details = gateAccess.signInDetails(serverUrl) ?: return@launch
                if (details.usesBrowserSignIn) {
                    gateAccess.signIn(serverUrl, details.portalUrl, details.clientId ?: DEFAULT_GATE_CLIENT_ID)
                } else {
                    _pageRequest.value = GatePageRequest(details.portalUrl, serverUrl)
                }
            } finally {
                busy.value = false
            }
        }
    }

    fun onPageSignedIn(cookies: String) {
        val request = _pageRequest.value ?: return
        _pageRequest.value = null
        scope.launch { gateAccess.savePageSignIn(request.serverUrl, request.portalUrl, cookies) }
    }

    fun dismissPage() {
        _pageRequest.value = null
    }

    private fun warmActive() {
        val serverUrl = serverUrlProvider.getBaseUrl()
        if (serverUrl.isNotBlank()) scope.launch { gateAccess.warm(serverUrl) }
    }

    private fun activeServerId(): String? =
        runCatching { deriveServerId(serverUrlProvider.getBaseUrl()).value }.getOrNull()
}
