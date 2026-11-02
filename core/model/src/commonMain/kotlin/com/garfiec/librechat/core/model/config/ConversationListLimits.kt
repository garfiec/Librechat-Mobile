package com.garfiec.librechat.core.model.config

import kotlinx.serialization.Serializable

/**
 * `conversationListLimits` on `/api/config` (v0.8.9-rc1): the bounds the list route enforces on
 * the `endpoints` filter. A request past them is answered 400.
 *
 * The defaults are MIRRORED from upstream `conversationListConfigSchema`
 * (`packages/data-provider/src/config.ts`), which a server that does not publish the limits applies.
 */
@Serializable
data class ConversationListLimits(
    val maxEndpointFilters: Int = DEFAULT_MAX_ENDPOINT_FILTERS,
    val maxEndpointNameLength: Int = DEFAULT_MAX_ENDPOINT_NAME_LENGTH,
) {
    companion object {
        const val DEFAULT_MAX_ENDPOINT_FILTERS: Int = 50
        const val DEFAULT_MAX_ENDPOINT_NAME_LENGTH: Int = 128
        val DEFAULT: ConversationListLimits = ConversationListLimits()
    }
}
