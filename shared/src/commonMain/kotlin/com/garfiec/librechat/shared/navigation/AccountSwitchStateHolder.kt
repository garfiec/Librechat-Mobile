package com.garfiec.librechat.shared.navigation

import com.garfiec.librechat.feature.conversations.drawer.AccountUiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Switching the active account, from the drawer's switcher or from a sign-in screen.
 *
 * The app parks an account on sign-in without failing over when the user has to act on that one
 * (required 2FA setup, "sign in again", declined terms). Other accounts can still be signed in then,
 * and [signInEscape] lists them so the sign-in screens can offer a way back to one. The parked
 * account stays in the roster, marked as needing sign-in (or 2FA setup), to come back to later.
 *
 * @param isLoggedIn the shell's signed-in flag; [isLoggedInNow] reads the same flag.
 * @param switchAccount the account switcher's switch; false when it refused (no session, not listed).
 * @param onSwitchRefused re-reads which accounts hold a session, so a stale row says why.
 * @param enterSwitchedAccount leaves the sign-in flow for the account now active.
 */
internal class AccountSwitchStateHolder(
    scope: CoroutineScope,
    accounts: Flow<List<AccountUiModel>>,
    isLoggedIn: Flow<Boolean>,
    private val isLoggedInNow: () -> Boolean,
    private val switchAccount: suspend (String) -> Boolean,
    private val onSwitchRefused: () -> Unit,
    private val enterSwitchedAccount: suspend () -> Unit,
) {

    /** The other accounts with a live session while the app is signed out; empty when signed in. */
    val signInEscape: StateFlow<List<AccountUiModel>> =
        combine(accounts, isLoggedIn, ::signInEscapeAccounts)
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /**
     * Switch to [accountId]. From a sign-in screen the switch alone leaves the user there (the shell
     * still believes it is signed out), so a switch made while signed out also enters the app.
     */
    suspend fun switchTo(accountId: String) {
        if (!switchAccount(accountId)) {
            onSwitchRefused()
            return
        }
        if (!isLoggedInNow()) enterSwitchedAccount()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * The accounts a sign-in screen offers to switch to: none while signed in (add-account runs signed
 * in, and its back affordance already returns to the live account), else every non-active account
 * that still holds a session.
 */
internal fun signInEscapeAccounts(accounts: List<AccountUiModel>, isLoggedIn: Boolean): List<AccountUiModel> =
    if (isLoggedIn) emptyList() else accounts.filter { !it.isActive && !it.needsSignIn }
