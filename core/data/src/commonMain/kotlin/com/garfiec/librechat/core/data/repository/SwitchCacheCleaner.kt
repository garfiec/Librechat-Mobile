package com.garfiec.librechat.core.data.repository

/**
 * Clears the caches that cannot be account-partitioned when the active account changes without a
 * logout (switch / add-completion / remove-active): WebView storage and the WebView cookie jar —
 * both process-global, holding the outgoing account's artifact state and its OAuth `refreshToken`
 * cookie. Runs inside the closed switch gate (requests are parked), after the outgoing account is
 * quiesced and before the new identity publishes, so the incoming account never observes them.
 *
 * [serverUrls] are the servers whose auth cookies must die with this transition — the outgoing and
 * incoming (or, on a remove-last teardown, every roster) server. Clearing per URL instead of the
 * whole jar leaves the identity provider's session cookie alive, which is what lets an OIDC
 * re-sign-in complete without re-entering the password.
 *
 * The Coil image cache is the other non-partitionable cache; it is cleared reactively in the UI
 * layer (which owns the `ImageLoader` singleton) on the same identity transition. File caches under
 * [CACHE_SUBDIRECTORIES] are NOT cleared on switch — same-owner stale content is accepted; they
 * clear on logout as before.
 */
fun interface SwitchCacheCleaner {
    suspend fun clearOnSwitch(serverUrls: Set<String>)
}
