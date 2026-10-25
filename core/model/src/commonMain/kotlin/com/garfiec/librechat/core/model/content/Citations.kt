package com.garfiec.librechat.core.model.content

import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.FileSearchSource
import com.garfiec.librechat.core.model.WebSearchData

// Inline citations from LibreChat's web_search and file_search tools, as upstream
// `client/src/utils/citations.ts` defines them. The model writes an anchor after the claim it cites:
//
//   standalone   Statement.\uE202turn0search0
//   composite    Statement.\uE200\uE202turn0search0\uE202turn0news1\uE201
//   highlighted  \uE203Cited text.\uE204\uE202turn0search0
//
// `turn{T}{type}{I}` indexes the I-th result of the given type in the T-th search the reply ran.
// Each marker arrives either as the private-use character or as the literal six characters `\uE202`:
// the tool's prompt asks for the escape, and models emit both.

const val CITATION_COMPOSITE_START = '\uE200'
const val CITATION_COMPOSITE_END = '\uE201'
const val CITATION_ANCHOR = '\uE202'

private val LITERAL_MARKER_REGEX = Regex("""\\[uU][eE]20([0-6])""")
private val LITERAL_MARKER_PREFIX_REGEX = Regex("""\\(?:[uU](?:[eE](?:2(?:0)?)?)?)?$""")

// Plain (not raw) strings: the private-use characters are Kotlin escapes, so no regex engine has to
// understand `\u` itself.
private const val ANCHOR_PATTERN = "\uE202turn(\\d+)(search|image|news|video|ref|file)(\\d+)"
private val CITATION_TOKEN_REGEX = Regex("\uE200[^\uE201\n]*\uE201|$ANCHOR_PATTERN")
private val ANCHOR_REGEX = Regex(ANCHOR_PATTERN)
private val STRAY_MARKER_REGEX = Regex("[\uE200-\uE206]")
private val TRAILING_PARTIAL_ANCHOR_REGEX = Regex("\uE202[a-z0-9]*$")
private val CANONICAL_GROUP_REGEX = Regex("\uE200(?:\uE202turn\\d+[a-z]+\\d+)+\uE201|\uE202turn\\d+[a-z]+\\d+")

/** One anchor: the [index]-th result of [type] in search [turn]. */
data class CitationRef(val turn: Int, val type: String, val index: Int) {
    val key: String get() = "$turn$type$index"
}

/** What a citation resolves to. */
sealed interface CitationSource {
    val label: String

    data class Web(
        val link: String,
        val title: String?,
        val snippet: String?,
        val attribution: String?,
    ) : CitationSource {
        val domain: String get() = cleanDomain(link)

        // Upstream Citation.tsx: attribution, then title, then the domain.
        override val label: String
            get() = attribution?.takeIf { it.isNotBlank() }
                ?: title?.takeIf { it.isNotBlank() }
                ?: domain.ifBlank { link }
    }

    data class File(
        val fileId: String?,
        val fileName: String,
        val snippet: String?,
        val pages: List<Int>,
        val relevance: Double?,
    ) : CitationSource {
        override val label: String get() = fileName
    }
}

/** A run of anchors cited together; a standalone anchor is a group of one. */
data class CitationGroup(val refs: List<CitationRef>) {
    fun canonical(): String {
        val anchors = refs.joinToString("") { "${CITATION_ANCHOR}turn${it.turn}${it.type}${it.index}" }
        return if (refs.size == 1) anchors else "$CITATION_COMPOSITE_START$anchors$CITATION_COMPOSITE_END"
    }
}

/**
 * The search results a reply's citations index into, built from its attachments.
 *
 * Mirrors upstream `useSearchResultsByTurn`: a `web_search` attachment is keyed by its `turn` (a
 * later one for the same turn replaces the earlier, as streaming re-sends it while scraping), and
 * one without a turn is ignored. The n-th `file_search` attachment is file turn n. Files are kept
 * in their own map rather than sharing the web map's turn keys: upstream puts both in one map, so a
 * reply that ran a web search and a file search loses one of them at turn 0.
 */
class CitationIndex private constructor(
    private val web: Map<Int, WebSearchData>,
    private val files: Map<Int, List<FileSearchSource>>,
) {
    val isEmpty: Boolean get() = web.isEmpty() && files.isEmpty()

    fun resolve(ref: CitationRef): CitationSource? {
        if (ref.type == "file") {
            val source = files[ref.turn]?.getOrNull(ref.index) ?: return null
            return CitationSource.File(
                fileId = source.fileId,
                fileName = source.fileName?.takeIf { it.isNotBlank() } ?: return null,
                snippet = source.content,
                pages = source.pages.orEmpty(),
                relevance = source.relevance,
            )
        }
        val data = web[ref.turn] ?: return null
        // Upstream Context.tsx's refTypeMap, plus images and videos, which its copy path maps but
        // its render path forgets (they look up `image`/`video` keys that never exist).
        val list = when (ref.type) {
            "search" -> data.organic
            "news" -> data.topStories
            "ref" -> data.references
            "image" -> data.images
            "video" -> data.videos
            else -> null
        }
        val item = list?.getOrNull(ref.index) ?: return null
        val link = item.link?.takeIf { it.isNotBlank() } ?: item.imageUrl?.takeIf { it.isNotBlank() } ?: return null
        return CitationSource.Web(
            link = link,
            title = item.title,
            snippet = item.snippet,
            attribution = item.attribution ?: item.source,
        )
    }

    /**
     * The group's sources, one per page: a model citing the same page from two searches gets one
     * row, as the clipboard numbering already counts it once.
     */
    fun resolve(group: CitationGroup): List<CitationSource> =
        group.refs.mapNotNull(::resolve).distinctBy { (it as? CitationSource.Web)?.link ?: it }

    override fun equals(other: Any?): Boolean =
        other is CitationIndex && web == other.web && files == other.files

    override fun hashCode(): Int = 31 * web.hashCode() + files.hashCode()

    companion object {
        val Empty = CitationIndex(emptyMap(), emptyMap())

        fun from(attachments: List<Attachment>?): CitationIndex {
            if (attachments.isNullOrEmpty()) return Empty
            val web = HashMap<Int, WebSearchData>()
            val files = HashMap<Int, List<FileSearchSource>>()
            var fileTurn = 0
            for (attachment in attachments) {
                when (attachment.type) {
                    "web_search" -> attachment.webSearch?.let { data -> data.turn?.let { web[it] = data } }
                    "file_search" -> attachment.fileSearch?.let { data ->
                        files[fileTurn++] = data.sources.orEmpty()
                    }
                }
            }
            return if (web.isEmpty() && files.isEmpty()) Empty else CitationIndex(web, files)
        }
    }
}

/**
 * [text] with its citations ready for the annotator: every resolvable anchor or group in its
 * canonical private-use form, everything else gone.
 *
 * - literal `\ue20x` escapes become the characters;
 * - highlight markers are dropped and their text kept (web highlights it only on hover);
 * - an anchor or group with nothing resolvable is removed with the whitespace before it, as
 *   upstream's `INVALID_CITATION_REGEX` cleanup does, so no "claim ." is left behind;
 * - a group keeps only its resolvable anchors, and one left with a single anchor becomes standalone;
 * - stray markers are removed (`CLEANUP_REGEX`);
 * - code spans and an open fence are left exactly as written, as upstream's remark plugin only
 *   visits text nodes.
 *
 * [streamingTail]: the text is the live end of a reply still being written. An anchor, group or
 * escape reaching the end may be incomplete (`turn0sea`, or `turn0search1` about to become
 * `turn0search12`), so it is held back until more text arrives.
 *
 * With [CitationIndex.Empty] every citation is removed, which is [stripCitations].
 */
fun normalizeCitations(text: String, index: CitationIndex, streamingTail: Boolean = false): String {
    val partialEscapeTail = streamingTail && LITERAL_MARKER_PREFIX_REGEX.containsMatchIn(text)
    if (!partialEscapeTail && !mayHoldCitations(text)) return text
    val out = StringBuilder(text.length)
    var proseStart = 0
    var i = 0
    while (i < text.length) {
        if (text[i] == '`') {
            var runEnd = i
            while (runEnd < text.length && text[runEnd] == '`') runEnd++
            val end = codeSpanEnd(text, i)
            if (end > runEnd) {
                out.append(normalizeProse(text.substring(proseStart, i), index, tail = false))
                out.append(text, i, end)
                proseStart = end
            }
            i = maxOf(end, runEnd)
        } else {
            i++
        }
    }
    out.append(normalizeProse(text.substring(proseStart), index, tail = streamingTail))
    return out.toString()
}

/** [text] with every citation marker removed, for plain-text surfaces (copy, speech, find). */
fun stripCitations(text: String): String = normalizeCitations(text, CitationIndex.Empty)

/** Cheap pre-check: nothing to do unless a marker character or a literal escape is present. */
fun mayHoldCitations(text: String): Boolean =
    text.any { it in '\uE200'..'\uE206' } || text.contains("\\ue20", ignoreCase = true)

private fun normalizeProse(prose: String, index: CitationIndex, tail: Boolean): String {
    if (prose.isEmpty()) return prose
    var text = prose.replace(LITERAL_MARKER_REGEX) { (it.groupValues[1].single() - '0' + 0xE200).toChar().toString() }
    if (tail) text = trimPartialTail(text)
    val out = StringBuilder(text.length)
    var last = 0
    for (match in CITATION_TOKEN_REGEX.findAll(text)) {
        out.append(cleanup(text.substring(last, match.range.first)))
        val refs = ANCHOR_REGEX.findAll(match.value).map { it.toRef() }.toList()
        val kept = refs.filter { index.resolve(it) != null }
        if (kept.isEmpty()) {
            trimTrailingSpaces(out)
        } else {
            out.append(CitationGroup(kept).canonical())
        }
        last = match.range.last + 1
    }
    out.append(cleanup(text.substring(last)))
    return out.toString()
}

/** An [ANCHOR_REGEX] match as a ref. */
private fun MatchResult.toRef(): CitationRef =
    CitationRef(turn = groupValues[1].toInt(), type = groupValues[2], index = groupValues[3].toInt())

/** Holds back an escape prefix, an unclosed group or an anchor that the next delta may complete. */
private fun trimPartialTail(text: String): String {
    var t = text
    LITERAL_MARKER_PREFIX_REGEX.find(t)?.let { t = t.substring(0, it.range.first) }
    val open = t.lastIndexOf(CITATION_COMPOSITE_START)
    if (open >= 0 && t.indexOf(CITATION_COMPOSITE_END, open) < 0) t = t.substring(0, open)
    TRAILING_PARTIAL_ANCHOR_REGEX.find(t)?.let { t = t.substring(0, it.range.first) }
    return t
}

private fun cleanup(text: String): String =
    if (text.any { it in '\uE200'..'\uE206' }) text.replace(STRAY_MARKER_REGEX, "") else text

private fun trimTrailingSpaces(out: StringBuilder) {
    var end = out.length
    while (end > 0 && (out[end - 1] == ' ' || out[end - 1] == '\t')) end--
    out.setLength(end)
}

/** A piece of normalized text: prose, or a citation group to draw as a pill. */
sealed interface CitationPiece {
    data class Text(val text: String) : CitationPiece
    data class Group(val group: CitationGroup) : CitationPiece
}

/** Splits normalized [text] into prose and the canonical groups [normalizeCitations] left in it. */
fun citationPieces(text: String): List<CitationPiece> {
    if (CITATION_ANCHOR !in text) return listOf(CitationPiece.Text(text))
    val pieces = mutableListOf<CitationPiece>()
    var last = 0
    for (match in CANONICAL_GROUP_REGEX.findAll(text)) {
        if (match.range.first > last) pieces += CitationPiece.Text(text.substring(last, match.range.first))
        val refs = ANCHOR_REGEX.findAll(match.value).map { it.toRef() }.toList()
        pieces += CitationPiece.Group(CitationGroup(refs))
        last = match.range.last + 1
    }
    if (last < text.length) pieces += CitationPiece.Text(text.substring(last))
    return pieces
}

/** Every group in normalized [text], in order, without repeats. */
fun citationGroupsIn(text: String): List<CitationGroup> =
    citationPieces(text).mapNotNull { (it as? CitationPiece.Group)?.group }.distinct()

/** Upstream `getCleanDomain`: the host without scheme or a leading `www.`. */
fun cleanDomain(link: String): String =
    link.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
        .substringAfterLast('@').removePrefix("www.")

private val HIGHLIGHT_SPAN_REGEX = Regex("(?:\\\\ue203|\uE203)(.*?)(?:\\\\ue204|\uE204)", RegexOption.IGNORE_CASE)
private val TRAILING_REFERENCE_RUN_REGEX = Regex("\\n\\s*\\[\\d+](\\[\\d+])*\\s*$")

/**
 * A message's citations as its clipboard copy writes them, ported from upstream
 * `createCitationProcessor` (`useCopyToClipboard.ts`): each cited link gets a number in order of
 * first appearance, shared by every text part of the message; a group becomes its sorted numbers
 * (`[1][3]`); highlighted text becomes bold; and [footer] lists the links. A citation that resolves
 * to no link (a file) is dropped, as upstream skips a source without one.
 */
class CitationClipboard(private val index: CitationIndex) {
    private val numbers = LinkedHashMap<String, Int>()

    fun process(text: String): String {
        if (!mayHoldCitations(text)) return text
        val bolded = text.replace(HIGHLIGHT_SPAN_REGEX) { "**${it.groupValues[1]}**" }
        val out = StringBuilder()
        for (piece in citationPieces(normalizeCitations(bolded, index))) {
            when (piece) {
                is CitationPiece.Text -> out.append(piece.text)
                is CitationPiece.Group -> index.resolve(piece.group)
                    .mapNotNull { (it as? CitationSource.Web)?.link }
                    .map { link -> numbers.getOrPut(link) { numbers.size + 1 } }
                    .distinct()
                    .sorted()
                    .forEach { out.append('[').append(it).append(']') }
            }
        }
        return out.toString().replace(TRAILING_REFERENCE_RUN_REGEX, "")
    }

    /** The sources list upstream appends after the last part, or null when nothing was cited. */
    fun footer(): String? = numbers.takeIf { it.isNotEmpty() }
        ?.entries?.joinToString(separator = "", prefix = "\nCitations:\n") { (link, n) -> "[$n] $link\n" }
}

/**
 * Where the backtick run at [i] stops hiding text: past the code span whose closing run matches it,
 * at the end of [text] for a line-leading run of three or more (an open fence), or just past the run
 * itself when nothing closes it, since an unmatched run is plain text and hides nothing.
 */
fun codeSpanEnd(text: String, i: Int): Int {
    fun runEnd(from: Int): Int {
        var k = from
        while (k < text.length && text[k] == '`') k++
        return k
    }
    val openEnd = runEnd(i)
    val run = openEnd - i
    if (run >= CODE_FENCE_RUN && (i == 0 || text[i - 1] == '\n')) return text.length
    var k = openEnd
    while (k < text.length) {
        k = text.indexOf('`', k)
        if (k < 0) return openEnd
        val end = runEnd(k)
        if (end - k == run) return end
        k = end
    }
    return openEnd
}

private const val CODE_FENCE_RUN = 3
