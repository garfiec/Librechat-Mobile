package com.garfiec.librechat.feature.chat.util

import kotlin.test.Test
import kotlin.test.assertEquals

class ModelCatalogKeyTest {
    private val catalogs = mapOf("google" to Unit, "openAI" to Unit)

    @Test
    fun vertexaiFallsBackToGoogle() {
        assertEquals("google", modelCatalogKey("vertexai", catalogs))
    }

    @Test
    fun aCatalogNamedVertexaiWins() {
        assertEquals("vertexai", modelCatalogKey("vertexai", catalogs + ("vertexai" to Unit)))
    }

    @Test
    fun otherProvidersAreUnchanged() {
        assertEquals("openAI", modelCatalogKey("openAI", catalogs))
        assertEquals("anthropic", modelCatalogKey("anthropic", catalogs))
    }
}
