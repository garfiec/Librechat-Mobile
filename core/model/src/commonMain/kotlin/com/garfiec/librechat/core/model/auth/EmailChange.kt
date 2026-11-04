package com.garfiec.librechat.core.model.auth

import kotlinx.serialization.Serializable

/** Body of `POST /api/user/email/change` (v0.8.9-rc1). Plain JSON, no `arg` wrapper. */
@Serializable
data class RequestEmailChangeRequest(
    val currentPassword: String,
    val newEmail: String,
)

/** Every answer of `POST /api/user/email/change`, success or refusal. */
@Serializable
data class EmailChangeResponse(
    val message: String? = null,
    val code: String? = null,
)

/**
 * Why `POST /api/user/email/change` refused, from the body's `code` (upstream
 * `EmailChangeErrorCode`, `packages/data-provider/src/types.ts`, registered in
 * `scripts/mirrors.json`). The server's `message` is never shown; each value has its own string.
 * `invalid_token` is in the union but only the confirm route returns it, and the app never calls
 * that route.
 */
enum class EmailChangeError(val code: String?) {
    InvalidRequest("invalid_request"),
    SameEmail("same_email"),
    Disabled("email_change_disabled"),
    LocalAccountRequired("local_account_required"),
    CurrentPasswordInvalid("current_password_invalid"),
    DomainNotAllowed("email_domain_not_allowed"),
    EmailInUse("email_in_use"),
    AccountModified("account_modified"),
    RequestInProgress("request_in_progress"),
    DeliveryFailed("email_delivery_failed"),
    ServiceUnavailable("email_service_unavailable"),

    /** 429 from `emailChangeLimiter`, which sends no code. */
    RateLimited(null),

    /** No answer: offline, timeout, TLS. */
    Network(null),

    /** Anything else, including a code this build doesn't know. */
    Unknown(null),
    ;

    /** Which field the message belongs under; null = the dialog itself. */
    val field: EmailChangeField?
        get() = when (this) {
            InvalidRequest, SameEmail, DomainNotAllowed, EmailInUse -> EmailChangeField.Email
            CurrentPasswordInvalid -> EmailChangeField.Password
            else -> null
        }

    companion object {
        fun fromCode(code: String?): EmailChangeError =
            code?.let { c -> entries.firstOrNull { it.code == c } } ?: Unknown
    }
}

enum class EmailChangeField { Email, Password }

/**
 * Upstream `requestSchema.currentPassword` `max(128)` (`packages/api/src/auth/email.ts`), registered
 * in `scripts/mirrors.json`. A longer password could never match, so it is refused before sending.
 */
const val MAX_EMAIL_CHANGE_PASSWORD_LENGTH = 128

/**
 * Upstream `EMAIL_CHANGE_USER_WINDOW` default, in seconds
 * (`api/server/middleware/limiters/emailChangeLimiter.js`): how long Send stays off after a 429 that
 * carried no `Retry-After`. An operator can change the window; the server still decides.
 */
const val EMAIL_CHANGE_COOLDOWN_SECONDS = 120L
