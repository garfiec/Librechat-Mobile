package com.garfiec.librechat.core.model.auth

import kotlinx.serialization.Serializable

/**
 * One registered passkey, as `GET /api/auth/passkey` lists it (upstream `serializePasskey`,
 * `packages/api/src/auth/passkey/handlers.ts`, v0.8.9-rc1). Dates are ISO-8601.
 */
@Serializable
data class Passkey(
    val id: String,
    val name: String = "",
    /** `singleDevice` or `multiDevice`. Not shown, as on web. */
    val deviceType: String? = null,
    /** True when the credential syncs through the platform's password manager ("Synced"). */
    val backedUp: Boolean = false,
    val transports: List<String> = emptyList(),
    val createdAt: String? = null,
    /** Null when the passkey has never signed in. */
    val lastUsedAt: String? = null,
)

/** `GET /api/auth/passkey`. Newest first (`findPasskeysByUser` sorts `createdAt: -1`). */
@Serializable
data class PasskeyListResponse(val passkeys: List<Passkey> = emptyList())

/** `PATCH /api/auth/passkey/:id`. */
@Serializable
data class PasskeyResponse(val passkey: Passkey)

/** Body of `PATCH /api/auth/passkey/:id`. No `arg` wrapper; the server trims and caps it at 60. */
@Serializable
data class RenamePasskeyRequest(val name: String)

/**
 * Body of `DELETE /api/auth/passkey/:id`. [password] is the account password; the server waives it
 * for an account with no password hash, so it is omitted (null) for non-local accounts.
 */
@Serializable
data class DeletePasskeyRequest(val password: String? = null)

/**
 * Upstream `MAX_PASSKEY_NAME_LENGTH` (`packages/api/src/auth/passkey/handlers.ts`), registered in
 * `scripts/mirrors.json`. The server truncates past it; the rename field stops there instead.
 */
const val MAX_PASSKEY_NAME_LENGTH = 60
