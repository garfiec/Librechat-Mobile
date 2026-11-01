package com.garfiec.librechat.core.model.config

import kotlinx.serialization.Serializable

@Serializable
data class TermsOfServiceConfig(
    val externalUrl: String? = null,
    val openNewTab: Boolean? = null,
    /**
     * The deployment requires every user to accept the terms. From v0.8.9-rc1 the server enforces it:
     * a user who has not accepted may read, but every write answers `403 terms_not_accepted`.
     */
    val modalAcceptance: Boolean? = null,
) {
    /** [externalUrl], or null when it is blank or its scheme is not safe to open. See [isSafeLinkUrl]. */
    val safeExternalUrl: String? get() = safeLinkUrlOrNull(externalUrl)
}
