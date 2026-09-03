package com.garfiec.librechat.feature.auth.oauth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OAuthWebViewTest {

    private val serverUrl = "https://chat.tensorgrid.space"

    @Test
    fun oauthStartUrlBuildsTheOauthPath() {
        // TCHAT: LibreChat mounts OAuth at /oauth, not /api/oauth — the bug this whole feature exists
        // to fix. Pinning this exact shape here is what would catch a regression back to the old path.
        assertEquals("https://chat.tensorgrid.space/oauth/openid", oauthStartUrl(serverUrl, "openid"))
    }

    @Test
    fun stillOnAnOauthPathIsNotComplete() {
        assertFalse(isOAuthReturnUrl(serverUrl, "$serverUrl/oauth/openid"))
        assertFalse(isOAuthReturnUrl(serverUrl, "$serverUrl/oauth/openid/callback?code=abc&state=xyz"))
    }

    @Test
    fun differentOriginIsNeverComplete() {
        // Every hop before the final redirect (the IdP's own domain, e.g. tensorgrid.space) is a
        // different origin from the server the app is signing into — none of these count as done.
        assertFalse(isOAuthReturnUrl(serverUrl, "https://tensorgrid.space/oauth/authorize?client_id=1"))
        assertFalse(isOAuthReturnUrl(serverUrl, "https://tensorgrid.space/login"))
    }

    @Test
    fun ordinaryPageOnServerOriginIsComplete() {
        assertTrue(isOAuthReturnUrl(serverUrl, serverUrl))
        assertTrue(isOAuthReturnUrl(serverUrl, "$serverUrl/"))
        assertTrue(isOAuthReturnUrl(serverUrl, "$serverUrl/c/new"))
    }

    @Test
    fun originComparisonIsCaseInsensitive() {
        assertTrue(isOAuthReturnUrl(serverUrl, "HTTPS://CHAT.TENSORGRID.SPACE/"))
    }

    @Test
    fun differentPortIsADifferentOrigin() {
        assertFalse(isOAuthReturnUrl(serverUrl, "https://chat.tensorgrid.space:8443/"))
    }

    @Test
    fun malformedUrlsNeverMatch() {
        assertFalse(isOAuthReturnUrl(serverUrl, "not a url"))
        assertFalse(isOAuthReturnUrl("not a url", "$serverUrl/"))
    }

    @Test
    fun pathPrefixMatchIsDeliberatelyOverBroad() {
        // Documenting current behaviour rather than leaving it implicit: a path that merely starts
        // with the letters "/oauth" (not a real /oauth* route) is still treated as "not complete
        // yet", same as a genuine /oauth path. isOAuthReturnUrl only ever needs to distinguish
        // "/oauth*" from LibreChat's own route space, and nothing there begins with those letters
        // otherwise, so the over-broad match never actually fires on a real page.
        assertFalse(isOAuthReturnUrl(serverUrl, "$serverUrl/oauthx"))
    }
}
