package com.garfiec.librechat.core.brand

/**
 * TCHAT: the pinned-server contract [ServerDataStore] and [ServerUrlViewModel] consume.
 *
 * - `url` blank means "unpinned" — every upstream behaviour (onboarding prompts for a server URL)
 *   is preserved verbatim; this is what a plain, non-Tchat build of this fork would pass.
 * - `allowOverride` is `false` in every Tchat build (see [TchatBrand.SERVER_URL]'s sole
 *   constructor site, `core/brand/di/BrandModule.kt`) — the pin is enforced unconditionally, with
 *   no in-app way to see or change it, debug builds included. `true` still exists as a case this
 *   type supports (every consumer branches on it, not on a hardcoded constant), for a
 *   hypothetical non-Tchat build of this fork that wants upstream's user-entered-URL onboarding
 *   back — Tchat itself never constructs one.
 *
 * A plain data class rather than reaching into [TchatBrand] directly from `:core:data` /
 * `:feature:auth` keeps both testable with a literal value instead of the real brand constant.
 */
data class ServerPinning(
    val url: String,
    val allowOverride: Boolean,
)
