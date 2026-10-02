package com.garfiec.librechat.core.data.update

/**
 * Splits a GitHub release body, as `release.yml` assembles it, into what What's new renders.
 *
 * The body is: a line of shields.io badges, an optional hand-written `# Highlights` section,
 * GitHub's generated `## What's Changed` PR list, and a `---` provenance footer. Badges and the
 * footer are dropped; bare pull-request URLs shorten to `#123` links, as GitHub renders them.
 */
object ReleaseNotesParser {

    data class Notes(val highlights: String?, val fullChangelog: String?)

    private val LINKED_IMAGE = Regex("""\[!\[[^\]]*]\([^)]*\)]\([^)]*\)""")
    private val IMAGE = Regex("""!\[[^\]]*]\([^)]*\)""")
    private val HTML_IMAGE = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)

    // A bare URL, not one already inside a markdown link.
    private val BARE_PULL_URL = Regex("""(?<![(\[<])https://github\.com/[^/\s]+/[^/\s]+/pull/(\d+)\b""")

    private val HIGHLIGHTS_HEADING = Regex("""^#{1,3}\s*Highlights\s*$""", RegexOption.IGNORE_CASE)
    private val CHANGES_HEADING = Regex("""^#{1,3}\s*What['’]s Changed\s*$""", RegexOption.IGNORE_CASE)
    private const val MAX_FOOTER_LINES = 3

    fun parse(body: String?): Notes {
        if (body.isNullOrBlank()) return Notes(null, null)
        val lines = stripFooter(body.replace("\r\n", "\n").lines())
            .map { line ->
                line.replace(LINKED_IMAGE, "").replace(IMAGE, "").replace(HTML_IMAGE, "")
                    .replace(BARE_PULL_URL) { "[#${it.groupValues[1]}](${it.value})" }
                    .trimEnd()
            }

        val highlightsAt = lines.indexOfFirst { HIGHLIGHTS_HEADING.matches(it.trim()) }
        val changesAt = lines.indexOfFirst { CHANGES_HEADING.matches(it.trim()) }

        val changelog = if (changesAt >= 0) section(lines, changesAt + 1, lines.size) else null
        val highlights = when {
            highlightsAt >= 0 -> {
                val end = if (changesAt > highlightsAt) changesAt else lines.size
                section(lines, highlightsAt + 1, end)
            }
            changesAt >= 0 -> section(lines, 0, changesAt)
            else -> section(lines, 0, lines.size)
        }
        return Notes(highlights = highlights, fullChangelog = changelog)
    }

    /**
     * Drops the provenance footer: everything from the last `---` rule, but only when what follows
     * is a short trailer with no heading. A rule used as a divider inside the notes is left alone.
     */
    private fun stripFooter(lines: List<String>): List<String> {
        val ruleAt = lines.indexOfLast { it.trim() == "---" }
        if (ruleAt < 0) return lines
        val trailer = lines.subList(ruleAt + 1, lines.size).filter { it.isNotBlank() }
        val isFooter = trailer.size <= MAX_FOOTER_LINES && trailer.none { it.trimStart().startsWith("#") }
        return if (isFooter) lines.subList(0, ruleAt) else lines
    }

    private fun section(lines: List<String>, from: Int, to: Int): String? =
        lines.subList(from, to).joinToString("\n").trim().ifEmpty { null }
}
