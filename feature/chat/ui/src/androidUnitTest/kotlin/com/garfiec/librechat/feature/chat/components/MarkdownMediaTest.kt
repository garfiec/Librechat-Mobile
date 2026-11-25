package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.media.buildAttachmentsByName
import io.ktor.http.Url
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownMediaTest {

    private val base = "https://chat.example.com"
    private val chart =
        Attachment(fileId = "f1", filename = "chart.png", filepath = "/api/files/code/download/s1/chart.png")

    private fun media(baseUrl: String = base, approvals: RemoteImageApprovals = RemoteImageApprovals()) =
        MarkdownMedia(buildAttachmentsByName(listOf(chart)), baseUrl, approvals)

    @Test
    fun ownServerLoadsWithoutAsking() {
        val media = media()
        assertEquals("$base/images/u1/a.png", media.urlFor("$base/images/u1/a.png"))
        assertEquals("$base/images/u1/a.png", media.urlFor("/images/u1/a.png"))
        assertEquals("$base/api/files/f1", media.urlFor("/api/files/f1"))
    }

    @Test
    fun attachmentNamedByTheReplyLoadsWithoutAsking() {
        assertEquals("$base/api/files/code/download/s1/chart.png", media().urlFor("chart.png"))
    }

    @Test
    fun subpathDeploymentLoadsItsOwnImages() {
        val media = media(baseUrl = "https://example.com/chat")
        assertEquals("https://example.com/chat/images/u1/a.png", media.urlFor("/images/u1/a.png"))
        assertEquals("https://example.com/chat/images/u1/a.png", media.urlFor("https://example.com/chat/images/u1/a.png"))
    }

    @Test
    fun anotherSiteRequestsNothingUntilApproved() {
        val media = media()
        val src = "https://cdn.example.net/plot.png"
        assertNull(media.urlFor(src))
        assertEquals(MarkdownImageSource.AskFirst(src, "cdn.example.net"), media.sourceFor(src))
    }

    @Test
    fun approvedUrlLoadsAndOnlyThatUrl() {
        val approvals = RemoteImageApprovals()
        val media = media(approvals = approvals)
        val src = "https://cdn.example.net/plot.png"
        val other = "https://cdn.example.net/other.png"
        media.approve(media.sourceFor(src) as MarkdownImageSource.AskFirst)

        assertEquals(src, media.urlFor(src))
        assertNull(media.urlFor(other))
        // The approval outlives this message's media holder: another reply showing the URL loads it.
        assertEquals(src, media(approvals = approvals).urlFor(src))
    }

    @Test
    fun anApprovalUnderOneServerDoesNotCarryToAnother() {
        val approvals = RemoteImageApprovals()
        val src = "https://cdn.example.net/plot.png"
        val serverA = media(approvals = approvals)
        serverA.approve(serverA.sourceFor(src) as MarkdownImageSource.AskFirst)

        assertEquals(src, serverA.urlFor(src))
        assertNull(media(baseUrl = "https://other.example.org", approvals = approvals).urlFor(src))
    }

    @Test
    fun theServersHostOnAnotherPortIsAskedAboutByItsOrigin() {
        val src = "https://chat.example.com:8443/a.png"
        assertEquals(MarkdownImageSource.AskFirst(src, "https://chat.example.com:8443"), media().sourceFor(src))
    }

    @Test
    fun theHttpClientParsesTheShownHost() {
        assertEquals(true, requestHostMatches("https://cdn.example.net/a.png", "cdn.example.net"))
        assertEquals(true, requestHostMatches("https://chat.example.com:8443/a.png", "https://chat.example.com:8443"))
        assertEquals(true, requestHostMatches("https://[2001:db8::1]:8443/a.png", "[2001:db8::1]:8443"))
        // Ktor skips the extra slash and would request evil.com.
        assertEquals("evil.com", Url("https:///evil.com/p.png").host)
        assertEquals(false, requestHostMatches("https:///evil.com/p.png", ""))
        assertEquals(false, requestHostMatches("https://evil.com/p.png", "cdn.example.net"))
    }

    @Test
    fun unresolvableSourcesAreNeitherLoadedNorAsked() {
        val media = media()
        assertNull(media.sourceFor("missing.png"))
        assertNull(media.sourceFor("sandbox:/mnt/data/chart.png"))
    }
}
