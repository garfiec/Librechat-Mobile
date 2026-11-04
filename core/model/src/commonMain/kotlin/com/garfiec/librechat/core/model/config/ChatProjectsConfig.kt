package com.garfiec.librechat.core.model.config

import kotlinx.serialization.Serializable

/**
 * The startup config's `projects` block (v0.8.9-rc1): what one Chat Project may hold. Every field is
 * optional on the wire; the effective values fall back to upstream's schema defaults
 * (`data-provider/src/limits.ts`), which are what a server without the block enforces too.
 */
@Serializable
data class ChatProjectsConfig(
    val maxFiles: Int? = null,
    val maxInstructionsLength: Int? = null,
    val maxDescriptionLength: Int? = null,
)

/** The limits a project's editors enforce, resolved from [ChatProjectsConfig] or its defaults. */
data class ChatProjectLimits(
    val maxFiles: Int = DEFAULT_MAX_FILES,
    val maxInstructionsLength: Int = DEFAULT_MAX_INSTRUCTIONS_LENGTH,
    val maxDescriptionLength: Int = DEFAULT_MAX_DESCRIPTION_LENGTH,
) {
    companion object {
        const val DEFAULT_MAX_FILES = 50
        const val DEFAULT_MAX_INSTRUCTIONS_LENGTH = 16_000
        const val DEFAULT_MAX_DESCRIPTION_LENGTH = 1_000

        /** Not configurable: the server cuts a longer name to this many characters. */
        const val MAX_NAME_LENGTH = 100

        val DEFAULT = ChatProjectLimits()

        fun from(config: ChatProjectsConfig?): ChatProjectLimits = ChatProjectLimits(
            maxFiles = config?.maxFiles?.takeIf { it > 0 } ?: DEFAULT_MAX_FILES,
            maxInstructionsLength = config?.maxInstructionsLength?.takeIf { it > 0 }
                ?: DEFAULT_MAX_INSTRUCTIONS_LENGTH,
            maxDescriptionLength = config?.maxDescriptionLength?.takeIf { it > 0 }
                ?: DEFAULT_MAX_DESCRIPTION_LENGTH,
        )
    }
}
