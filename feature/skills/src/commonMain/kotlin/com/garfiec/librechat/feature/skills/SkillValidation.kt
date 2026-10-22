package com.garfiec.librechat.feature.skills

/** Why a skill name would be refused, in the order the editor reports them. */
enum class SkillNameIssue {
    /** Not kebab-case, or longer than [SkillValidation.NAME_MAX_LENGTH]. */
    INVALID_FORMAT,

    /** Starts with a brand namespace (`anthropic-`, `claude-`). */
    RESERVED_PREFIX,

    /** Is one of the slash-command words a skill may not shadow. */
    RESERVED_WORD,
}

/**
 * Client-side skill field validation mirroring upstream: the limits in
 * `packages/data-provider/src/types/skills.ts` and the reserved names in
 * `packages/data-schemas/src/methods/skill.ts` (`validateSkillName`), so the editor flags inline
 * what the server would otherwise refuse with a 400.
 */
object SkillValidation {
    const val NAME_MAX_LENGTH = 64
    const val DESCRIPTION_MAX_LENGTH = 1024
    const val DISPLAY_TITLE_MAX_LENGTH = 128
    const val BODY_MAX_LENGTH = 100_000

    /** Upstream `SKILL_NAME_PATTERN = /^[a-z0-9][a-z0-9-]*$/`. */
    private val NAME_PATTERN = Regex("^[a-z0-9][a-z0-9-]*$")

    /** Upstream `RESERVED_NAME_PREFIXES`, matched against the lowercased name. */
    val RESERVED_NAME_PREFIXES: List<String> = listOf("anthropic-", "claude-")

    /** Upstream `RESERVED_NAME_WORDS`, matched exactly against the lowercased name. */
    val RESERVED_NAME_WORDS: Set<String> = setOf(
        "help",
        "clear",
        "compact",
        "model",
        "exit",
        "quit",
        "settings",
        "anthropic",
        "claude",
    )

    /** The first reason [name] would be refused, or null when it is acceptable or still empty. */
    fun nameIssue(name: String): SkillNameIssue? {
        if (name.isEmpty()) return null
        if (name.length > NAME_MAX_LENGTH || !NAME_PATTERN.matches(name)) return SkillNameIssue.INVALID_FORMAT
        val lowered = name.lowercase()
        if (RESERVED_NAME_PREFIXES.any(lowered::startsWith)) return SkillNameIssue.RESERVED_PREFIX
        if (lowered in RESERVED_NAME_WORDS) return SkillNameIssue.RESERVED_WORD
        return null
    }

    fun isNameValid(name: String): Boolean = name.isNotEmpty() && nameIssue(name) == null

    fun isDescriptionValid(description: String): Boolean =
        description.isNotBlank() && description.length <= DESCRIPTION_MAX_LENGTH

    fun isDisplayTitleValid(displayTitle: String): Boolean = displayTitle.length <= DISPLAY_TITLE_MAX_LENGTH

    fun isBodyValid(body: String): Boolean = body.length <= BODY_MAX_LENGTH
}
