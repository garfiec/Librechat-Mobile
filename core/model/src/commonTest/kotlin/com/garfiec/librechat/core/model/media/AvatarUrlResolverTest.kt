package com.garfiec.librechat.core.model.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AvatarUrlResolverTest {

    @Test
    fun `a relative images path joins the server base`() {
        assertEquals(
            "https://chat.example.com/images/u1/avatar-1.png",
            resolveAvatarUrl("/images/u1/avatar-1.png", "https://chat.example.com"),
        )
    }

    /** The avatar route appends `?manual=true`; the join must not drop or escape it. */
    @Test
    fun `the query the avatar route appends survives the join`() {
        assertEquals(
            "https://chat.example.com/images/u1/avatar-1.png?manual=true",
            resolveAvatarUrl("/images/u1/avatar-1.png?manual=true", "https://chat.example.com"),
        )
    }

    @Test
    fun `a base path deployment keeps its prefix`() {
        assertEquals(
            "https://host/librechat/images/u1/avatar-1.png",
            resolveAvatarUrl("/images/u1/avatar-1.png", "https://host/librechat"),
        )
    }

    @Test
    fun `a trailing slash on the base does not double up`() {
        assertEquals(
            "https://chat.example.com/images/u1/avatar-1.png",
            resolveAvatarUrl("/images/u1/avatar-1.png", "https://chat.example.com/"),
        )
    }

    /** A social-login avatar lives on the provider's host; joining it to the server would 404. */
    @Test
    fun `an absolute provider url is left alone`() {
        assertEquals(
            "https://lh3.googleusercontent.com/a/abc123",
            resolveAvatarUrl("https://lh3.googleusercontent.com/a/abc123", "https://chat.example.com"),
        )
    }

    @Test
    fun `absent and blank avatars resolve to nothing`() {
        assertNull(resolveAvatarUrl(null, "https://chat.example.com"))
        assertNull(resolveAvatarUrl("", "https://chat.example.com"))
        assertNull(resolveAvatarUrl("   ", "https://chat.example.com"))
    }

    /**
     * With no base URL yet (a restore that runs before onboarding resolves one) the raw value is
     * handed back rather than a URL rooted at nothing: the caller renders its fallback either way,
     * and a fabricated origin would be cached under a key no later request matches.
     */
    @Test
    fun `a blank base url yields the raw path`() {
        assertEquals("/images/u1/avatar-1.png", resolveAvatarUrl("/images/u1/avatar-1.png", ""))
    }
}
