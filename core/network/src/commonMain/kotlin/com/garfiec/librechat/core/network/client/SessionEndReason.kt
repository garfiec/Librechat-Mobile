package com.garfiec.librechat.core.network.client

/**
 * What the token store's session signal reports.
 *
 * [EXPIRED] and [SIGNED_OUT] route the app back to the auth flow; [BANNED] does not. [EXPIRED] and
 * [BANNED] arrive unannounced and are reported to the user; a deliberate sign-out needs no explanation
 * at all. A ban cannot be left to `ApiException.isBanned`: only the sign-in form reads it, and
 * mid-session the exception lands in whichever background call hit a ban-checked route.
 */
enum class SessionEndReason {
    /** The server rejected the session: a refresh answered 401/403, or there was no token to send. */
    EXPIRED,

    /** The user signed out, or removed their last account. */
    SIGNED_OUT,

    /**
     * The server banned the account (or its IP) mid-session. Reported, but it ends nothing by itself:
     * upstream checks bans only on some routes. Banning an account for its own violations also deletes
     * its server sessions, so its next refresh fails and arrives separately as [EXPIRED]; a ban must
     * not spend the store's one-shot expiry report. See `core/network/CLAUDE.md`.
     */
    BANNED,
}
