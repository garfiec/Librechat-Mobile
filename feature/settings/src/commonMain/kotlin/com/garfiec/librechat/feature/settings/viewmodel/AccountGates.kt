package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.common.DetectedBackend
import com.garfiec.librechat.core.model.config.StartupConfig

/** Upstream's provider value for an email-and-password account. */
internal const val LOCAL_PROVIDER = "local"

/** First release carrying the passkey routes; `landingCommit` 461730211672. */
internal const val PASSKEYS_MIN_VERSION = "0.8.9-rc1"
internal const val PASSKEYS_LANDED_DATE = "2026-10-01"

/**
 * Whether Settings offers passkey management (list, rename, remove): web's `passkeyLoginEnabled`
 * predicate plus the release floor. The floor hides the row only on a server KNOWN to predate the
 * routes; a dev build or one past the commit map announces the flag and keeps it (VERSION_GATES.md).
 * Shown for non-local accounts too, so a stranded credential stays removable.
 */
internal fun isPasskeyManagementAvailable(config: StartupConfig?, backend: DetectedBackend?): Boolean =
    config?.passkeyLoginEnabled == true &&
        !BackendVersion.featureSupport(backend, PASSKEYS_MIN_VERSION, PASSKEYS_LANDED_DATE).isRuledOut

/** First release carrying `POST /api/user/email/change`; `landingCommit` 4ffe3825ecc5. */
internal const val EMAIL_CHANGE_MIN_VERSION = "0.8.9-rc1"
internal const val EMAIL_CHANGE_LANDED_DATE = "2026-10-01"

/**
 * Whether the profile offers "Email address" (change email): web's `changeEmail` row predicate plus
 * the release floor, applied like the passkey gate's: it hides the row only on a server KNOWN to
 * predate the route, so a dev build or one past the commit map keeps it. An unknown provider (null)
 * is not treated as local.
 */
internal fun isEmailChangeAvailable(config: StartupConfig?, provider: String?, backend: DetectedBackend?): Boolean =
    provider == LOCAL_PROVIDER &&
        config?.emailEnabled == true &&
        config.allowEmailChange &&
        !BackendVersion.featureSupport(backend, EMAIL_CHANGE_MIN_VERSION, EMAIL_CHANGE_LANDED_DATE).isRuledOut
