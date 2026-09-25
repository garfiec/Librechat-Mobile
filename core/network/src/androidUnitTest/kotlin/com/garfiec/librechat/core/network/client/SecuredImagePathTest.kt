package com.garfiec.librechat.core.network.client

import com.google.common.truth.Truth.assertThat
import io.ktor.http.URLBuilder
import org.junit.Test

/**
 * The predicate two plugins share: [AuthInterceptorPlugin] keeps the local image mount out of its
 * 401 refresh/session-expiry leg, and [ImageCookiePlugin] uses it as the attach gate for the refresh
 * token. Too loose and the credential goes everywhere; too tight and a base-path deployment's images
 * both fail to load and sign the user out.
 */
class SecuredImagePathTest {

    private fun matches(path: String, baseUrl: String? = "https://chat.example.com"): Boolean =
        isSecuredImagePath(URLBuilder(path), baseUrl)

    @Test
    fun `matches the image mount of a host-root deployment`() {
        assertThat(matches("https://chat.example.com/images/user-1/img.png")).isTrue()
        assertThat(matches("https://chat.example.com/images/avatar-abc.jpg")).isTrue()
    }

    @Test
    fun `matches the image mount of a base-path deployment`() {
        val base = "https://chat.example.com/librechat"
        assertThat(matches("https://chat.example.com/librechat/images/user-1/img.png", base)).isTrue()
    }

    /** Host-root `/images/` on a base-path deployment is somebody else's route, not the mount. */
    @Test
    fun `does not match outside the deployment's base path`() {
        val base = "https://chat.example.com/librechat"
        assertThat(matches("https://chat.example.com/images/user-1/img.png", base)).isFalse()
        assertThat(matches("https://chat.example.com/other/images/x.png", base)).isFalse()
    }

    /** `/api/files/images` is the multipart upload POST on the files router, a bearer route. */
    @Test
    fun `does not match the files upload route`() {
        assertThat(matches("https://chat.example.com/api/files/images")).isFalse()
        assertThat(matches("https://chat.example.com/api/files/download/u1/f1")).isFalse()
    }

    @Test
    fun `does not match ordinary api paths`() {
        assertThat(matches("https://chat.example.com/api/convos")).isFalse()
        assertThat(matches("https://chat.example.com/")).isFalse()
    }

    /** A segment that merely starts with the mount's name is a different route. */
    @Test
    fun `matches whole segments only`() {
        assertThat(matches("https://chat.example.com/images-archive/x.png")).isFalse()
        assertThat(matches("https://chat.example.com/Images/x.png")).isFalse()
    }

    /**
     * An unresolvable base URL falls back to the host-root shape. Inert for the cookie, which is
     * additionally gated on [isSameServerAuthority] and so fails closed on exactly this input.
     */
    @Test
    fun `falls back to a host-root mount when the base URL is unknown`() {
        assertThat(matches("https://chat.example.com/images/x.png", baseUrl = null)).isTrue()
        assertThat(matches("https://chat.example.com/images/x.png", baseUrl = "")).isTrue()
    }

    /** A trailing slash on a stored base URL must not shift the expected mount by one segment. */
    @Test
    fun `tolerates a trailing slash on the base URL`() {
        val base = "https://chat.example.com/librechat/"
        assertThat(matches("https://chat.example.com/librechat/images/x.png", base)).isTrue()
    }
}
