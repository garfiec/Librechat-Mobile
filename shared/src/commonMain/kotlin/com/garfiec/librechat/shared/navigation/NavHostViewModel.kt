package com.garfiec.librechat.shared.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.extensions.serverHostLabel
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.AccountTransition
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.accountTransitions
import com.garfiec.librechat.core.common.network.ConnectivityObserver
import com.garfiec.librechat.core.common.result.suspendRunCatching
import com.garfiec.librechat.core.data.datastore.AccountEntry
import com.garfiec.librechat.core.data.datastore.AccountRoster
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.repository.AccountSwitcher
import com.garfiec.librechat.core.data.repository.AuthRepository
import com.garfiec.librechat.core.data.repository.BackgroundTaskRepository
import com.garfiec.librechat.core.data.repository.BannerRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.EndpointTokenRepository
import com.garfiec.librechat.core.data.repository.FailoverResult
import com.garfiec.librechat.core.data.repository.FileRepository
import com.garfiec.librechat.core.data.repository.ResumePinStore
import com.garfiec.librechat.core.data.repository.ToolFavoritesRepository
import com.garfiec.librechat.core.data.util.SessionTaskRunner
import com.garfiec.librechat.core.model.Banner
import com.garfiec.librechat.core.model.media.resolveAvatarUrl
import com.garfiec.librechat.core.network.client.AccountReadyGate
import com.garfiec.librechat.core.network.client.ServerUrlProvider
import com.garfiec.librechat.core.network.client.SessionEndReason
import com.garfiec.librechat.core.network.client.SessionHealth
import com.garfiec.librechat.core.network.client.TokenManager
import com.garfiec.librechat.feature.conversations.drawer.AccountUiModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * An unannounced sign-out, or a ban. [accountLabel] is blank when the account can't be named;
 * [switchedTo] is the account the app moved to instead of signing out, or null when it routed to
 * sign-in. [banned] marks a ban, which moves nothing: the user stays where they are until the server
 * ends the session itself.
 */
data class SessionExpiredNotice(
    val accountLabel: String,
    val switchedTo: String? = null,
    val banned: Boolean = false,
)

/** Where a session change sends the app: sign-in, or the chat of the account now active. */
enum class SessionRoute { SignIn, Chat }

/**
 * Navigation-shell ViewModel: auth/session routing, account identity + hygiene, banners, version
 * check, and sidebar mode. The drawer's conversation-list data lives in [com.garfiec.librechat
 * .feature.conversations.drawer.DrawerViewModel] (extracted so `:shared` is nav glue, not a stealth
 * feature module); this VM keeps the account *list* the drawer footer renders, but not the drawer data.
 */
@Suppress("LongParameterList") // debt: constructor dependencies
class NavHostViewModel(
    private val authRepository: AuthRepository,
    bannerRepository: BannerRepository,
    private val configRepository: ConfigRepository,
    private val sessionTaskRunner: SessionTaskRunner,
    private val tokenManager: TokenManager,
    private val accountReadyGate: AccountReadyGate,
    private val settingsDataStore: SettingsDataStore,
    private val serverUrlProvider: ServerUrlProvider,
    private val connectivityObserver: ConnectivityObserver,
    private val endpointTokenRepository: EndpointTokenRepository,
    private val toolFavoritesRepository: ToolFavoritesRepository,
    private val fileRepository: FileRepository,
    private val backgroundTaskRepository: BackgroundTaskRepository,
    private val resumePinStore: ResumePinStore,
    private val activeAccountProvider: ActiveAccountProvider,
    private val accountRoster: AccountRoster,
    private val accountSwitcher: AccountSwitcher,
) : ViewModel() {

    private val bannerStateHolder =
        BannerStateHolder(bannerRepository, serverUrlProvider, viewModelScope)
    private val versionCheckStateHolder =
        VersionCheckStateHolder(configRepository, settingsDataStore, serverUrlProvider, viewModelScope)

    // Seeded synchronously so first-frame routing (LibreChatNavHost reads isLoggedIn.value once in a
    // LaunchedEffect to redirect to auth) gets the correct value with no flash. The init{} block below
    // re-resolves the same value asynchronously.
    //
    // Usually an in-memory null check, because TokenCacheWarmer decrypts on the IO dispatcher at
    // startKoin — but not guaranteed. The warmer only launches, so when it has not landed this falls
    // through to the store's read-through fallback and pays the keystore build plus two decrypts here,
    // on Main, during first composition. Don't gate the first frame on the warm instead: that hangs a
    // logged-out cold start, which reaches no other entry point on the store.
    private val _isLoggedIn = MutableStateFlow(tokenManager.isAuthenticated)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    val versionMismatch: StateFlow<VersionMismatchState?> = versionCheckStateHolder.versionMismatch

    val banner: StateFlow<Banner?> = bannerStateHolder.banner

    // The one owner of session-driven routing: nothing else may route on the token store's signal, or
    // it sends the user to auth past a failover. A channel, not the replay-0 signal: a send while no
    // NavHost is composed waits for it.
    private val _sessionRoute = Channel<SessionRoute>(Channel.CONFLATED)
    val sessionRoute: Flow<SessionRoute> = _sessionRoute.receiveAsFlow()

    /** The unannounced sign-out or ban to report, or null when there is nothing to report. Set for
     *  [SessionEndReason.EXPIRED] and [SessionEndReason.BANNED] — see [SessionEndReason]. */
    private val _sessionExpiredNotice = MutableStateFlow<SessionExpiredNotice?>(null)
    val sessionExpiredNotice: StateFlow<SessionExpiredNotice?> = _sessionExpiredNotice.asStateFlow()

    /**
     * True while the ACTIVE account's session is kept but cannot renew — its refresh is being refused
     * by something that is not LibreChat (see [SessionHealth]). Every request is 401ing meanwhile, so
     * the shell shows a banner with the one action that helps: [signInAgain]. Scoped to the active
     * account the way the expiry signal is: a straggler block on a switched-away account must not
     * banner the live one, and a switch clears it by itself.
     */
    val sessionRenewalBlocked: StateFlow<Boolean> =
        combine(tokenManager.sessionHealth, activeAccountProvider.state) { health, account ->
            val blocked = health as? SessionHealth.RenewalBlocked ?: return@combine false
            val activeId = (account as? AccountState.Resolved)?.id?.value
            blocked.accountId == null || blocked.accountId == activeId
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * The user's answer to [sessionRenewalBlocked]: end the active session and go to sign-in. Not
     * the hard-expiry path — no failover and no "signed out" dialog, because the user asked for THIS
     * account and chose this. The roster entry and cached data stay, so signing back in restores
     * everything, as it does after an expiry. Under [sessionDecision] so a concurrent expiry report
     * cannot fail over from underneath it.
     */
    fun signInAgain() {
        viewModelScope.launch {
            sessionDecision.withLock {
                tokenManager.endSessionForReauth(activeAccountId())
                sessionRevision.update { it + 1 }
                // Lowered before the route event goes out, as in [onSessionEnded].
                _isLoggedIn.value = false
                _sessionRoute.trySend(SessionRoute.SignIn)
            }
        }
    }

    // Re-reads which roster accounts still hold a session. The roster does not change on a hard
    // expiry (the entry stays, only its token slot empties), so the account list has to be told.
    private val sessionRevision = MutableStateFlow(0)

    // Serializes the cold-start route with expiry handling. Both read the active account and may fail
    // over from it; interleaved, one acts on the account the other has already left.
    private val sessionDecision = Mutex()

    // The account the last failover landed on. An expiry handled while it is active and still live was
    // reported for the account it replaced. Guarded by [sessionDecision].
    private var failoverTarget: String? = null

    // The account a ban was last announced for. Every request to a ban-checked route reports the ban
    // again, so without this the dialog would come back each time the user dismissed it.
    private var banAnnouncedFor: String? = null

    fun dismissSessionExpiredNotice() {
        _sessionExpiredNotice.value = null
    }

    // The live identity for the NavHost's account hygiene (Coil cache clear + back-stack reset on
    // an account flip). Exposed as STATE rather than a transition flow so the UI can persist the
    // identity it last ran hygiene for and catch up after Activity recreation or process death — a
    // cold transition flow restarted in that gap would silently swallow a flip that landed mid-gap.
    val accountState: StateFlow<AccountState> = activeAccountProvider.state

    // The signed-in accounts for the drawer chip + switcher sheet: active entry first, the rest by
    // recency (matching "switch to most-recently-active" on remove). Account identity + switching
    // stay in the nav shell; only the drawer *renders* this list ([AccountUiModel] lives with the
    // drawer feature, hence the cross-module type reference).
    val accounts: StateFlow<List<AccountUiModel>> =
        combine(
            accountRoster.entriesFlow(),
            activeAccountProvider.state,
            sessionRevision,
        ) { entries, accountState, _ ->
            val activeId = (accountState as? AccountState.Resolved)?.id?.value
            entries
                .sortedWith(
                    compareByDescending<AccountEntry> { it.accountId == activeId }
                        .thenByDescending { it.lastActiveAt },
                )
                .map { entry ->
                    AccountUiModel(
                        accountId = entry.accountId,
                        displayLabel = entry.displayLabel,
                        serverHost = entry.serverUrl.serverHostLabel(),
                        // The server hands back a RELATIVE `/images/…` path, which Coil has
                        // no fetcher for — it fails before issuing a request, and the slot
                        // draws nothing. Resolved here rather than at seed time so the
                        // persisted entry keeps what the server actually sent, and a
                        // re-pointed server URL heals on the next read.
                        avatarUrl = resolveAvatarUrl(entry.avatarUrl, entry.serverUrl),
                        isActive = entry.accountId == activeId,
                        needsSignIn = !accountSwitcher.hasSession(entry.accountId),
                    )
                }
        }
            // hasSession reads a non-active account's slot through the encrypted store.
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Seeded `null` (= "not resolved yet") and warmed up by the Eagerly-started collector, never a
    // synchronous read — Koin instantiates the VM on the Main thread. The nullable seed lets
    // TabletLayout distinguish "unknown" from "closed" so it can snap to the persisted state on
    // first resolution instead of animating false -> true (a visible sidebar jump on every tablet
    // cold start).
    val tabletSidebarOpen: StateFlow<Boolean?> = settingsDataStore.tabletSidebarOpen
        .map<Boolean, Boolean?> { it }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            null,
        )

    val tabletSidebarGestureEnabled: StateFlow<Boolean> = settingsDataStore.tabletSidebarGestureEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    init {
        @Suppress("TooGenericExceptionCaught") // a failed startup check must not crash the root ViewModel
        viewModelScope.launch {
            try {
                // Wait for the server URL's async warm-up before any startup network work, so a
                // logged-in cold start can't fire requests (auth check, version/config fetch,
                // session tasks) at an empty base URL while ServerDataStore is still resolving.
                serverUrlProvider.awaitBaseUrl()
                // Wait for the roster seed to reconcile the token mirror to the durable active pointer
                // before deciding the route. The synchronous _isLoggedIn seed above reads the raw
                // (possibly crash-diverged) mirror; without this gate a divergence would flash the
                // wrong screen. The gate's seed also drives the server URL, so this is ordered first.
                accountReadyGate.awaitReady()
                val loggedIn = sessionDecision.withLock { resolveStartupSession() }
                if (loggedIn) {
                    // Upgrade safety net: establish the active account before any tenant reads/writes
                    // when an already-logged-in user came from a pre-tenancy build (no login fires).
                    // Isolate its failure: restore does a live getUser() on the upgrade path, and a
                    // transient error (e.g. offline first launch) must not also skip the version check
                    // and session tasks below — those are independent of account resolution.
                    val accountResolved = tryRestoreAccount()
                    versionCheckStateHolder.checkBackendVersion()
                    // Session tasks for the cold-start case (role fetch, tag refresh,
                    // favorites sync). Runs on the application scope so tasks outlive
                    // this VM's scope. Fresh logins fire these from AuthRepositoryImpl.
                    sessionTaskRunner.runAll()
                    // Offline-upgrade recovery: getUser() couldn't run, so the account is unresolved and
                    // every tenant read is empty for an otherwise logged-in user. Re-attempt when
                    // connectivity returns instead of stranding them until a manual relaunch.
                    if (!accountResolved) retryAccountRestoreOnReconnect()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e) { "Failed to check auth state on init" }
            }
        }
        bannerStateHolder.fetchBanner()
        // A dead session has to lower the flag, not just navigate: the 401 path produces no
        // [AccountTransition.Ended], so without this the flag stays true for the rest of the process
        // and the first-frame routing after an Activity recreation still believes the user is signed in.
        viewModelScope.launch {
            tokenManager.sessionExpiredFlow.collect { reason ->
                if (reason == SessionEndReason.BANNED) onBanned() else onSessionEnded(reason)
            }
        }
        // The active account changed underneath this Activity-scoped VM (switch / add-completion /
        // remove → Switched; remove-last → Ended; plain logout also lands here as Ended, where the
        // clears below just repeat logout()'s — idempotent). This is the nav/session half of the
        // reset; the drawer half (conversation list, tags, projects) runs in DrawerViewModel against
        // its own independent accountTransitions() subscription.
        viewModelScope.launch {
            activeAccountProvider.accountTransitions().collect { transition ->
                endpointTokenRepository.clear()
                // Tool favorites are process-lifetime in-memory state, and refresh() deliberately
                // keeps the old set on any non-404 error, so without this drop a flaky incoming
                // server would keep rendering the previous account's pins in the tool picker.
                toolFavoritesRepository.clear()
                // Same singleton-state hazard: the usage-hold probe verdict describes the server
                // being left, and carrying its 404 over suppresses the queued-attachment TTL touch
                // on the incoming one, whose files the reaper then collects out from under a send.
                fileRepository.clear()
                // Same again for the background-tasks route: its latched 404 is the old server's.
                backgroundTaskRepository.clear()
                // A resume pin names another account's run; keeping it would replay that
                // account's agent/tool config into a resume on this one.
                resumePinStore.clear()
                // Reseed the in-memory config from the (already-flipped) server's own srv:-keyed
                // cache — warm on switch-back — instead of clear(), which would wipe every server's
                // disk cache.
                configRepository.reloadForActiveServer()
                if (transition is AccountTransition.Switched) {
                    // The switch path never runs the login-side session machinery
                    // (AuthRepositoryImpl fires these on sign-in; logout/re-auth via onAuthComplete)
                    // so the incoming account's session state is fetched here.
                    // Drop the outgoing account's banner before fetching: until this request
                    // lands, the previous server's banner would render over the new one.
                    bannerStateHolder.clearForAccountChange()
                    bannerStateHolder.fetchBanner()
                    versionCheckStateHolder.checkBackendVersion()
                    sessionTaskRunner.runAll()
                } else if (transition is AccountTransition.Ended) {
                    // The last account signed out (plain logout, or remove-last): mark logged-out so
                    // first-frame routing is correct. Nav-to-auth itself rides the session-expired
                    // signal emitted by the teardown.
                    _isLoggedIn.value = false
                    // The banner belongs to the server just signed out of; the auth screens only
                    // hide it, so without this it reappears the moment the next login navigates.
                    bannerStateHolder.clearForAccountChange()
                }
            }
        }
    }

    /** Switch the active account (no-op for the already-active one). Post-switch refresh and the
     *  back-stack reset ride on the [accountTransitions] collectors, not this call. An account whose
     *  session has expired is not switched to, since every request on it would 401. */
    fun switchAccount(accountId: String) {
        viewModelScope.launch {
            // A refusal can mean the row was stale: a straggler request's hard expiry empties a
            // non-active slot without any signal, so the list still showed it signed in. Re-read it,
            // or the tap does nothing and the row never says why.
            if (!accountSwitcher.switch(accountId)) sessionRevision.update { it + 1 }
        }
    }

    /**
     * The cold-start route. The active account's session may have died while another one is still
     * live (it expired with the app closed, or before failover existed): start on the live one rather
     * than at sign-in. Runs under [sessionDecision], since the startup requests of a dead account
     * report its expiry through [onSessionEnded] at the same time.
     */
    private suspend fun resolveStartupSession(): Boolean {
        if (authRepository.isLoggedIn()) {
            _isLoggedIn.value = true
            return true
        }
        // Read before the failover moves the active pointer off the expired account.
        val expiredLabel = expiredAccountLabel().orEmpty()
        val switched = accountSwitcher.failOverFromActive() as? FailoverResult.Switched
        _isLoggedIn.value = switched != null
        if (switched != null) {
            failoverTarget = switched.entry.accountId
            _sessionExpiredNotice.value = SessionExpiredNotice(expiredLabel, switchedTo = switched.entry.displayLabel)
            // The first frame already routed to sign-in off the dead account's seed.
            _sessionRoute.trySend(SessionRoute.Chat)
        }
        return switched != null
    }

    private suspend fun onSessionEnded(reason: SessionEndReason) = sessionDecision.withLock<Unit> {
        val wasLoggedIn = _isLoggedIn.value
        val expired = reason == SessionEndReason.EXPIRED
        // Read before the failover moves the active pointer off the expired account.
        val expiredLabel = if (expired) expiredAccountLabel().orEmpty() else null
        val failover = if (expired) accountSwitcher.failOverFromActive() else FailoverResult.NoLiveAccount
        sessionRevision.update { it + 1 }
        when {
            failover is FailoverResult.Switched -> {
                failoverTarget = failover.entry.accountId
                // Another account still holds a session: stay signed in on it. The account flip resets
                // the back stack through the NavHost's account hygiene, as any switch does. Announced
                // even from the sign-in screen: the user is being moved onto an account they didn't pick.
                _isLoggedIn.value = true
                _sessionExpiredNotice.value =
                    SessionExpiredNotice(expiredLabel.orEmpty(), switchedTo = failover.entry.displayLabel)
                // For when the expiry was discovered from the sign-in screen (a cold start on the dead
                // account): the flip's own back-stack reset does not leave the auth flow.
                _sessionRoute.trySend(SessionRoute.Chat)
            }
            // The account a failover just landed on is live, so this reports the one it left: the
            // signal went out before the flip and is only being handled now.
            failover == FailoverResult.ActiveStillLive && activeAccountId() == failoverTarget -> Unit
            else -> {
                // Lowered before the route event goes out: the NavHost reads this flag to keep an
                // account flip racing this signal from routing straight back to chat on a dead session.
                _isLoggedIn.value = false
                // Only announce an expiry to someone the app believed was signed in. A logged-out cold
                // start still fires requests (banners, the drawer); each 401s with no token to refresh
                // and settles as expired, so dropping [wasLoggedIn] puts the dialog on every launch.
                if (expiredLabel != null && wasLoggedIn) {
                    _sessionExpiredNotice.value = SessionExpiredNotice(expiredLabel)
                }
                _sessionRoute.trySend(SessionRoute.SignIn)
            }
        }
    }

    /**
     * A ban leaves the user where they are (see [SessionEndReason.BANNED]); if the server ended the
     * session, the next refresh reports EXPIRED and signs out through [onSessionEnded]. Routing to
     * sign-in here would end a session the server may not have ended, and sign-in is ban-checked too.
     * Announced once per account, only to someone signed in (the sign-in form shows the server's
     * own ban message), and never over a notice the user hasn't read yet.
     */
    private suspend fun onBanned() {
        if (!_isLoggedIn.value || _sessionExpiredNotice.value != null) return
        // Empty for a legacy session with no account id, which still needs the once-only guard.
        val accountKey = activeAccountId().orEmpty()
        if (accountKey == banAnnouncedFor) return
        banAnnouncedFor = accountKey
        _sessionExpiredNotice.value = SessionExpiredNotice(expiredAccountLabel().orEmpty(), banned = true)
    }

    private fun activeAccountId(): String? =
        (activeAccountProvider.state.value as? AccountState.Resolved)?.id?.value

    /** Remove an account and all its local data. Removing the active one switches to the
     *  most-recently-active survivor, or routes to auth when it was the last. */
    fun removeAccount(accountId: String) {
        viewModelScope.launch { accountSwitcher.remove(accountId) }
    }

    /** Abandon any in-progress add-account flow; no-op when none is pending. Driven by the NavHost
     *  when the add-flow routes leave the back stack (back-out, session expiry, completion). */
    fun cancelPendingAdd() {
        viewModelScope.launch { accountSwitcher.cancelAdd() }
    }

    /** True while an add-account flow is in progress. The NavHost checks this at composition: a
     *  back stack restored after process death can still hold add-flow routes, but the pending
     *  session is memory-only — restored add-mode screens without it would silently target the
     *  LIVE server, so the NavHost strips them. */
    fun hasPendingAdd(): Boolean = accountSwitcher.pendingAdd != null

    /**
     * Attempts the upgrade-path account restore, swallowing transient failures. Returns `true` when the
     * account is resolved (or restore wasn't needed), `false` when a logged-in upgrade user is still
     * unaccounted and should be retried on reconnect. restoreAccountIfNeeded() self-guards, so it is
     * cheap and safe to call repeatedly.
     */
    private suspend fun tryRestoreAccount(): Boolean =
        suspendRunCatching {
            authRepository.restoreAccountIfNeeded()
        }.getOrElse { e ->
            Logger.w(e) { "Account restore failed on cold start; will retry on reconnect" }
            false
        }

    /** The display label of the account whose session just ended, or null when it can't be named —
     *  a legacy pre-tenancy session, or one whose roster entry has already gone. */
    private suspend fun expiredAccountLabel(): String? {
        val activeId = activeAccountId() ?: return null
        return accountRoster.entriesFlow().firstOrNull()
            ?.firstOrNull { it.accountId == activeId }
            ?.displayLabel
    }

    private fun retryAccountRestoreOnReconnect() {
        viewModelScope.launch {
            // Suspends until a connected emission finally resolves the account, then stops collecting.
            // firstOrNull (not first) so a flow that ever completes returns null instead of throwing
            // NoSuchElementException out of this launch.
            val resolved = connectivityObserver.isConnected
                .filter { it }
                .firstOrNull { tryRestoreAccount() } != null
            // Account just resolved: tenant list Flows repopulate reactively via the active-account
            // gate, but the one-shot session fetches (roles / tags / favorites) already ran while empty,
            // so re-run them now that identity and connectivity are both available.
            if (resolved) sessionTaskRunner.runAll()
        }
    }

    fun onAuthComplete() {
        _isLoggedIn.value = true
        sessionRevision.update { it + 1 }
        // The drawer conversation list is refreshed by the nav host alongside this call
        // (DrawerViewModel.refreshConversationsAfterLogin) — accountTransitions() doesn't fire on a
        // login-from-logged-out, so that crossing is wired explicitly there. Session tasks
        // (role fetch, tag refresh, favorites sync) already fired from AuthRepositoryImpl on the
        // preceding login/OAuth/2FA success, so we don't re-run them here.
        bannerStateHolder.fetchBanner()
        versionCheckStateHolder.checkBackendVersion()
    }

    fun setTabletSidebarOpen(open: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setTabletSidebarOpen(open)
        }
    }

    // Toggles via the StateFlow, not a call-site boolean — Nav3 entry closures can capture stale state.
    fun toggleTabletSidebar() {
        setTabletSidebarOpen(tabletSidebarOpen.value != true)
    }

    fun dismissBanner(bannerId: String) {
        bannerStateHolder.dismissBanner(bannerId)
    }

    fun dismissVersionWarning() {
        versionCheckStateHolder.dismissVersionWarning()
    }

    fun dismissVersionWarningPermanently() {
        versionCheckStateHolder.dismissVersionWarningPermanently()
    }

    fun logout() {
        // Delegate to the account switcher's remove() (via the repository, which first revokes the
        // session server-side): it promotes the most-recently-used survivor — the user stays signed in
        // as that account — or, when this was the last account, tears down to logged-out and emits
        // session-expired to route to auth. Either way the accountTransitions collector above resets
        // this VM's in-memory state (config, sidebar mode, …) for both the Switched and Ended cases
        // (and DrawerViewModel resets the drawer data), so nothing is cleared imperatively here. Not
        // forcing the auth route here is what lets the successor promotion keep the user in the app.
        viewModelScope.launch { authRepository.logout() }
    }
}
