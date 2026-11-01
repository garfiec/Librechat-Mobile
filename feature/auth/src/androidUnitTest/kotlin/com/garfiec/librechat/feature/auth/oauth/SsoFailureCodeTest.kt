package com.garfiec.librechat.feature.auth.oauth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SsoFailureCodeTest {

    @Test
    fun `the strict-tenant callback redirect reports its specific code`() {
        assertThat(
            ssoFailureCode("https://chat.example.com/login?redirect=false&error=auth_failed&code=TENANT_CONTEXT_REQUIRED"),
        ).isEqualTo("TENANT_CONTEXT_REQUIRED")
    }

    @Test
    fun `any other page reports its error parameter`() {
        assertThat(ssoFailureCode("https://chat.example.com/login?error=auth_failed")).isEqualTo("auth_failed")
        // A callback's own authorization code is not a failure code.
        assertThat(ssoFailureCode("https://chat.example.com/oauth/google/callback?code=4%2F0Ab&state=s")).isNull()
    }
}
