package com.garfiec.librechat.core.network.di

import com.garfiec.librechat.core.common.di.KoinQualifiers
import com.garfiec.librechat.core.network.client.ImageCookiePlugin
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.plugins.pluginOrNull
import org.junit.After
import org.junit.Test
import org.koin.core.KoinApplication
import org.koin.core.qualifier.Qualifier

/**
 * Which clients carry the image cookie, asserted on the graph the app actually builds. A per-client
 * test installs the plugin itself and so can never observe it missing from the module.
 */
class ImageCookieInstallTest {

    private var app: KoinApplication? = null

    @After
    fun tearDown() {
        app?.close()
    }

    private fun client(qualifier: Qualifier?): HttpClient {
        val koin = app ?: NetworkGraphTestFakes.koinApp().also { app = it }
        return NetworkGraphTestFakes.client(koin, qualifier)
    }

    /** Coil resolves this client and only this one, so its absence here is every image failing. */
    @Test
    fun `the main client authenticates image requests`() {
        assertThat(client(null).pluginOrNull(ImageCookiePlugin)).isNotNull()
    }

    /**
     * The narrowness is the point: a refresh token belongs on the image mount and nowhere else, and
     * neither of these clients ever fetches an image.
     */
    @Test
    fun `the streaming and refresh clients do not carry the cookie`() {
        assertThat(client(KoinQualifiers.Streaming).pluginOrNull(ImageCookiePlugin)).isNull()
        assertThat(client(KoinQualifiers.Refresh).pluginOrNull(ImageCookiePlugin)).isNull()
    }
}
