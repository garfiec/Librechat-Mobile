package com.garfiec.librechat.core.common.identity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShareLinkMatcherTest {

    private val root = ShareLinkAccount("acct-root", "https://chat.example.com")
    private val sub = ShareLinkAccount("acct-sub", "https://chat.example.com/librechat")

    @Test
    fun parsesALinkOnTheHostRoot() {
        assertEquals(
            ParsedShareLink("https://chat.example.com", "abc-123_X"),
            ShareLinkMatcher.parse("https://chat.example.com/share/abc-123_X"),
        )
    }

    @Test
    fun parsesALinkUnderASubpath() {
        assertEquals(
            ParsedShareLink("https://chat.example.com/librechat", "abc"),
            ShareLinkMatcher.parse("https://chat.example.com/librechat/share/abc"),
        )
    }

    @Test
    fun ignoresTrailingSlashQueryAndFragment() {
        assertEquals("abc", ShareLinkMatcher.parse("https://h.example/share/abc/")?.shareId)
        assertEquals("abc", ShareLinkMatcher.parse("https://h.example/share/abc?x=1")?.shareId)
        assertEquals("abc", ShareLinkMatcher.parse("https://h.example/share/abc#top")?.shareId)
        assertEquals("abc", ShareLinkMatcher.parse("  https://h.example/share/abc \n")?.shareId)
    }

    @Test
    fun rejectsWhatIsNotAShareLink() {
        assertNull(ShareLinkMatcher.parse("https://h.example/c/abc"))
        assertNull(ShareLinkMatcher.parse("https://h.example/share/"))
        assertNull(ShareLinkMatcher.parse("https://h.example/share/abc/files/f1"))
        assertNull(ShareLinkMatcher.parse("ftp://h.example/share/abc"))
        assertNull(ShareLinkMatcher.parse("h.example/share/abc"))
        assertNull(ShareLinkMatcher.parse("librechat://share/abc"))
        assertNull(ShareLinkMatcher.parse("https:///share/abc"))
        assertNull(ShareLinkMatcher.parse("https://h.example/share/a%20b"))
        // Shared text that holds a link among other words, or two links, is not one link.
        assertNull(ShareLinkMatcher.parse("look https://h.example/share/abc"))
        assertNull(ShareLinkMatcher.parse("https://h.example/share/abc https://h.example/share/def"))
    }

    @Test
    fun matchesTheAccountOnTheSameDeployment() {
        val accounts = listOf(root, sub)
        assertEquals(
            ShareLinkTarget("abc", "acct-root"),
            ShareLinkMatcher.match("https://chat.example.com/share/abc", accounts),
        )
        assertEquals(
            ShareLinkTarget("abc", "acct-sub"),
            ShareLinkMatcher.match("https://chat.example.com/librechat/share/abc", accounts),
        )
    }

    @Test
    fun aSubpathDeploymentIsNotTheHostRoot() {
        assertNull(ShareLinkMatcher.match("https://chat.example.com/share/abc", listOf(sub)))
        assertNull(ShareLinkMatcher.match("https://chat.example.com/librechat/share/abc", listOf(root)))
        assertNull(ShareLinkMatcher.match("https://chat.example.com/other/share/abc", listOf(root, sub)))
    }

    @Test
    fun hostCaseDefaultPortAndTrailingSlashDoNotMatter() {
        val stored = ShareLinkAccount("a", "https://Chat.Example.com:443/librechat/")
        assertEquals(
            ShareLinkTarget("abc", "a"),
            ShareLinkMatcher.match("https://chat.example.COM/librechat/share/abc", listOf(stored)),
        )
    }

    @Test
    fun aStoredUrlCarryingAWebRouteStillMatches() {
        // A server URL pasted from the browser's address bar, stored before entry cleaning existed.
        val stored = ShareLinkAccount("a", "https://chat.example.com/c/new")
        assertEquals(
            ShareLinkTarget("abc", "a"),
            ShareLinkMatcher.match("https://chat.example.com/share/abc", listOf(stored)),
        )
    }

    @Test
    fun anotherHostOrPortOrSchemeDoesNotMatch() {
        assertNull(ShareLinkMatcher.match("https://other.example.com/share/abc", listOf(root)))
        assertNull(ShareLinkMatcher.match("https://chat.example.com:8443/share/abc", listOf(root)))
        assertNull(ShareLinkMatcher.match("http://chat.example.com/share/abc", listOf(root)))
    }

    @Test
    fun twoAccountsOnOneServerResolveToTheFirstListed() {
        val first = ShareLinkAccount("first", "https://chat.example.com")
        val second = ShareLinkAccount("second", "https://chat.example.com/")
        assertEquals(
            "first",
            ShareLinkMatcher.match("https://chat.example.com/share/abc", listOf(first, second))?.accountId,
        )
        assertEquals(
            "second",
            ShareLinkMatcher.match("https://chat.example.com/share/abc", listOf(second, first))?.accountId,
        )
    }

    @Test
    fun noAccountsMatchesNothing() {
        assertNull(ShareLinkMatcher.match("https://chat.example.com/share/abc", emptyList()))
    }
}
