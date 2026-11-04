package com.garfiec.librechat.core.data.datastore

import com.garfiec.librechat.core.network.client.AuthGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The token store's [AuthGate] (v0.8.9-rc1): the one step the server requires before the active
 * session may go on. Kept apart from [CommonTokenDataStore] so the store only decides *when* a gate
 * applies (account scoping, the refresh arm, re-arming on a new session).
 */
internal class AuthGateState {
    private val _gate = MutableStateFlow<AuthGate?>(null)
    val gate: StateFlow<AuthGate?> = _gate.asStateFlow()

    /**
     * Records [gate] if it belongs to [activeAccount] (a null account is the active session). An
     * enrollment report outranks a terms one: no terms screen can be reached past it. Returns
     * whether it was recorded.
     */
    fun report(gate: AuthGate, activeAccount: String?): Boolean {
        if (gate.accountId != null && gate.accountId != activeAccount) return false
        if (gate is AuthGate.TermsNotAccepted && _gate.value is AuthGate.TwoFactorSetupRequired) return false
        _gate.value = gate
        return true
    }

    /** Clears [gate] once acted on; a newer report is left in place. */
    fun consume(gate: AuthGate) {
        _gate.compareAndSet(gate, null)
    }

    fun clear() {
        _gate.value = null
    }
}
