package com.garfiec.librechat.shared.navigation

import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.data.datastore.AccountEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Where a `/share/` link opened in the app goes, and the account switch it may wait on. Held by
 * [NavHostViewModel]; split out so the switch hand-off can be tested without the shell's other
 * dependencies.
 *
 * @param isLoggedIn whether the app is signed in right now; nothing is offered while signed out.
 * @param rosterEntries the signed-in accounts the app holds.
 * @param hasSession whether an account still holds a session (read through the encrypted store,
 *   so it runs on [ioDispatcher]).
 * @param switchAccount switches to an account; false when the account refuses (its session ended).
 * @param onSwitchRefused re-reads which accounts hold a session, after a refused switch.
 */
internal class SharedLinkStateHolder(
    private val scope: CoroutineScope,
    private val activeAccountProvider: ActiveAccountProvider,
    private val isLoggedIn: () -> Boolean,
    private val rosterEntries: suspend () -> List<AccountEntry>,
    private val hasSession: suspend (String) -> Boolean,
    private val switchAccount: suspend (String) -> Boolean,
    private val onSwitchRefused: () -> Unit,
    private val ioDispatcher: CoroutineDispatcher,
) {
    /**
     * The shared link waiting on an account switch: its account, and the `shareId` the viewer opens
     * once the switch has reset the back stack. Taken by [takePendingSharedChat] on the next flip.
     */
    private var pendingSharedChat: Pair<String, String>? = null

    private val _offer = MutableStateFlow<SharedLinkRoute.OfferSwitch?>(null)

    /** A shared link on another account's server, waiting for the user to switch or decline. */
    val offer: StateFlow<SharedLinkRoute.OfferSwitch?> = _offer.asStateFlow()

    private val _browserLinks = Channel<String>(Channel.BUFFERED)

    /** Links the app gives up on and hands to the browser: a switch the account refused. */
    val browserLinks: Flow<String> = _browserLinks.receiveAsFlow()

    /**
     * Where a `/share/` [url] goes: the viewer on the active account, an offer to switch to the
     * signed-in account whose server it is on, or [SharedLinkRoute.NotOurs]. Nothing is offered
     * while signed out.
     */
    suspend fun route(url: String): SharedLinkRoute {
        if (!isLoggedIn()) return SharedLinkRoute.NotOurs
        // A link shared into a cold start can arrive before the active account resolves; without it
        // the active account would read as another one and be offered as a switch to itself.
        val activeId = withTimeoutOrNull(ACCOUNT_RESOLVE_TIMEOUT_MS) { activeAccountProvider.awaitResolvedAccount() }
            ?.value ?: return SharedLinkRoute.NotOurs
        val entries = rosterEntries()
        val liveIds = withContext(ioDispatcher) {
            entries.filter { hasSession(it.accountId) }.mapTo(mutableSetOf()) { it.accountId }
        }
        return sharedLinkRoute(url, entries, activeId, liveIds)
    }

    /** Asks the user whether to switch accounts for [offer]. */
    fun offerSwitch(offer: SharedLinkRoute.OfferSwitch) {
        _offer.value = offer
    }

    fun dismissOffer() {
        _offer.value = null
    }

    /**
     * The user chose to switch for the offered link. The viewer opens after the switch's back-stack
     * reset ([takePendingSharedChat]); an account that refuses the switch (its session ended since
     * the offer) sends the link to the browser instead.
     */
    fun acceptSwitch() {
        val offer = _offer.value ?: return
        _offer.value = null
        pendingSharedChat = offer.accountId to offer.shareId
        scope.launch {
            if (!switchAccount(offer.accountId)) {
                pendingSharedChat = null
                onSwitchRefused()
                _browserLinks.send(offer.url)
            }
        }
    }

    /**
     * The `shareId` to open now that the active account has flipped to [accountId] (null: signed
     * out), or null. Called on every flip, whether or not the viewer can open, so a link never
     * outlives the switch it asked for: a flip to another account, or one that lands signed out,
     * drops it.
     */
    fun takePendingSharedChat(accountId: String?): String? {
        val pending = pendingSharedChat ?: return null
        pendingSharedChat = null
        return pending.second.takeIf { accountId != null && pending.first == accountId }
    }

    companion object {
        const val ACCOUNT_RESOLVE_TIMEOUT_MS = 5_000L
    }
}
