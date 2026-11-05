package com.garfiec.librechat.core.model.code

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CodeEnvironmentManagementTest {
    private val pairing = CodeWorkerPairing(
        workerId = "code-abc",
        code = "it's-secret",
        expiresAt = "2026-11-04T12:00:00.000Z",
        endpoint = "https://code.example.com",
    )

    @Test
    fun posixCommandQuotesEveryValueAndPassesBothPermissions() {
        assertEquals(
            "librechat-code pair 'https://code.example.com' 'it'\\''s-secret' --worker-id 'code-abc'\n" +
                "LIBRECHAT_CODE_WORKER_ID='code-abc' librechat-code run --default-workspace " +
                "--allow-workspace-writes --allow-workspace-commands",
            CodeWorkerCommand.setup(pairing, CodeWorkerShell.POSIX),
        )
    }

    @Test
    fun powerShellCommandSetsTheWorkerIdOnItsOwnLine() {
        assertEquals(
            "librechat-code pair 'https://code.example.com' 'it''s-secret' --worker-id 'code-abc'\n" +
                "\$env:LIBRECHAT_CODE_WORKER_ID = 'code-abc'\n" +
                "librechat-code run --default-workspace --allow-workspace-writes --allow-workspace-commands",
            CodeWorkerCommand.setup(pairing, CodeWorkerShell.POWERSHELL),
        )
    }

    @Test
    fun theSetupIdComesFromALinkOrStandsAlone() {
        val id = "A".repeat(42) + "_"
        assertEquals(id, CodeSetupAttempt.idFrom("https://chat.example.com/code/setup/$id"))
        assertEquals(id, CodeSetupAttempt.idFrom("  https://chat.example.com/sub/code/setup/$id/?x=1 "))
        assertEquals(id, CodeSetupAttempt.idFrom(id))
        assertNull(CodeSetupAttempt.idFrom("https://chat.example.com/code/setup/short"))
        assertNull(CodeSetupAttempt.idFrom("A".repeat(42) + "!"))
    }

    @Test
    fun aScopeTheDeploymentDropsFallsBackToTheFirstAllowed() {
        assertEquals(StatefulCodeScope.ALL, StatefulCodeScope.allowed(null))
        assertEquals(listOf("user", "conversation"), StatefulCodeScope.allowed(listOf("conversation", "user")))
        assertEquals("conversation", StatefulCodeScope.resolve("agent-user", listOf("conversation")))
        assertEquals("agent-user", StatefulCodeScope.resolve("agent-user", null))
    }
}
