package com.garfiec.librechat.core.model.mcp

import com.garfiec.librechat.core.model.Attachment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class McpAppsTest {

    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    @Test
    fun `a missing or malformed policy is Apps off`() {
        assertFalse(McpAppsPolicy.resolve(null).enabled)
        assertFalse(McpAppsPolicy.resolve(json("""{"enabled":true}""")).enabled)
        assertFalse(McpAppsPolicy.resolve(json("""{"enabled":"true","legacyHtmlEnabled":true}""")).enabled)
        assertFalse(McpAppsPolicy.resolve(json("""true""")).enabled)
    }

    @Test
    fun `an enabled policy keeps its limits and defaults the bad ones`() {
        val policy = McpAppsPolicy.resolve(
            json(
                """{"enabled":true,"legacyHtmlEnabled":true,"maxActiveViews":99,"maxActionPreviewChars":200,
                "operationLimits":{"maxActive":4},"cspLimits":{"maxSourcesPerDirective":-1}}""",
            ),
        )
        assertTrue(policy.enabled)
        assertEquals(McpAppsPolicy.DEFAULT_MAX_ACTIVE_VIEWS, policy.maxActiveViews)
        assertEquals(200, policy.maxActionPreviewChars)
        assertEquals(4, policy.maxPendingActions)
        assertEquals(McpAppCspLimits.DEFAULT_MAX_SOURCES, policy.cspLimits.maxSourcesPerDirective)
    }

    @Test
    fun `only text html with the mcp-app profile is an App`() {
        assertTrue(isMcpAppMimeType("text/html;profile=mcp-app"))
        assertTrue(isMcpAppMimeType("TEXT/HTML; charset=utf-8; Profile=\"mcp-app\""))
        assertFalse(isMcpAppMimeType("text/html"))
        assertFalse(isMcpAppMimeType("text/html;profile=MCP-APP"))
        assertFalse(isMcpAppMimeType("application/xhtml+xml;profile=mcp-app"))
        assertFalse(isMcpAppMimeType(null))
    }

    @Test
    fun `normalize keeps valid sources only and caps each directive`() {
        val decl = McpAppCspDeclaration.normalize(
            json(
                """{"connectDomains":["https://api.example.com"," wss://rt.example.com ","https://x.com; script-src *",
                "'unsafe-eval'"],"resourceDomains":"https://cdn.example.com","frameDomains":[]}""",
            ),
        )
        assertEquals(listOf("https://api.example.com", "wss://rt.example.com"), decl.connectDomains)
        assertNull(decl.resourceDomains)
        assertNull(decl.frameDomains)

        val capped = McpAppCspDeclaration.normalize(
            json("""{"connectDomains":["a.com","b.com","c.com"]}"""),
            McpAppCspLimits(maxSourcesPerDirective = 2),
        )
        assertEquals(listOf("a.com", "b.com"), capped.connectDomains)
    }

    @Test
    fun `the view policy defaults to no egress and no frames`() {
        val policy = buildMcpAppCspPolicy(McpAppCspDeclaration(), proxyLoader = false)
        assertEquals(
            "default-src 'none'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; " +
                "connect-src 'none'; form-action 'none'; img-src 'self' data:; media-src 'self' data:; " +
                "font-src 'self'; frame-src 'none'; worker-src 'self'; object-src 'none'; base-uri 'self'",
            policy,
        )
    }

    @Test
    fun `declared domains widen only their directives and the loader admits its frame`() {
        val decl = McpAppCspDeclaration(
            resourceDomains = listOf("https://cdn.x"),
            connectDomains = listOf("https://api.x"),
        )
        val view = buildMcpAppCspPolicy(decl, proxyLoader = false)
        assertTrue("script-src 'self' 'unsafe-inline' https://cdn.x" in view)
        assertTrue("connect-src https://api.x" in view)
        assertTrue("form-action https://api.x" in view)
        assertTrue("frame-src 'none'" in view)
        assertTrue("frame-src blob:" in buildMcpAppCspPolicy(decl, proxyLoader = true))
    }

    @Test
    fun `a link opens only on a declared host scheme and port`() {
        val csp = McpAppCspDeclaration(
            connectDomains = listOf("https://api.example.com"),
            resourceDomains = listOf("https://*.cdn.example.com", "wss://socket.example.com"),
            frameDomains = listOf("http://maps.example.com:8080"),
        )
        assertTrue(isAllowedAppLink("https://api.example.com/path?q=1", csp))
        assertTrue(isAllowedAppLink("https://api.example.com:443/", csp))
        assertTrue(isAllowedAppLink("https://a.cdn.example.com/x", csp))
        assertTrue(isAllowedAppLink("http://maps.example.com:8080/", csp))

        assertFalse(isAllowedAppLink("http://api.example.com/", csp), "scheme must match exactly")
        assertFalse(isAllowedAppLink("https://cdn.example.com/", csp), "a wildcard never covers the apex")
        assertFalse(isAllowedAppLink("https://socket.example.com/", csp), "ws declarations are not links")
        assertFalse(isAllowedAppLink("http://maps.example.com/", csp), "port must match")
        assertFalse(isAllowedAppLink("https://api.example.com@evil.com/", csp), "userinfo is not the host")
        assertFalse(isAllowedAppLink("javascript:alert(1)", csp))
        assertFalse(isAllowedAppLink("https://api.example.com./", csp))
        assertFalse(isAllowedAppLink("https://evil.com\\@api.example.com/", csp))
    }

    @Test
    fun `nothing declared means no links and address literals never match`() {
        assertFalse(isAllowedAppLink("https://example.com/", McpAppCspDeclaration()))
        val csp = McpAppCspDeclaration(connectDomains = listOf("https://127.0.0.1"))
        assertFalse(isAllowedAppLink("https://127.0.0.1/", csp))
    }

    @Test
    fun `heights clamp into bounds and junk is ignored`() {
        assertEquals(80, clampAppViewHeight(3.0))
        assertEquals(4000, clampAppViewHeight(9e9))
        assertEquals(261, clampAppViewHeight(260.6))
        assertNull(clampAppViewHeight(-1.0))
        assertNull(clampAppViewHeight(Double.NaN))
    }

    private fun appAttachment(resources: String, toolCallId: String = "call_1", stepId: String? = null) = Attachment(
        type = "ui_resources",
        toolCallId = toolCallId,
        stepId = stepId,
        uiResources = json(resources),
    )

    private val app = """{"resourceId":"r1","uri":"ui://weather/forecast","mimeType":"text/html;profile=mcp-app",
        "text":"<html></html>","serverName":"weather","toolName":"show_forecast","serverBinding":"v1.abc"}"""

    @Test
    fun `selects Apps in order skipping legacy html and index-keyed payloads`() {
        val legacy = """{"resourceId":"r2","uri":"ui://x","mimeType":"text/html","serverName":"weather","toolName":"t"}"""
        val selected = selectToolCallUiResources(
            listOf(
                appAttachment("[$app,$legacy]"),
                appAttachment("""{"0":$app}"""),
                Attachment(type = "image/png", fileId = "f"),
                appAttachment("[$app]", toolCallId = "call_2"),
            ),
        )
        assertEquals(listOf(1, 2), selected.map { it.ordinal })
        assertEquals(listOf("call_1", "call_2"), selected.map { it.attachment.toolCallId })
    }

    @Test
    fun `a repeated identity gets its own key per occurrence`() {
        val selected = selectToolCallUiResources(listOf(appAttachment("[$app,$app]")))
        assertEquals(2, selected.map { it.key }.distinct().size)
        val byStep = selectToolCallUiResources(
            listOf(appAttachment("[$app]", stepId = "s1"), appAttachment("[$app]", stepId = "s2")),
        )
        assertFalse(byStep[0].key == byStep[1].key)
    }

    @Test
    fun `inline html comes from text else from a utf-8 blob`() {
        val fromText = UiResource.from(json(app))!!
        assertEquals("<html></html>", fromText.inlineHtml())
        // "<p>é</p>" in UTF-8, base64.
        val fromBlob = UiResource.from(json("""{"uri":"ui://x","blob":"PHA+w6k8L3A+"}"""))!!
        assertEquals("<p>é</p>", fromBlob.inlineHtml())
        assertNull(UiResource.from(json("""{"uri":"ui://x"}"""))!!.inlineHtml())
    }

    @Test
    fun `the app tool result always exists and carries only an object structuredContent`() {
        val resource = UiResource.from(
            json(
                """{"uri":"ui://x","serverName":"s","toolName":"t","content":[{"type":"text","text":"hi"}],
                "structuredContent":[1,2],"isError":true,"resultMeta":{"k":1}}""",
            ),
        )!!
        val result = resource.appToolResult()
        assertEquals(setOf("content", "isError", "_meta"), result.keys)
        val empty = UiResource.from(json("""{"uri":"ui://x"}"""))!!.appToolResult()
        assertEquals(setOf("content"), empty.keys)
        assertEquals("[]", empty["content"].toString())
    }

    @Test
    fun `a read result yields the matching App document and its content-level csp`() {
        val read = json(
            """{"contents":[{"uri":"ui://other","mimeType":"text/html;profile=mcp-app","text":"no"},
            {"uri":"ui://x","mimeType":"text/html","text":"legacy"},
            {"uri":"ui://x","mimeType":"text/html;profile=mcp-app","text":"<p>app</p>",
             "_meta":{"ui":{"csp":{"connectDomains":["https://api.x"]},"permissions":{"clipboardWrite":{}}}}}]}""",
        )
        val doc = pickAppDocument(read, "ui://x")!!
        assertEquals("<p>app</p>", doc.html)
        assertEquals(listOf("https://api.x"), McpAppCspDeclaration.normalize(doc.csp).connectDomains)
        assertEquals(McpAppPermissions(clipboardWrite = true), McpAppPermissions.supported(doc.permissions))
        assertNull(pickAppDocument(read, "ui://missing"))
    }

    @Test
    fun `only geolocation and clipboard-write are delegated and each only when truthy`() {
        val all = McpAppPermissions.supported(
            json("""{"geolocation":{},"clipboardWrite":true,"camera":{},"microphone":{}}"""),
        )
        assertEquals(McpAppPermissions(geolocation = true, clipboardWrite = true), all)
        assertEquals(listOf("geolocation", "clipboard-write"), all.allowTokens())
        assertEquals("""{"geolocation":{},"clipboardWrite":{}}""", all.toJson().toString())
        assertEquals(
            McpAppPermissions.NONE,
            McpAppPermissions.supported(json("""{"geolocation":false,"clipboardWrite":null,"camera":{}}""")),
        )
        assertEquals(McpAppPermissions.NONE, McpAppPermissions.supported(null))
        assertEquals(McpAppPermissions.NONE, McpAppPermissions.supported(json("""["geolocation"]""")))
        assertTrue(McpAppPermissions.NONE.allowTokens().isEmpty())
    }

    @Test
    fun `legacy html is text html that is not an App and an omitted type counts as html`() {
        fun res(mime: String?) = UiResource.from(
            json(if (mime == null) """{"uri":"ui://a"}""" else """{"uri":"ui://a","mimeType":"$mime"}"""),
        )!!
        assertTrue(res(null).isLegacyHtml)
        assertTrue(res("text/html").isLegacyHtml)
        assertTrue(res("TEXT/HTML; charset=utf-8").isLegacyHtml)
        assertFalse(res("text/html;profile=mcp-app").isLegacyHtml)
        assertFalse(res("text/uri-list").isLegacyHtml)
        assertFalse(res("application/vnd.mcp-ui.remote-dom").isLegacyHtml)
    }

    @Test
    fun `conversation resources are indexed by id and the later one wins`() {
        fun att(json: String) = Attachment(type = "ui_resources", uiResources = json(json))
        val map = collectConversationUiResources(
            sequenceOf(
                att("""[{"resourceId":"a","uri":"ui://1","text":"one"},{"uri":"ui://no-id","text":"x"}]"""),
                Attachment(type = "file", fileId = "f"),
                att("""{"0":{"resourceId":"skip","uri":"ui://2"}}"""),
                att("""[{"resourceId":"a","uri":"ui://3","text":"three"},{"resourceId":"b","uri":"ui://4"}]"""),
            ),
        )
        assertEquals(listOf("a", "b"), map.keys.toList())
        assertEquals("three", map.getValue("a").text)
    }

    @Test
    fun `a resource carries its permissions declaration`() {
        val resource = UiResource.from(json("""{"uri":"ui://a","permissions":{"geolocation":{}}}"""))!!
        assertTrue(McpAppPermissions.supported(resource.permissions).geolocation)
    }
}
