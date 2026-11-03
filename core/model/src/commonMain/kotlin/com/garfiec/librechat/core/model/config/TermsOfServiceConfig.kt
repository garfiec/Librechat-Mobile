package com.garfiec.librechat.core.model.config

import com.garfiec.librechat.core.model.serializer.TextOrLinesSerializer
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
    /** The acceptance screen's title; the app's own "Terms of Service" when absent. */
    val modalTitle: String? = null,
    /** The terms the user accepts, as Markdown. The server sends a string or an array of lines. */
    @Serializable(with = TextOrLinesSerializer::class)
    val modalContent: String? = null,
) {
    /** [externalUrl], or null when it is blank or its scheme is not safe to open. See [isSafeLinkUrl]. */
    val safeExternalUrl: String? get() = safeLinkUrlOrNull(externalUrl)
}
