package com.garfiec.librechat.feature.conversations.drawer

import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DrawerSkillsGateTest {

    private fun role(use: Boolean) =
        UserRolePermissions(name = "USER", permissions = mapOf("SKILLS" to mapOf("USE" to use)))

    @Test
    fun `role USE and the skills capability open it`() {
        assertThat(isSkillsNavEnabled(role(use = true), listOf("tools", "skills"))).isTrue()
    }

    @Test
    fun `the role is permissive until it loads`() {
        assertThat(isSkillsNavEnabled(null, listOf("skills"))).isTrue()
    }

    @Test
    fun `a role without USE closes it`() {
        assertThat(isSkillsNavEnabled(role(use = false), listOf("skills"))).isFalse()
    }

    @Test
    fun `the capability fails closed`() {
        assertThat(isSkillsNavEnabled(role(use = true), null)).isFalse()
        assertThat(isSkillsNavEnabled(role(use = true), emptyList())).isFalse()
        assertThat(isSkillsNavEnabled(role(use = true), listOf("tools", "memory"))).isFalse()
    }
}
