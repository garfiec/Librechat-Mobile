package com.garfiec.librechat.core.model.acl

/**
 * A permission change the server refused for a reason that passes (v0.8.9-rc1): the update is
 * audited and fail-closed, and its error body carries one of upstream's approved codes
 * (`APPROVED_ERROR_CODES` in `packages/api/src/acl/accessControlService.ts`, five of them from
 * 0.8.9). Every one is retryable: the same change can be sent again once the server recovers.
 */
enum class PermissionUpdateFailure(val code: String) {
    /** 409: another change to this resource's permissions holds its lock. */
    BUSY("RESOURCE_PERMISSIONS_BUSY"),

    /** 503: the lock store failed. */
    UNAVAILABLE("RESOURCE_PERMISSIONS_UNAVAILABLE"),

    /** The change could not be written to the audit log, so it was not made. */
    AUDIT_UNAVAILABLE("PERMISSION_AUDIT_UNAVAILABLE"),

    /** 500: the server could not prepare the recipients the change grants access to. */
    PRINCIPAL_PREPARATION_FAILED("PRINCIPAL_PREPARATION_FAILED"),

    /** 502: the identity directory that verifies a recipient (Entra ID) did not answer in time. */
    DIRECTORY_VERIFICATION_UNAVAILABLE("DIRECTORY_VERIFICATION_UNAVAILABLE"),
    ;

    companion object {
        fun fromCode(code: String?): PermissionUpdateFailure? = entries.firstOrNull { it.code == code }
    }
}
