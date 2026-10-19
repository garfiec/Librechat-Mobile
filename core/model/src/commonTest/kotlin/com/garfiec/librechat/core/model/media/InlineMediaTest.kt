package com.garfiec.librechat.core.model.media

import com.garfiec.librechat.core.model.Attachment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class InlineMediaTest {

    private val base = "https://chat.example.com"

    private fun file(name: String, id: String? = "f-$name", path: String? = "/api/files/code/download/$name") =
        Attachment(fileId = id, filename = name, filepath = path)

    private val chart = file("chart.png")
    private val byName = buildAttachmentsByName(listOf(chart))

    @Test
    fun resolvesBareNamesToTheTurnsAttachment() {
        val cases = listOf(
            "chart.png",
            "CHART.PNG",
            "./chart.png",
            "/mnt/data/chart.png",
            "chart.png?v=2",
            "chart.png#top",
            "ch%61rt.png",
        )
        for (src in cases) assertSame(chart, resolveInlineMedia(src, byName), src)
    }

    @Test
    fun addressesAreNeverLookedUp() {
        val cases = listOf(
            "https://cdn.example.com/chart.png",
            "sandbox:/mnt/data/chart.png",
            "data:image/png;base64,AAAA",
            "//cdn.example.com/chart.png",
            "/api/files/other/chart.png",
            "/images/u1/chart.png",
            "/API/files/chart.png",
        )
        for (src in cases) assertNull(resolveInlineMedia(src, byName), src)
    }

    @Test
    fun urlEncodedNamesDecodeBeforeLookup() {
        val spaced = file("my chart.png")
        val map = buildAttachmentsByName(listOf(spaced))
        assertSame(spaced, resolveInlineMedia("my%20chart.png", map))
        assertSame(spaced, resolveInlineMedia("my chart.png", map))
        // `+` is not a space in decodeURIComponent.
        assertNull(resolveInlineMedia("my+chart.png", map))
    }

    @Test
    fun malformedEscapeMatchesTheRawName() {
        val odd = file("100%.png")
        val map = buildAttachmentsByName(listOf(odd))
        assertSame(odd, resolveInlineMedia("100%.png", map))
        assertNull(resolveInlineMedia("chart%E0%A4.png", byName))
    }

    @Test
    fun onlyDisplayableImagesAreIndexed() {
        val map = buildAttachmentsByName(
            listOf(
                file("data.csv"),
                file("diagram.svg"),
                file("nopath.png", path = null),
                Attachment(fileId = "x", filepath = "/api/files/x"),
                file("photo.JPEG"),
            ),
        )
        assertEquals(setOf("photo.jpeg"), map.keys)
    }

    @Test
    fun aNameTwoFilesClaimIsDropped() {
        val first = file("output.png", id = "a")
        val second = file("output.png", id = "b")
        val third = file("output.png", id = "a")
        val map = buildAttachmentsByName(listOf(first, second, third, chart))
        assertNull(resolveInlineMedia("output.png", map))
        assertSame(chart, resolveInlineMedia("chart.png", map))
    }

    @Test
    fun theLaterRecordOfOneFileWins() {
        val stale = file("chart.png", id = "a", path = "/api/files/old/chart.png")
        val fresh = file("chart.png", id = "a", path = "/api/files/new/chart.png")
        assertSame(fresh, resolveInlineMedia("chart.png", buildAttachmentsByName(listOf(stale, fresh))))
        // With no file_id, the path is the identity: two paths are two files.
        val pathOnly = buildAttachmentsByName(
            listOf(file("a.png", id = null, path = "/x/a.png"), file("a.png", id = null, path = "/y/a.png")),
        )
        assertTrue(pathOnly.isEmpty())
    }

    @Test
    fun keyIsTheLeafOfANestedFilename() {
        val nested = file("plots/Revenue.png")
        assertSame(nested, resolveInlineMedia("revenue.png", buildAttachmentsByName(listOf(nested))))
    }

    @Test
    fun loadableUrls() {
        val cases = listOf(
            "chart.png" to "$base/api/files/code/download/chart.png",
            "https://cdn.example.com/a.png" to "https://cdn.example.com/a.png",
            "data:image/png;base64,AAAA" to "data:image/png;base64,AAAA",
            "/images/u1/a.png" to "$base/images/u1/a.png",
            "/api/files/f1" to "$base/api/files/f1",
            "missing.png" to null,
            "sandbox:/mnt/data/chart.png" to null,
            "//cdn.example.com/a.png" to null,
        )
        for ((src, expected) in cases) assertEquals(expected, resolveInlineMediaTarget(src, byName, base)?.url, src)
    }

    @Test
    fun servedPathsKeepTheDeploymentSubpath() {
        assertEquals(
            InlineMediaTarget("https://example.com/chat/images/u1/a.png"),
            resolveInlineMediaTarget("/images/u1/a.png", emptyMap(), "https://example.com/chat/"),
        )
        assertNull(resolveInlineMediaTarget("/images/u1/a.png", emptyMap(), ""))
    }

    @Test
    fun ownServerImagesAreNotForeign() {
        val cases = listOf(
            "https://chat.example.com/images/u1/a.png",
            "HTTPS://Chat.Example.com/api/files/f1",
            "https://chat.example.com",
            "https://chat.example.com?x=1",
            "/images/u1/a.png",
            "/api/files/f1",
            "data:image/png;base64,AAAA",
        )
        for (src in cases) {
            val target = assertNotNull(resolveInlineMediaTarget(src, byName, base), src)
            assertNull(target.foreignHost, src)
        }
    }

    @Test
    fun otherSitesAreForeignAndNamed() {
        val cases = listOf(
            "https://cdn.example.com/a.png" to "cdn.example.com",
            "https://cdn.example.com:8080/a.png" to "cdn.example.com:8080",
            "http://cdn.example.com/a.png" to "cdn.example.com",
            "https://chat.example.com.evil.test/a.png" to "chat.example.com.evil.test",
            "https://chat.example.com@evil.test/a.png" to "evil.test",
            "https://Tracker.Test/pixel.gif?u=1" to "tracker.test",
            "https://[2001:db8::1]:8443/a.png" to "[2001:db8::1]:8443",
        )
        for ((src, host) in cases) {
            assertEquals(InlineMediaTarget(src, host), resolveInlineMediaTarget(src, byName, base), src)
        }
    }

    @Test
    fun theServersHostOnAnotherPortOrSchemeIsNamedByItsWholeOrigin() {
        val cases = listOf(
            "https://chat.example.com:8443/a.png" to "https://chat.example.com:8443",
            "https://chat.example.com:443/a.png" to "https://chat.example.com:443",
        )
        for ((src, label) in cases) {
            assertEquals(InlineMediaTarget(src, label), resolveInlineMediaTarget(src, byName, base), src)
        }
        // A plain-http server's own host over https is asked about by origin too.
        assertEquals(
            "https://plain.example.com",
            resolveInlineMediaTarget("https://plain.example.com/a.png", byName, "http://plain.example.com")?.foreignHost,
        )
    }

    @Test
    fun theServersHostOverCleartextIsNeverLoaded() {
        // The bearer is scoped by host, so this would send it unencrypted.
        assertNull(resolveInlineMediaTarget("http://chat.example.com/a.png", byName, base))
        assertNull(resolveInlineMediaTarget("HTTP://Chat.Example.com:80/a.png", byName, base))
    }

    @Test
    fun aHostThatCannotBeNamedExactlyIsNeverLoaded() {
        val cases = listOf(
            // A URL parser skips the extra slash and requests evil.com; the chip would name nothing.
            "https:///evil.com/p.png",
            "https://",
            // Parsers disagree about a backslash: one reads the host as evil.test.
            "https://chat.example.com\\@evil.test/a.png",
            // A Cyrillic lookalike of the server reads the same as the server.
            "https://chat.ex\u0430mple.com/a.png",
            "https://%65vil.com/a.png",
            "https://cdn.example.com:/a.png",
            "https://cdn.example.com:80x/a.png",
            "https://[zz]/a.png",
        )
        for (src in cases) assertNull(resolveInlineMediaTarget(src, byName, base), src)
    }

    @Test
    fun subpathDeploymentOwnsItsWholeOrigin() {
        val subpath = "https://example.com/chat"
        val cases = listOf(
            "https://example.com/chat/images/u1/a.png" to null,
            "https://example.com/elsewhere/a.png" to null,
            "https://other.example.com/chat/a.png" to "other.example.com",
        )
        for ((src, host) in cases) {
            assertEquals(host, resolveInlineMediaTarget(src, emptyMap(), subpath)?.foreignHost, src)
        }
    }

    @Test
    fun noServerMakesEveryWebImageForeign() {
        assertEquals("cdn.example.com", resolveInlineMediaTarget("https://cdn.example.com/a.png", byName, "")?.foreignHost)
    }

    @Test
    fun attachmentsResolvedByNameAreNeverForeign() {
        val stored = file("plot.png", path = "https://storage.example.net/bucket/plot.png")
        val target = resolveInlineMediaTarget("plot.png", buildAttachmentsByName(listOf(stored)), base)
        assertEquals(InlineMediaTarget("https://storage.example.net/bucket/plot.png"), target)
    }

    @Test
    fun noAttachmentsResolvesNothing() {
        assertNull(resolveInlineMedia("chart.png", emptyMap()))
        assertNull(resolveInlineMedia("chart.png", null))
        assertTrue(buildAttachmentsByName(null).isEmpty())
    }
}
