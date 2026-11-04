package com.garfiec.librechat.core.model.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EmailChangeErrorTest {

    @Test
    fun every_request_code_maps_to_its_own_value() {
        val codes = mapOf(
            "invalid_request" to EmailChangeError.InvalidRequest,
            "same_email" to EmailChangeError.SameEmail,
            "email_change_disabled" to EmailChangeError.Disabled,
            "local_account_required" to EmailChangeError.LocalAccountRequired,
            "current_password_invalid" to EmailChangeError.CurrentPasswordInvalid,
            "email_domain_not_allowed" to EmailChangeError.DomainNotAllowed,
            "email_in_use" to EmailChangeError.EmailInUse,
            "account_modified" to EmailChangeError.AccountModified,
            "request_in_progress" to EmailChangeError.RequestInProgress,
            "email_delivery_failed" to EmailChangeError.DeliveryFailed,
            "email_service_unavailable" to EmailChangeError.ServiceUnavailable,
        )
        codes.forEach { (code, expected) -> assertEquals(expected, EmailChangeError.fromCode(code), code) }
    }

    @Test
    fun an_unknown_or_missing_code_is_unknown() {
        assertEquals(EmailChangeError.Unknown, EmailChangeError.fromCode("invalid_token"))
        assertEquals(EmailChangeError.Unknown, EmailChangeError.fromCode("something_new"))
        assertEquals(EmailChangeError.Unknown, EmailChangeError.fromCode(null))
    }

    @Test
    fun errors_land_under_the_field_they_are_about() {
        assertEquals(EmailChangeField.Password, EmailChangeError.CurrentPasswordInvalid.field)
        assertEquals(EmailChangeField.Email, EmailChangeError.EmailInUse.field)
        assertEquals(EmailChangeField.Email, EmailChangeError.SameEmail.field)
        assertNull(EmailChangeError.ServiceUnavailable.field)
        assertNull(EmailChangeError.RateLimited.field)
    }
}
