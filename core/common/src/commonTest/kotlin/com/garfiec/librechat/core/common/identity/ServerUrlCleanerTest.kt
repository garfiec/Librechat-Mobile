package com.garfiec.librechat.core.common.identity

import kotlin.test.Test
import kotlin.test.assertEquals

class ServerUrlCleanerTest {

    /** The web UI's login redirect, copied out of a browser's address bar. */
    @Test
    fun pastedLoginRedirectReducesToTheDeployment() {
        assertEquals(
            "https://chat.example.com",
            cleanServerUrl("https://chat.example.com/login?redirect_to=%2Fc%2Fnew"),
        )
    }

    @Test
    fun webRoutesAreStripped() {
        assertEquals("https://h.example", cleanServerUrl("https://h.example/login"))
        assertEquals("https://h.example", cleanServerUrl("https://h.example/login/"))
        assertEquals("https://h.example", cleanServerUrl("https://h.example/register"))
        assertEquals("https://h.example", cleanServerUrl("https://h.example/c/new"))
        assertEquals("https://h.example", cleanServerUrl("https://h.example/c/4f0a-1234"))
        assertEquals("https://h.example", cleanServerUrl("https://h.example/share/abc123"))
        assertEquals("https://h.example", cleanServerUrl("https://h.example/forgot-password"))
        assertEquals("https://h.example", cleanServerUrl("https://h.example/reset-password"))
        assertEquals("https://h.example", cleanServerUrl("https://h.example/verify"))
    }

    @Test
    fun queryFragmentAndTrailingSlashesAreDropped() {
        assertEquals("https://h.example", cleanServerUrl("https://h.example/?x=1"))
        assertEquals("https://h.example", cleanServerUrl("https://h.example?x=1"))
        assertEquals("https://h.example", cleanServerUrl("https://h.example#top"))
        assertEquals("https://h.example", cleanServerUrl("https://h.example///"))
        assertEquals("https://h.example", cleanServerUrl("  https://h.example/  \n"))
    }

    /**
     * Byte-preserving on what is kept: the joined image URLs are cache keys, so a clean base must
     * come back exactly as it went in. Case and the explicit port are [normalizeServerUrl]'s
     * business, not this function's.
     */
    @Test
    fun aCleanBaseIsReturnedUnchanged() {
        assertEquals("https://chat.example.com", cleanServerUrl("https://chat.example.com"))
        assertEquals("https://Chat.Example.com:8443", cleanServerUrl("https://Chat.Example.com:8443"))
        assertEquals("http://10.0.0.5:3080", cleanServerUrl("http://10.0.0.5:3080"))
        assertEquals("https://[::1]:3080/librechat", cleanServerUrl("https://[::1]:3080/librechat/"))
    }

    /** A subpath deployment is a different server; an unrecognised path is not this function's to drop. */
    @Test
    fun anUnrecognisedPathIsKept() {
        assertEquals("https://h.example/librechat", cleanServerUrl("https://h.example/librechat"))
        assertEquals("https://h.example/librechat", cleanServerUrl("https://h.example/librechat/"))
        // The route is stripped off the end of a subpath too; the subpath itself stays.
        assertEquals("https://h.example/librechat", cleanServerUrl("https://h.example/librechat/login"))
        assertEquals("https://h.example/librechat", cleanServerUrl("https://h.example/librechat/c/new"))
    }

    @Test
    fun routesAreMatchedCaseSensitivelyAndWhole() {
        assertEquals("https://h.example/Login", cleanServerUrl("https://h.example/Login"))
        assertEquals("https://h.example/loginx", cleanServerUrl("https://h.example/loginx"))
        // `/c/<id>/more` is not a conversation route.
        assertEquals("https://h.example/c/x/y", cleanServerUrl("https://h.example/c/x/y"))
    }

    @Test
    fun schemeIsNeitherAddedNorChanged() {
        assertEquals("chat.example.com", cleanServerUrl("chat.example.com/login?x=1"))
        assertEquals("http://h.example:3080", cleanServerUrl("http://h.example:3080/c/new"))
    }

    @Test
    fun blankStaysBlank() {
        assertEquals("", cleanServerUrl(""))
        assertEquals("", cleanServerUrl("   "))
    }

    @Test
    fun originKeepsSchemeAndAuthorityOnly() {
        assertEquals("https://h.example", serverOrigin("https://h.example/librechat"))
        assertEquals("https://h.example:8443", serverOrigin("https://h.example:8443/a/b?x=1#f"))
        assertEquals("https://Chat.Example.com", serverOrigin("https://Chat.Example.com"))
        assertEquals("http://192.168.1.5:3080", serverOrigin("http://192.168.1.5:3080/"))
        assertEquals("h.example", serverOrigin("h.example/librechat"))
    }

    @Test
    fun originOfAPathlessCleanUrlIsTheSameUrl() {
        val clean = cleanServerUrl("https://h.example/login?redirect_to=%2F")
        assertEquals(clean, serverOrigin(clean))
    }
}
