package com.garfiec.librechat.feature.chat.components.mcpapp

import com.garfiec.librechat.core.model.mcp.McpAppCspDeclaration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpAppRelayPageTest {

    private val page = McpAppRelayPage.build(
        McpAppCspDeclaration(resourceDomains = listOf("https://cdn.example.com")),
    )

    @Test
    fun `the shell carries the loader policy and loads nothing`() {
        assertTrue("Content-Security-Policy" in page.shellHtml)
        assertTrue("frame-src blob:" in page.shellHtml)
        assertFalse("<script src" in page.shellHtml)
        assertFalse("<link" in page.shellHtml)
    }

    @Test
    fun `the View policy is embedded as a script-safe string literal`() {
        assertFalse("/*__VIEW_CSP__*/" in page.relayScript)
        assertTrue("frame-src 'none'" in page.relayScript)
        assertEquals("\"a\\u003c/script\\u003e\"", McpAppRelayPage.inlineScriptString("a</script>"))
    }

    @Test
    fun `the inner frame never gets same-origin popups or top navigation`() {
        listOf("allow-same-origin", "allow-popups", "allow-top-navigation").forEach {
            assertFalse(it in page.relayScript, it)
        }
        assertEquals("allow-scripts allow-forms", McpAppProtocol.SANDBOX_TOKENS)
    }

    @Test
    fun `the relay only accepts messages from the inner frame`() {
        assertTrue("event.source !== innerFrame.contentWindow" in page.relayScript)
    }

    @Test
    fun `the android page inlines the prelude and relay into the body`() {
        val html = McpAppRelayPage.inlinePage(page, "function __sb_post(m){}")
        assertTrue("<body><script>function __sb_post(m){}" in html)
        assertTrue(html.trimEnd().endsWith("</html>"))
    }
}
