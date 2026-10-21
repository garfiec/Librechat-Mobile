package com.garfiec.librechat.shared.navigation

import androidx.compose.ui.platform.UriHandler
import kotlin.test.Test
import kotlin.test.assertEquals

/** In-app link taps: `/share/` links go to the router, every other URI to the platform unchanged. */
class SharedLinkUriHandlerTest {

    private val toRouter = mutableListOf<String>()
    private val toPlatform = mutableListOf<String>()
    private val handler = SharedLinkUriHandler(
        platform = object : UriHandler {
            override fun openUri(uri: String) {
                toPlatform += uri
            }
        },
        onSharedLink = { toRouter += it },
    )

    @Test
    fun shareLinksGoToTheRouter() {
        val links = listOf("https://chat.example.com/share/abc123", "https://home.example/librechat/share/xyz")
        links.forEach(handler::openUri)

        assertEquals(links, toRouter)
        assertEquals(emptyList(), toPlatform)
    }

    @Test
    fun everyOtherUriGoesToThePlatform() {
        val others = listOf(
            "https://chat.example.com/c/abc",
            "https://example.com/shared/abc",
            "mailto:a@b.c",
            "tel:123",
        )
        others.forEach(handler::openUri)

        assertEquals(others, toPlatform)
        assertEquals(emptyList(), toRouter)
    }
}
