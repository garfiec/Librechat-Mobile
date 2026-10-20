package com.garfiec.librechat.feature.chat.components.artifact

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Inputs follow the shape of `buildDocxCdnDocument` / the PPTX CDN document in upstream
 * `packages/api/src/files/documents/html.ts`: CSP meta first in `<head>`, renderer tags with
 * SRI, then a base64 `<script type="application/octet-stream;base64">` and the inline bootstrap.
 */
class OfficePreviewLocalizerTest {

    private val serverCsp =
        "default-src 'none'; script-src https://cdn.jsdelivr.net 'unsafe-inline'; style-src 'unsafe-inline'; " +
            "img-src 'self' data: blob:; font-src data:; connect-src 'self' https://cdn.jsdelivr.net; " +
            "base-uri 'none'; form-action 'none'"

    private val dataBlock = """<script id="lc-doc-data" type="application/octet-stream;base64">UEsDBBQ=</script>"""

    private val bootstrap = """<script>
(function () {
  if (typeof docx === 'undefined') { showFallback('renderer-not-loaded'); return; }
  var s = "<script src='https://evil.example/x.js'></" + "script>";
})();
</script>"""

    private fun doc(head: String, body: String = "") = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="$serverCsp">
<title>Preview</title>
<style>
#lc-fallback { padding: 24px; }
</style>
$head
</head>
<body>
<div id="lc-render"><div class="lc-docx-loading">Loading preview…</div></div>
<div id="lc-fallback" hidden><p id="lc-fallback-notice">Simplified preview</p><article class="lc-docx"><p>Hello</p></article></div>
$body
$dataBlock
$bootstrap
</body>
</html>"""

    private val jszipCdn =
        """<script src="https://cdn.jsdelivr.net/npm/jszip@3.10.1/dist/jszip.min.js" """ +
            """integrity="sha384-+mbV2IY1Zk/X1p/nWllGySJSUN8uMs+gUAN10Or95UBH0fpj6GfKgPmgC5EXieXG" crossorigin="anonymous"></script>"""
    private val docxCdn =
        """<script src="https://cdn.jsdelivr.net/npm/docx-preview@0.3.7/dist/docx-preview.min.js" """ +
            """integrity="sha384-Fw+ZM2MtvxCe867uRzZY5GtGP+gs0NLvrlJS768RZWuKhOHMN4Fln3i3gMt1NSyQ" crossorigin="anonymous"></script>"""
    private val pptxCdn =
        """<script src="https://cdn.jsdelivr.net/npm/pptx-preview@1.0.7/dist/pptx-preview.umd.js" """ +
            """integrity="sha384-CwntHHT2FbwZXuCmbf6K93YaEB9xRVVLaqFJ7pdMykeQABb/3MA0sbt2lGbgi1Mr" crossorigin="anonymous"></script>"""

    private class Case(
        val name: String,
        val input: String,
        val present: List<String> = emptyList(),
        val absent: List<String> = emptyList(),
    )

    private val localJszip = """<script src="jszip/jszip.min.js"></script>"""
    private val localDocx = """<script src="docx-preview/docx-preview.min.js"></script>"""

    private val cases = listOf(
        Case(
            name = "docx document",
            input = doc("$jszipCdn\n$docxCdn"),
            present = listOf("$localJszip\n$localDocx"),
            absent = listOf("integrity=", "crossorigin="),
        ),
        Case(
            name = "pptx document (renderer not vendored)",
            input = doc(pptxCdn),
            absent = listOf("pptx-preview"),
        ),
        Case(
            name = "unknown docx-preview version",
            input = doc(jszipCdn + "\n" + docxCdn.replace("docx-preview@0.3.7", "docx-preview@0.3.8")),
            present = listOf(localJszip),
            absent = listOf("docx-preview@", localDocx),
        ),
        Case(
            name = "extra remote script, link and import",
            input = doc(
                "$jszipCdn\n$docxCdn\n" +
                    """<SCRIPT type="text/javascript" SRC='https://evil.example/a.js'></SCRIPT>""" +
                    """<script src="//evil.example/b.js"></script>""" +
                    """<link rel="stylesheet" href="https://evil.example/c.css">""" +
                    """<style>@import url("https://evil.example/d.css"); .x { color: red; }</style>""",
            ),
            present = listOf(localJszip, localDocx, ".x { color: red; }"),
            absent = listOf("a.js", "b.js", "c.css", "d.css"),
        ),
    )

    @Test
    fun `each captured shape localizes as expected`() {
        cases.forEach { case ->
            val out = localizeOfficePreviewHtml(case.input)
            case.present.forEach { assertTrue(out.contains(it), "${case.name}: missing $it\n$out") }
            case.absent.forEach { assertFalse(out.contains(it), "${case.name}: still contains $it\n$out") }
            assertFalse(out.contains("cdn.jsdelivr.net"), "${case.name}: CDN survived\n$out")
            assertEquals(1, out.split("Content-Security-Policy").size - 1, "${case.name}: expected one CSP")
            assertTrue(
                out.contains("<head><meta http-equiv=\"Content-Security-Policy\" content=\"$OFFICE_PREVIEW_CSP\">"),
                "${case.name}: CSP not first in head",
            )
            // The payload and bootstrap are raw text and must survive byte-for-byte, including the
            // script-looking string literal inside the bootstrap.
            assertTrue(out.contains(dataBlock), "${case.name}: data block altered")
            assertTrue(out.contains(bootstrap), "${case.name}: bootstrap altered")
            assertTrue(out.contains("<article class=\"lc-docx\"><p>Hello</p></article>"), "${case.name}: fallback altered")
            assertEquals(out, localizeOfficePreviewHtml(out), "${case.name}: not idempotent")
        }
    }

    @Test
    fun `replacement CSP names no remote origin`() {
        assertFalse(OFFICE_PREVIEW_CSP.contains("http"))
        assertFalse(OFFICE_PREVIEW_CSP.contains("jsdelivr"))
        assertTrue(OFFICE_PREVIEW_CSP.startsWith("default-src 'none'"))
    }

    @Test
    fun `a document without a CSP gets one rather than failing open`() {
        val out = localizeOfficePreviewHtml("<!DOCTYPE html><p>hi</p>$docxCdn")
        assertTrue(out.startsWith("<!DOCTYPE html><meta http-equiv=\"Content-Security-Policy\""), out)
        assertTrue(out.endsWith(localDocx), out)
        assertTrue(localizeOfficePreviewHtml("<p>hi</p>").startsWith("<meta http-equiv=\"Content-Security-Policy\""))
    }

    @Test
    fun `header and other look-alike tags are not mistaken for head or script`() {
        val out = localizeOfficePreviewHtml("<html><head></head><body><header>x</header><scripts/></body></html>")
        assertEquals(1, out.split("Content-Security-Policy").size - 1)
        assertTrue(out.contains("<header>x</header><scripts/>"))
    }

    @Test
    fun `a meta refresh is dropped, other metas are kept`() {
        val out = localizeOfficePreviewHtml(
            "<html><head><meta charset=\"utf-8\"><meta http-equiv=\"Refresh\" content=\"0;url=https://x.test\"></head></html>",
        )
        assertFalse(out.contains("x.test"), out)
        assertTrue(out.contains("<meta charset=\"utf-8\">"), out)
    }

    @Test
    fun `the final office document hands the WebView no CDN reference`() {
        val built = ArtifactWebContent.buildOfficePreviewHtml(doc("$jszipCdn\n$docxCdn"), "html", isDarkTheme = false)
        assertFalse(built.contains("cdn.jsdelivr.net"))
        assertTrue(built.contains(localJszip))
        assertTrue(built.contains(localDocx))
    }
}
