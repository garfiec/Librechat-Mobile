package com.garfiec.librechat.feature.conversations.drawer

import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DrawerPermissionFlagsTest {

    private fun role(vararg permissions: Pair<String, Map<String, Boolean>>) =
        UserRolePermissions(name = "USER", permissions = mapOf(*permissions))

    private fun entryVisible(role: UserRolePermissions?): Boolean {
        val flags = drawerPermissionFlags(role)
        return DrawerUiState(agentsEnabled = flags.agentsEnabled, marketplaceEnabled = flags.marketplaceEnabled)
            .agentsEntryVisible
    }

    @Test
    fun `the Agents entry shows while the role is unknown`() {
        assertThat(entryVisible(null)).isTrue()
        assertThat(entryVisible(role())).isTrue()
    }

    @Test
    fun `the Agents entry shows with both AGENTS and MARKETPLACE use`() {
        assertThat(entryVisible(role("AGENTS" to mapOf("USE" to true), "MARKETPLACE" to mapOf("USE" to true)))).isTrue()
    }

    @Test
    fun `a role without MARKETPLACE use hides the Agents entry`() {
        val denied = role("AGENTS" to mapOf("USE" to true), "MARKETPLACE" to mapOf("USE" to false))

        assertThat(drawerPermissionFlags(denied).marketplaceEnabled).isFalse()
        assertThat(entryVisible(denied)).isFalse()
    }

    @Test
    fun `a role without AGENTS use hides the Agents entry`() {
        assertThat(entryVisible(role("AGENTS" to mapOf("USE" to false), "MARKETPLACE" to mapOf("USE" to true)))).isFalse()
    }

    @Test
    fun `MARKETPLACE use gates only the Agents entry`() {
        val flags = drawerPermissionFlags(role("MARKETPLACE" to mapOf("USE" to false)))

        assertThat(flags.agentsEnabled).isTrue()
        assertThat(flags.skillsEnabled).isTrue()
        assertThat(flags.bookmarksEnabled).isTrue()
        assertThat(flags.schedulesPermitted).isTrue()
    }
}
