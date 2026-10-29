package com.garfiec.librechat.core.network.client

import com.garfiec.librechat.core.model.auth.AuthCookies
import com.garfiec.librechat.core.model.auth.OpenIdMarkers
import com.garfiec.librechat.core.model.auth.SessionCredentials
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class OpenIdCookiesTest {
    private val original = AuthCookies("idp-refresh", OpenIdMarkers("signed-marker"))

    @Test
    fun capturesCompleteOpaqueCookies() {
        assertEquals(
            AuthCookies("opaque+%2F==", OpenIdMarkers("signed%2Bmarker")),
            CookieHelper.capture("theme=dark; refreshToken=opaque+%2F==; token_provider=openid; openid_user_id=signed%2Bmarker"),
        )
    }

    @Test
    fun partialOpenIdCaptureIsNotANormalSession() {
        assertNull(CookieHelper.capture("refreshToken=idp-refresh; token_provider=openid"))
        assertNull(CookieHelper.capture("token_provider=openid; openid_user_id=marker"))
        assertNull(CookieHelper.capture("refreshToken=idp-refresh; token_provider=openid; openid_user_id="))
    }

    @Test
    fun librechatProviderDiscardsUnrelatedMarkers() {
        assertEquals(AuthCookies("normal"), CookieHelper.capture(
            "refreshToken=normal; token_provider=librechat; openid_user_id=stale",
        ))
    }

    @Test
    fun rotationUpdatesTokenAndMarkerTogether() {
        assertEquals(AuthCookies("next==", OpenIdMarkers("next-marker")), CookieHelper.update(original, headersOf(
            HttpHeaders.SetCookie, listOf("refreshToken=next==; HttpOnly", "openid_user_id=next-marker; HttpOnly"),
        )))
    }

    @Test
    fun foundationCombinedCookiesRotateTheWholeSession() {
        val headers = headersOf(HttpHeaders.SetCookie,
            "refreshToken=next+%2F==; Path=/; HttpOnly, " +
                "token_provider=openid; Path=/; HttpOnly, " +
                "openid_user_id=next%2Bmarker; Path=/; HttpOnly",
        )
        assertEquals(AuthCookies("next+%2F==", OpenIdMarkers("next%2Bmarker")), CookieHelper.update(original, headers))
        assertEquals("next+%2F==", CookieHelper.extractRefreshToken(headers))
    }

    @Test
    fun foundationCombinedCookiesKeepExpiresCommas() {
        val headers = headersOf(HttpHeaders.SetCookie,
            "refreshToken=next; Expires=Thu, 01 Jan 2099 00:00:00 GMT; HttpOnly, " +
                "openid_user_id=next-marker; Expires=Thu, 01 Jan 2099 00:00:00 GMT; HttpOnly",
        )
        assertEquals(AuthCookies("next", OpenIdMarkers("next-marker")), CookieHelper.update(original, headers))
    }

    @Test
    fun expiredCombinedMarkerCannotBeCommitted() {
        assertFailsWith<IllegalStateException> {
            CookieHelper.update(original, headersOf(HttpHeaders.SetCookie,
                "refreshToken=next; HttpOnly, openid_user_id=expired; Expires=Thu, 01 Jan 1970 00:00:00 GMT",
            ))
        }
    }

    @Test
    fun omissionRetainsTheWholeSession() {
        assertEquals(original, CookieHelper.update(original, headersOf()))
    }

    @Test
    fun rotatingOnlyTheOpenIdTokenIsIncomplete() {
        assertFailsWith<IllegalStateException> {
            CookieHelper.update(original, headersOf(HttpHeaders.SetCookie, "refreshToken=rotated"))
        }
    }

    @Test
    fun clearingTheProviderDropsTheMarker() {
        assertEquals(AuthCookies("idp-refresh"), CookieHelper.update(original, headersOf(
            HttpHeaders.SetCookie, "token_provider=; Max-Age=0",
        )))
    }

    @Test
    fun expiredMarkersCannotBeCommitted() {
        assertFailsWith<IllegalStateException> {
            CookieHelper.update(original, headersOf(HttpHeaders.SetCookie,
                "openid_user_id=expired; Expires=Thu, 01 Jan 1970 00:00:00 GMT"))
        }
    }

    @Test
    fun maxAgeTakesPrecedenceOverExpires() {
        assertEquals(AuthCookies("idp-refresh", OpenIdMarkers("current")), CookieHelper.update(original, headersOf(
            HttpHeaders.SetCookie, "openid_user_id=current; Max-Age=3600; Expires=Thu, 01 Jan 1970 00:00:00 GMT",
        )))
    }

    @Test
    fun cookieMergeKeepsOneHeaderAndAppValuesWin() {
        val headers = HeadersBuilder().apply {
            append(HttpHeaders.Cookie, "gateway=keep; refreshToken=stale")
            append(HttpHeaders.Cookie, "theme=dark")
        }
        CookieHelper.merge(headers, mapOf("refreshToken" to "current"))
        assertEquals(listOf("gateway=keep; theme=dark; refreshToken=current"), headers.getAll(HttpHeaders.Cookie))
    }

    @Test
    fun rawSseUsesTheSameCookieRules() {
        val lines = sseCustomHeaderLines(
            mapOf("Cookie" to "gateway=keep; token_provider=stale; openid_user_id=stale"), original.openId,
        )
        assertEquals("Cookie: gateway=keep; token_provider=openid; openid_user_id=signed-marker\r\n", lines)
        assertEquals("Cookie: gateway=keep\r\n", sseCustomHeaderLines(mapOf("Cookie" to "gateway=keep; token_provider=stale"), null))
    }

    @Test
    fun credentialRepresentationsAreRedacted() {
        val rendered = SessionCredentials("access-secret", original).toString() + original + original.openId
        listOf("access-secret", "idp-refresh", "signed-marker").forEach { assertFalse(it in rendered) }
    }
}
