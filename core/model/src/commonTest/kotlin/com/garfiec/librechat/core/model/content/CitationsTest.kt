package com.garfiec.librechat.core.model.content

import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.FileSearchData
import com.garfiec.librechat.core.model.FileSearchSource
import com.garfiec.librechat.core.model.WebSearchData
import com.garfiec.librechat.core.model.WebSearchSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CitationsTest {

    private fun source(n: Int, attribution: String? = null) = WebSearchSource(
        link = "https://www.site$n.com/page",
        title = "Title $n",
        snippet = "Snippet $n",
        attribution = attribution,
    )

    private fun webSearch(turn: Int?, organic: Int = 3, news: Int = 0, images: Int = 0) = Attachment(
        type = "web_search",
        webSearch = WebSearchData(
            turn = turn,
            organic = List(organic) { source(it) },
            topStories = List(news) { source(100 + it) },
            images = List(images) { WebSearchSource(imageUrl = "https://img.example/$it.png", title = "Image $it") },
        ),
    )

    private val index = CitationIndex.from(listOf(webSearch(0), webSearch(1, news = 2)))

    // The two forms the model writes: the escape as six literal characters, and the character itself.
    private val a = "\\ue202"
    private val c0 = "\\ue200"
    private val c1 = "\\ue201"

    @Test
    fun `turn0search2 resolves to the third organic result of the first search`() {
        val resolved = index.resolve(CitationRef(turn = 0, type = "search", index = 2))
        assertIs<CitationSource.Web>(resolved)
        assertEquals("https://www.site2.com/page", resolved.link)
        assertEquals("site2.com", resolved.domain)
    }

    @Test
    fun `news resolves to top stories image to images and out of range to nothing`() {
        assertEquals("https://www.site101.com/page", (index.resolve(CitationRef(1, "news", 1)) as CitationSource.Web).link)
        assertNull(index.resolve(CitationRef(1, "news", 2)))
        assertNull(index.resolve(CitationRef(5, "search", 0)))
        val images = CitationIndex.from(listOf(webSearch(0, images = 1)))
        assertEquals("https://img.example/0.png", (images.resolve(CitationRef(0, "image", 0)) as CitationSource.Web).link)
    }

    @Test
    fun `a later attachment for the same turn replaces the earlier and one without a turn is ignored`() {
        val replaced = CitationIndex.from(listOf(webSearch(0, organic = 1), webSearch(0, organic = 3), webSearch(null, organic = 9)))
        assertTrue(replaced.resolve(CitationRef(0, "search", 2)) != null)
        assertNull(replaced.resolve(CitationRef(0, "search", 5)))
    }

    @Test
    fun `file turns count file_search attachments in order apart from web turns`() {
        fun files(vararg names: String) = Attachment(
            type = "file_search",
            fileSearch = FileSearchData(
                sources = names.map { FileSearchSource(fileId = it, fileName = "$it.pdf", content = "text of $it") },
            ),
        )
        val mixed = CitationIndex.from(listOf(webSearch(0), files("a", "b"), files("c")))
        assertEquals("b.pdf", mixed.resolve(CitationRef(0, "file", 1))?.label)
        assertEquals("c.pdf", mixed.resolve(CitationRef(1, "file", 0))?.label)
        // The web result at turn 0 survives the file turn 0 (upstream's shared map loses one).
        assertTrue(mixed.resolve(CitationRef(0, "search", 0)) != null)
    }

    @Test
    fun `the label is the attribution then the title then the domain`() {
        assertEquals("Reuters", CitationSource.Web("https://www.reuters.com/x", "Title", null, "Reuters").label)
        assertEquals("Title", CitationSource.Web("https://www.reuters.com/x", "Title", null, " ").label)
        assertEquals("reuters.com", CitationSource.Web("https://www.reuters.com/x", null, null, null).label)
    }

    @Test
    fun `a literal standalone anchor becomes canonical`() {
        assertEquals("Statement.\uE202turn0search0 more", normalizeCitations("Statement.${a}turn0search0 more", index))
    }

    @Test
    fun `the character form is kept as it is`() {
        assertEquals("Statement.\uE202turn0search1", normalizeCitations("Statement.\uE202turn0search1", index))
    }

    @Test
    fun `a composite keeps its resolvable anchors`() {
        val text = "Claim $c0${a}turn0search0${a}turn9search0${a}turn1news1$c1, next."
        assertEquals("Claim \uE200\uE202turn0search0\uE202turn1news1\uE201, next.", normalizeCitations(text, index))
    }

    @Test
    fun `a composite left with one anchor becomes standalone`() {
        val text = "Claim $c0${a}turn0search0${a}turn9search0$c1."
        assertEquals("Claim \uE202turn0search0.", normalizeCitations(text, index))
    }

    @Test
    fun `an unresolvable anchor or group goes with the space before it`() {
        assertEquals("Claim. Next.", normalizeCitations("Claim. ${a}turn7search0 Next.", index).replace("  ", " "))
        assertEquals("Claim, next.", normalizeCitations("Claim $c0${a}turn7search0${a}turn8news1$c1, next.", index))
        assertEquals("Claim.", normalizeCitations("Claim.\uE202turn0search99", index))
    }

    @Test
    fun `highlight markers go and their text stays`() {
        val text = "\\ue203Apple will hold an event\\ue204 ${c0}${a}turn0search0${a}turn1search3$c1, with invites."
        assertEquals("Apple will hold an event \uE202turn0search0, with invites.", normalizeCitations(text, index))
    }

    @Test
    fun `stray markers are removed`() {
        assertEquals("a b c", normalizeCitations("a\uE201 b\uE206 c\uE200", index))
    }

    @Test
    fun `markers inside code spans and an open fence are left as written`() {
        val inSpan = "Use `${a}turn0search0` here.${a}turn0search0"
        assertEquals("Use `${a}turn0search0` here.\uE202turn0search0", normalizeCitations(inSpan, index))
        val fence = "Text.${a}turn0search0\n```\ncode ${a}turn0search0"
        assertEquals("Text.\uE202turn0search0\n```\ncode ${a}turn0search0", normalizeCitations(fence, index))
        // An unmatched backtick hides nothing.
        assertEquals("a ` b\uE202turn0search0", normalizeCitations("a ` b${a}turn0search0", index))
    }

    @Test
    fun `strip removes every citation`() {
        assertEquals("Claim, next. Done.", stripCitations("Claim $c0${a}turn0search0$c1, next. Done.\uE202turn0search1"))
        assertEquals("No markers [1] here", stripCitations("No markers [1] here"))
    }

    @Test
    fun `a streaming tail holds back what the next delta may complete`() {
        assertEquals("Claim.", normalizeCitations("Claim.${a}turn0sea", index, streamingTail = true))
        assertEquals("Claim.", normalizeCitations("Claim.${a}turn0search1", index, streamingTail = true))
        assertEquals("Claim.", normalizeCitations("Claim.\\ue2", index, streamingTail = true))
        assertEquals("Claim ", normalizeCitations("Claim $c0${a}turn0search0${a}turn1", index, streamingTail = true))
        // Settled, the same text keeps its last anchor.
        assertEquals("Claim.\uE202turn0search1", normalizeCitations("Claim.${a}turn0search1", index))
        // And a backslash only disappears while the reply is still growing.
        assertEquals("path\\", normalizeCitations("path\\", index))
    }

    @Test
    fun `pieces split normalized text into prose and groups`() {
        val pieces = citationPieces("A\uE202turn0search0 b \uE200\uE202turn0search1\uE202turn1news0\uE201.")
        assertEquals(5, pieces.size)
        assertEquals(CitationPiece.Text("A"), pieces[0])
        assertEquals(listOf(CitationRef(0, "search", 0)), (pieces[1] as CitationPiece.Group).group.refs)
        assertEquals(
            listOf(CitationRef(0, "search", 1), CitationRef(1, "news", 0)),
            (pieces[3] as CitationPiece.Group).group.refs,
        )
        assertEquals(CitationPiece.Text("."), pieces[4])
    }

    @Test
    fun `text without markers is returned untouched`() {
        val text = "Plain **bold** and arr[1]"
        assertTrue(normalizeCitations(text, index) === text)
    }

    @Test
    fun `a group citing one page from two searches resolves to it once`() {
        val same = WebSearchSource(link = "https://same.example/a", title = "Same")
        val twice = CitationIndex.from(
            listOf(
                Attachment(type = "web_search", webSearch = WebSearchData(turn = 0, organic = listOf(same))),
                Attachment(type = "web_search", webSearch = WebSearchData(turn = 1, organic = listOf(same))),
            ),
        )
        val group = CitationGroup(listOf(CitationRef(0, "search", 0), CitationRef(1, "search", 0)))
        assertEquals(1, twice.resolve(group).size)
    }

    @Test
    fun `cleanDomain drops scheme www and path`() {
        assertEquals("example.com", cleanDomain("https://www.example.com/a?b#c"))
        assertEquals("news.example.org", cleanDomain("http://news.example.org"))
    }
}
