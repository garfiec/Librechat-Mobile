package com.garfiec.librechat.core.model.code

import kotlinx.serialization.Serializable

/**
 * Settings › Code execution: the user's own machines, pairing a new one, and approving a worker's
 * setup. MIRRORED from upstream `packages/data-provider/src/types.ts` (`TCodeEnvironmentSummary`,
 * `TCodeEnvironmentsResponse`, `TCodeEnvironmentPairingResponse`), `codeSetup.ts` and
 * `code/worker.ts`; see `scripts/mirrors.json`.
 */

/** A machine this user can see, as `GET /api/code-environments` lists it. */
@Serializable
data class CodeEnvironmentSummary(
    val id: String,
    val name: String? = null,
    /** `managed` or `attached`. */
    val type: String? = null,
    /** May change [settings]; upstream leaves it out when false. */
    val canEdit: Boolean? = null,
    val canDelete: Boolean? = null,
    val configSchema: CodeEnvironmentConfigSchema? = null,
    val settings: CodeEnvironmentUserSettings? = null,
) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: id
}

/** A deployment control plane a user may pair a personal worker with. */
@Serializable
data class CodeControlPlane(
    val id: String,
    val name: String? = null,
) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: id
}

@Serializable
data class CodeEnvironmentsResponse(
    val environments: List<CodeEnvironmentSummary> = emptyList(),
    /** Empty unless self-service workers are on: then pairing is not offered. */
    val controlPlanes: List<CodeControlPlane> = emptyList(),
)

/** `POST /api/code-environments/pairings`. */
@Serializable
data class CodeEnvironmentPairingRequest(val name: String, val controlPlaneId: String)

/** A one-time pairing the worker redeems; shown once and gone at [expiresAt]. */
@Serializable
data class CodeWorkerPairing(
    val workerId: String,
    val code: String,
    /** ISO 8601. */
    val expiresAt: String,
    val endpoint: String,
)

@Serializable
data class CodeEnvironmentPairingResponse(
    val environment: CodeEnvironmentSummary? = null,
    val pairing: CodeWorkerPairing,
)

/** `PATCH /api/code-environments/:id/settings` body. */
@Serializable
data class CodeEnvironmentSettingsRequest(val settings: CodeEnvironmentUserSettings)

/** The answer to a settings change or a removal: the machine as it now is. */
@Serializable
data class CodeEnvironmentResponse(val environment: CodeEnvironmentSummary? = null)

/** The shell [CodeWorkerCommand.setup] writes for. */
enum class CodeWorkerShell { POSIX, POWERSHELL }

/** Upstream `createCodeWorkerSetupCommand`: pair the worker, then run it with a default workspace. */
object CodeWorkerCommand {
    /** Web's Settings page passes both permissions on. */
    fun setup(
        pairing: CodeWorkerPairing,
        shell: CodeWorkerShell,
        allowWorkspaceWrites: Boolean = true,
        allowWorkspaceCommands: Boolean = true,
    ): String {
        val quote: (String) -> String = if (shell == CodeWorkerShell.POWERSHELL) ::quotePowerShell else ::quotePosix
        val pair = "librechat-code pair ${quote(pairing.endpoint)} ${quote(pairing.code)} --worker-id ${quote(pairing.workerId)}"
        val run = (
            listOf("librechat-code run", "--default-workspace") +
                listOfNotNull(
                    "--allow-workspace-writes".takeIf { allowWorkspaceWrites },
                    "--allow-workspace-commands".takeIf { allowWorkspaceCommands },
                )
            ).joinToString(" ")
        return if (shell == CodeWorkerShell.POWERSHELL) {
            "$pair\n\$env:LIBRECHAT_CODE_WORKER_ID = ${quote(pairing.workerId)}\n$run"
        } else {
            "$pair\nLIBRECHAT_CODE_WORKER_ID=${quote(pairing.workerId)} $run"
        }
    }

    private fun quotePosix(value: String) = "'" + value.replace("'", "'\\''") + "'"

    private fun quotePowerShell(value: String) = "'" + value.replace("'", "''") + "'"
}

/** A worker's setup request a signed-in user approves or denies (`/api/code-environments/setup/:attemptId`). */
@Serializable
data class CodeSetupAttempt(
    val attemptId: String,
    /** `pending`, `provisioning`, `approved`, `completed`, `denied`, `cancelled`, `expired` or `failed`. */
    val status: String,
    val expiresAt: String? = null,
    val verificationCode: String? = null,
    val machineName: String? = null,
    val workspace: CodeSetupWorkspace? = null,
    val allowWorkspaceWrites: Boolean? = null,
    val allowWorkspaceCommands: Boolean? = null,
    /** SHA-256 of the machine's DER SPKI public key, hex. */
    val keyFingerprint: String? = null,
    val controlPlanes: List<CodeControlPlane> = emptyList(),
    /** The plane an approved attempt is bound to. */
    val controlPlaneId: String? = null,
    val failure: CodeSetupFailure? = null,
) {
    companion object {
        const val PENDING = "pending"
        const val PROVISIONING = "provisioning"
        const val APPROVED = "approved"
        const val COMPLETED = "completed"
        const val DENIED = "denied"
        const val CANCELLED = "cancelled"
        const val EXPIRED = "expired"
        const val FAILED = "failed"

        private const val TOKEN_LENGTH = 43

        /**
         * The attempt id in a worker's setup link (`…/code/setup/<id>`), or the bare id: 43
         * base64url characters, as upstream's `isBrowserEnrollmentToken` checks. Null otherwise.
         */
        fun idFrom(input: String): String? {
            val trimmed = input.trim()
            val marker = "/code/setup/"
            val candidate = trimmed.substringAfter(marker, trimmed).substringBefore('?').substringBefore('#').trimEnd('/')
            return candidate.takeIf { id ->
                id.length == TOKEN_LENGTH && id.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }
            }
        }
    }
}

/** `private` (a private workspace) or `directory` (a local directory, path not shared). */
@Serializable
data class CodeSetupWorkspace(val type: String? = null)

@Serializable
data class CodeSetupFailure(val code: String? = null, val retryable: Boolean? = null)

/** `POST /api/code-environments/setup/:attemptId/approve` body: exactly one key. */
@Serializable
data class CodeSetupApprovalRequest(val controlPlaneId: String)
