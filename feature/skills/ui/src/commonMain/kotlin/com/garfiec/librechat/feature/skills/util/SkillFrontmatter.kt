package com.garfiec.librechat.feature.skills.util

internal data class FrontmatterField(val key: String, val value: String)

internal data class ParsedFrontmatter(val fields: List<FrontmatterField>, val body: String)

/** Keys the detail header already shows, so the metadata list leaves them out (web's `SKIP_KEYS`). */
internal val HEADER_FRONTMATTER_KEYS = setOf("name", "description")

/**
 * Splits a SKILL.md body's YAML frontmatter (`---` … `---`) from its markdown. A port of upstream's
 * `parseFrontmatter` (`client/src/components/Skills/utils/frontmatter.ts`): one `key: value` per line,
 * a key with no value takes the `- item` lines under it joined with commas, and keys in [skipKeys]
 * (lowercase) or with no value are dropped. A body without a closed block comes back unchanged.
 */
internal fun parseFrontmatter(raw: String, skipKeys: Set<String> = emptySet()): ParsedFrontmatter {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("---")) return ParsedFrontmatter(emptyList(), raw)
    val after = trimmed.substring(3)
    val closing = after.indexOf("\n---")
    if (closing == -1) return ParsedFrontmatter(emptyList(), raw)

    val lines = after.substring(0, closing).split('\n')
    val body = after.substring(closing + 4).trim()
    val fields = mutableListOf<FrontmatterField>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val colon = line.indexOf(':')
        val key = if (colon == -1) "" else line.substring(0, colon).trim()
        if (key.isNotEmpty()) {
            var value = line.substring(colon + 1).trim()
            // List items are consumed even under a skipped key, or they would be read as keys.
            if (value.isEmpty()) {
                val items = mutableListOf<String>()
                while (i + 1 < lines.size && lines[i + 1].trim().startsWith("-")) {
                    items += lines[i + 1].trim().substring(1).trim()
                    i++
                }
                value = items.joinToString(",")
            }
            if (key.lowercase() !in skipKeys && value.isNotEmpty()) fields += FrontmatterField(key, value)
        }
        i++
    }
    return ParsedFrontmatter(fields, body)
}
