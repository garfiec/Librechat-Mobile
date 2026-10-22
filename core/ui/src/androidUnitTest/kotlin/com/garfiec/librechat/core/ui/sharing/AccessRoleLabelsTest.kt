package com.garfiec.librechat.core.ui.sharing

import com.garfiec.librechat.core.model.AccessRole
import com.garfiec.librechat.core.model.ResourceType
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.acl_role_editor
import com.garfiec.librechat.core.ui.resources.acl_role_owner
import com.garfiec.librechat.core.ui.resources.acl_role_viewer
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AccessRoleLabelsTest {

    /** `GET /api/permissions/{type}/roles` as `seedDefaultRoles` stores them. */
    private fun seeded(type: String) = listOf(
        AccessRole(accessRoleId = "${type}_viewer", name = "com_ui_role_viewer", permissionBits = 1),
        AccessRole(accessRoleId = "${type}_editor", name = "com_ui_role_editor", permissionBits = 3),
        AccessRole(accessRoleId = "${type}_owner", name = "com_ui_role_owner", permissionBits = 15),
    )

    @Test
    fun `a web localization key resolves to the app's translation, for skills and agents alike`() {
        listOf(ResourceType.SKILL, ResourceType.AGENT).forEach { type ->
            val roles = seeded(type)
            fun label(suffix: String) = resolveAccessRoleLabel(type, "${type}_$suffix", roles)
            assertThat(label("viewer")).isEqualTo(AccessRoleLabel.BuiltIn(Res.string.acl_role_viewer))
            assertThat(label("editor")).isEqualTo(AccessRoleLabel.BuiltIn(Res.string.acl_role_editor))
            assertThat(label("owner")).isEqualTo(AccessRoleLabel.BuiltIn(Res.string.acl_role_owner))
        }
    }

    @Test
    fun `a server name that is plain text is shown as sent`() {
        val roles = listOf(AccessRole(accessRoleId = "skill_reviewer", name = "Reviewer"))
        assertThat(resolveAccessRoleLabel(ResourceType.SKILL, "skill_reviewer", roles)).isEqualTo(AccessRoleLabel.Server("Reviewer"))
    }

    @Test
    fun `a known id is translated even when the roles list hasn't loaded`() {
        assertThat(resolveAccessRoleLabel(ResourceType.SKILL, "skill_editor", emptyList()))
            .isEqualTo(AccessRoleLabel.BuiltIn(Res.string.acl_role_editor))
        assertThat(resolveAccessRoleLabel(ResourceType.AGENT, "agent_viewer", emptyList()))
            .isEqualTo(AccessRoleLabel.BuiltIn(Res.string.acl_role_viewer))
    }

    @Test
    fun `another type's id is not taken for this type's role`() {
        assertThat(resolveAccessRoleLabel(ResourceType.AGENT, "skill_owner", emptyList())).isEqualTo(AccessRoleLabel.Id("skill_owner"))
    }

    @Test
    fun `an unknown role with an unknown key falls back to its id`() {
        val roles = listOf(AccessRole(accessRoleId = "skill_auditor", name = "com_ui_role_auditor"))
        assertThat(resolveAccessRoleLabel(ResourceType.SKILL, "skill_auditor", roles)).isEqualTo(AccessRoleLabel.Id("skill_auditor"))
    }

    @Test
    fun `new grants start at the viewer role, wherever the server lists it`() {
        assertThat(defaultAccessRoleId(ResourceType.SKILL, seeded(ResourceType.SKILL).reversed())).isEqualTo("skill_viewer")
        assertThat(defaultAccessRoleId(ResourceType.AGENT, seeded(ResourceType.AGENT).reversed())).isEqualTo("agent_viewer")
    }

    @Test
    fun `without a viewer role the default is the role with the fewest bits`() {
        val roles = listOf(
            AccessRole(accessRoleId = "custom_owner", permissionBits = 15),
            AccessRole(accessRoleId = "custom_reader", permissionBits = 1),
        )
        assertThat(defaultAccessRoleId(ResourceType.AGENT, roles)).isEqualTo("custom_reader")
        assertThat(defaultAccessRoleId(ResourceType.AGENT, emptyList())).isNull()
    }
}
