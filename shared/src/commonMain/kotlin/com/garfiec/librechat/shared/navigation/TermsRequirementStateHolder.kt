package com.garfiec.librechat.shared.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the active account still has to accept the server's terms (`modalAcceptance`, enforced
 * server-side from v0.8.9-rc1).
 *
 * A state, not a route event: the requirement can be learned while the user is still in the sign-in
 * flow (the post-login check, a `403 terms_not_accepted` from a request racing the login), where the
 * terms cannot open, and the shell's route channel is conflated, so a later route would overwrite a
 * one-shot "show terms". The NavHost opens the screen whenever this is raised and the navigator is
 * out of the sign-in flow.
 *
 * Raised only for the account that is still active ([activeAccount]): a check started before an
 * account switch must not ask the incoming account to accept the outgoing one's terms.
 */
class TermsRequirementStateHolder(private val activeAccount: () -> String?) {

    private val _required = MutableStateFlow(false)
    val required: StateFlow<Boolean> = _required.asStateFlow()

    /** Records that [accountId] (the account active when the check started) must accept. */
    fun raise(accountId: String?) {
        if (accountId == activeAccount()) _required.value = true
    }

    /** Accepted, declined, signed out or switched away: nothing to accept until the next check. */
    fun clear() {
        _required.value = false
    }

    companion object {
        /**
         * Whether the shell should open the terms now: required, signed in, out of the sign-in flow
         * (there is no session to accept for yet) and not already on the stack.
         */
        fun shouldShow(required: Boolean, loggedIn: Boolean, inAuthFlow: Boolean, termsOpen: Boolean): Boolean =
            required && loggedIn && !inAuthFlow && !termsOpen
    }
}
