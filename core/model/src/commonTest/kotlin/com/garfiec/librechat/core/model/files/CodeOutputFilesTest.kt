package com.garfiec.librechat.core.model.files

import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.WorkspaceChange
import com.garfiec.librechat.core.model.media.resolveAttachmentUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CodeOutputFilesTest {

    private val fallback = Attachment(
        filename = "big.csv",
        filepath = "/api/files/code/download/sess1/file1",
        expiresAt = 1_000L,
    )

    @Test
    fun recognisesTheFallbackRouteByPathShape() {
        assertTrue(fallback.isCodeDownloadFallback)
        assertTrue(fallback.copy(filepath = "/librechat/api/files/code/download/s/f?execution_profile=stateful").isCodeDownloadFallback)
        assertFalse(Attachment(fileId = "f", filepath = "/uploads/u/report.csv").isCodeDownloadFallback)
        assertFalse(Attachment(fileId = "f", filepath = "/api/files/download/u/f").isCodeDownloadFallback)
        assertFalse(Attachment(fileId = "f").isCodeDownloadFallback)
    }

    @Test
    fun aCodeOutputIsAFallbackOrCarriesItsAgentOrWorkspaceChange() {
        assertTrue(fallback.isCodeOutput)
        assertTrue(Attachment(fileId = "f", agentId = "agent_1").isCodeOutput)
        assertTrue(Attachment(fileId = "f", workspaceChange = WorkspaceChange(path = "/a.py")).isCodeOutput)
        // Another tool's file, e.g. a generated image, carries neither.
        assertFalse(Attachment(fileId = "f", filepath = "/images/u/img.png", toolCallId = "call_1").isCodeOutput)
    }

    @Test
    fun expiresAtItsDeadline() {
        assertFalse(fallback.isExpired(nowMs = 999L))
        assertTrue(fallback.isExpired(nowMs = 1_000L))
        assertFalse(Attachment(fileId = "f").isExpired(nowMs = Long.MAX_VALUE))
    }

    @Test
    fun joinsTheServerOriginForAHostRootDeployment() {
        assertEquals(
            "https://chat.example.com/api/files/code/download/sess1/file1",
            codeDownloadUrl("/api/files/code/download/sess1/file1", "https://chat.example.com/"),
        )
    }

    @Test
    fun aSubpathBaseIsNotDoubled() {
        assertEquals(
            "https://host.example/librechat/api/files/code/download/s/f",
            codeDownloadUrl("/librechat/api/files/code/download/s/f", "https://host.example/librechat"),
        )
    }

    @Test
    fun keepsThePortAndTheQueryVerbatim() {
        val path = "/api/files/code/download/s/f?execution_profile=stateful&execution_route_key=stateful%3Aabc"
        assertEquals("https://host.example:8443$path", codeDownloadUrl(path, "https://host.example:8443/login?redirect_to=%2Fc"))
    }

    @Test
    fun noBaseNoUrl() {
        assertNull(codeDownloadUrl("/api/files/code/download/s/f", ""))
    }

    @Test
    fun imageUrlsForAFallbackUseTheSameJoin() {
        val image = Attachment(filename = "chart.png", filepath = "/librechat/api/files/code/download/s/f")
        assertEquals(
            "https://host.example/librechat/api/files/code/download/s/f",
            resolveAttachmentUrl(image, "https://host.example/librechat"),
        )
    }
}
