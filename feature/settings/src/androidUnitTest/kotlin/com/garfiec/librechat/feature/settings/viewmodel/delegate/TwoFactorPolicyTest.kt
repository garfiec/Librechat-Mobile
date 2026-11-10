package com.garfiec.librechat.feature.settings.viewmodel.delegate

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Disable 2FA is hidden only where the server refuses it (v0.8.9-rc1 `blockTwoFactorDisableWhenRequired`). */
class TwoFactorPolicyTest {

    @Test
    fun `enforced 2FA blocks disabling for password accounts only`() {
        assertThat(isTwoFactorRequiredFor(true, "local")).isTrue()
        assertThat(isTwoFactorRequiredFor(true, "ldap")).isTrue()
        assertThat(isTwoFactorRequiredFor(true, "google")).isFalse()
        assertThat(isTwoFactorRequiredFor(true, "openid")).isFalse()
    }

    @Test
    fun `without the flag nothing is blocked`() {
        assertThat(isTwoFactorRequiredFor(false, "local")).isFalse()
        assertThat(isTwoFactorRequiredFor(null, "local")).isFalse()
    }

    @Test
    fun `2FA applies to password, LDAP and unknown providers`() {
        assertThat(isTwoFactorPolicyProvider("local")).isTrue()
        assertThat(isTwoFactorPolicyProvider("ldap")).isTrue()
        assertThat(isTwoFactorPolicyProvider(null)).isTrue()
        assertThat(isTwoFactorPolicyProvider("google")).isFalse()
    }
}
