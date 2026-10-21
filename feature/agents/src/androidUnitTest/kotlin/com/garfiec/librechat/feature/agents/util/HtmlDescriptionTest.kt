package com.garfiec.librechat.feature.agents.util

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HtmlDescriptionTest {

    @Test
    fun `plain text keeps its newlines and entities as written`() {
        val text = "Line one\n\nLine two & more &amp; stays"
        val result = htmlDescriptionToAnnotatedString("  $text  ")
        assertThat(result.text).isEqualTo(text)
        assertThat(result.spanStyles).isEmpty()
    }

    @Test
    fun `a less-than sign in plain text is not a tag`() {
        assertThat(htmlDescriptionToAnnotatedString("a < b and 3<4").text).isEqualTo("a < b and 3<4")
    }

    @Test
    fun `bold and strong render bold`() {
        val result = htmlDescriptionToAnnotatedString("Writes <b>read-only</b> and <strong>safe</strong> SQL")
        assertThat(result.text).isEqualTo("Writes read-only and safe SQL")
        val bold = result.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertThat(bold.map { result.text.substring(it.start, it.end) }).containsExactly("read-only", "safe")
    }

    @Test
    fun `italic and em render italic`() {
        val result = htmlDescriptionToAnnotatedString("<i>one</i> <em>two</em>")
        assertThat(result.text).isEqualTo("one two")
        val italic = result.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }
        assertThat(italic.map { result.text.substring(it.start, it.end) }).containsExactly("one", "two")
    }

    @Test
    fun `an https link becomes a tappable url`() {
        val html = "See <a href=\"https://docs.example.com/a?b=1&amp;c=2\">the docs</a>."
        val result = htmlDescriptionToAnnotatedString(html)
        assertThat(result.text).isEqualTo("See the docs.")
        val links = result.getLinkAnnotations(0, result.length)
        assertThat(links).hasSize(1)
        val link = links.single()
        assertThat((link.item as LinkAnnotation.Url).url).isEqualTo("https://docs.example.com/a?b=1&c=2")
        assertThat(result.text.substring(link.start, link.end)).isEqualTo("the docs")
    }

    @Test
    fun `a mailto link is kept and a javascript link is dropped to its text`() {
        val mail = htmlDescriptionToAnnotatedString("<a href='mailto:help@example.com'>Email us</a>")
        assertThat((mail.getLinkAnnotations(0, mail.length).single().item as LinkAnnotation.Url).url)
            .isEqualTo("mailto:help@example.com")

        val script = htmlDescriptionToAnnotatedString("<a href=\"javascript:alert(1)\">click</a>")
        assertThat(script.text).isEqualTo("click")
        assertThat(script.getLinkAnnotations(0, script.length)).isEmpty()
    }

    @Test
    fun `br breaks a line and paragraphs leave one blank line`() {
        assertThat(htmlDescriptionToAnnotatedString("One<br>Two<br/>Three").text).isEqualTo("One\nTwo\nThree")
        assertThat(htmlDescriptionToAnnotatedString("<p>First</p><p>Second</p>").text).isEqualTo("First\n\nSecond")
    }

    @Test
    fun `source whitespace collapses like a browser`() {
        val result = htmlDescriptionToAnnotatedString("<p>\n   Helps   with\n  reviews.  </p>\n\n<p>  Ask away. </p>")
        assertThat(result.text).isEqualTo("Helps with reviews.\n\nAsk away.")
    }

    @Test
    fun `unordered and ordered lists get markers`() {
        val bullets = htmlDescriptionToAnnotatedString("Can:<ul><li>Search</li><li>Summarise</li></ul>Done")
        assertThat(bullets.text).isEqualTo("Can:\n• Search\n• Summarise\nDone")

        val numbered = htmlDescriptionToAnnotatedString("<ol><li>Ask</li><li> Wait </li></ol>")
        assertThat(numbered.text).isEqualTo("1. Ask\n2. Wait")
    }

    @Test
    fun `unknown tags are dropped and script content is not text`() {
        val html = "<span class=\"x\">Kept</span><script>alert(1)</script> <u>too</u>"
        val result = htmlDescriptionToAnnotatedString(html)
        assertThat(result.text).isEqualTo("Kept too")
    }

    @Test
    fun `unclosed and misnested tags still produce the text`() {
        val result = htmlDescriptionToAnnotatedString("<b>bold <i>both</b> plain")
        assertThat(result.text).isEqualTo("bold both plain")
        val bold = result.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertThat(bold.map { result.text.substring(it.start, it.end) }).containsExactly("bold both")
    }

    @Test
    fun `entities in markup are decoded`() {
        assertThat(htmlDescriptionToAnnotatedString("<p>Tom &amp; Jerry&nbsp;&lt;3</p>").text).isEqualTo("Tom & Jerry\u00A0<3")
    }
}
