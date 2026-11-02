package com.garfiec.librechat.core.model.config

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Cases from upstream's `isSafeLinkUrl` spec (v0.8.9-rc1). */
class SafeLinkUrlTest {

    @Test
    fun allowsWebMailPhoneAndRelative() {
        listOf(
            "https://example.com/privacy",
            "HTTP://example.com",
            "mailto:legal@example.com",
            "tel:+15551234",
            "/terms",
            "terms.html",
            "./a:b",
            "",
            "   ",
        ).forEach { assertTrue(isSafeLinkUrl(it), it) }
    }

    @Test
    fun refusesScriptSchemesHoweverDisguised() {
        listOf(
            "javascript:alert(1)",
            "JavaScript:alert(1)",
            " javascript:alert(1)",
            "\u0000javascript:alert(1)",
            " javascript:alert(1)",
            "﻿javascript:alert(1)",
            "　javascript:alert(1)",
            "java\tscript:alert(1)",
            "java\nscript:alert(1)",
            "data:text/html,<script>",
            "vbscript:x",
            "file:///etc/passwd",
        ).forEach { assertFalse(isSafeLinkUrl(it), it) }
    }

    @Test
    fun configAccessorsDropUnsafeAndBlank() {
        val json = Json { ignoreUnknownKeys = true }
        val config = json.decodeFromString<InterfaceConfig>(
            """{"privacyPolicy":{"externalUrl":"javascript:alert(1)"},""" +
                """"termsOfService":{"externalUrl":" https://x.example/t ","modalAcceptance":true}}""",
        )

        assertNull(config.privacyPolicy?.safeExternalUrl)
        assertEquals("https://x.example/t", config.termsOfService?.safeExternalUrl)
        assertEquals(true, config.termsOfService?.modalAcceptance)
        assertNull(TermsOfServiceConfig(externalUrl = "  ").safeExternalUrl)
    }

    @Test
    fun relativeLinksResolveAgainstTheDeploymentRoot() {
        val root = "https://host.example/librechat"
        assertEquals("https://other.example/p", absoluteLinkUrl("https://other.example/p", root))
        assertEquals("mailto:a@b.example", absoluteLinkUrl("mailto:a@b.example", root))
        assertEquals("https://host.example/librechat/privacy", absoluteLinkUrl("privacy", root))
        assertEquals("https://host.example/privacy", absoluteLinkUrl("/privacy", root))
        assertEquals("https://cdn.example/t", absoluteLinkUrl("//cdn.example/t", root))
        assertEquals("https://host.example/terms", absoluteLinkUrl("/terms", "https://host.example/"))
    }
}
