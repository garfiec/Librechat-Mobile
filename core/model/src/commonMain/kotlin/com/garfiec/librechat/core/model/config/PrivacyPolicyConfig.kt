package com.garfiec.librechat.core.model.config

import kotlinx.serialization.Serializable

@Serializable
data class PrivacyPolicyConfig(
    val externalUrl: String? = null,
    val openNewTab: Boolean? = null,
) {
    /** [externalUrl], or null when it is blank or its scheme is not safe to open. See [isSafeLinkUrl]. */
    val safeExternalUrl: String? get() = safeLinkUrlOrNull(externalUrl)
}
