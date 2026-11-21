package com.garfiec.librechat.feature.skills

import com.garfiec.librechat.core.model.AccessRole
import com.garfiec.librechat.core.model.Principal
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SkillShareLabelsTest {

    private val author = "65f0c0ffee0000000000abcd"

    /** `GET /api/permissions/skill/roles` as `seedDefaultRoles` stores them. */
    private val serverRoles = listOf(
        AccessRole(accessRoleId = "skill_viewer", name = "com_ui_role_viewer", permissionBits = 1),
        AccessRole(accessRoleId = "skill_editor", name = "com_ui_role_editor", permissionBits = 3),
        AccessRole(accessRoleId = "skill_owner", name = "com_ui_role_owner", permissionBits = 15),
    )

    // --- default role (the label and default rules themselves are tested in core/ui) ---

    @Test
    fun `new skill grants start at the viewer role, wherever the server lists it`() {
        assertThat(defaultSkillAccessRoleId(serverRoles.reversed())).isEqualTo("skill_viewer")
    }

    // --- summary row ---

    private val ownerEntry = Principal(type = "user", id = author, name = "Ada", accessRoleId = "skill_owner")

    @Test
    fun `nothing is claimed before the grants load`() {
        assertThat(shareSummary(loaded = false, principals = emptyList(), isPublic = false, authorId = author))
            .isEqualTo(ShareSummary.Unknown)
    }

    @Test
    fun `a skill only its author holds is private`() {
        assertThat(shareSummary(loaded = true, principals = listOf(ownerEntry), isPublic = false, authorId = author))
            .isEqualTo(ShareSummary.Private)
        assertThat(shareSummary(loaded = true, principals = emptyList(), isPublic = false, authorId = author))
            .isEqualTo(ShareSummary.Private)
    }

    @Test
    fun `grants are counted without the author, and public rides along`() {
        val principals = listOf(
            ownerEntry,
            Principal(type = "user", id = "65f0c0ffee0000000000b002", accessRoleId = "skill_editor"),
            // A group can share the author's id space only by coincidence; it is still someone else.
            Principal(type = "group", id = author, accessRoleId = "skill_viewer"),
        )
        assertThat(shareSummary(loaded = true, principals = principals, isPublic = true, authorId = author))
            .isEqualTo(ShareSummary.Shared(count = 2, isPublic = true))
        assertThat(shareSummary(loaded = true, principals = listOf(ownerEntry), isPublic = true, authorId = author))
            .isEqualTo(ShareSummary.Shared(count = 0, isPublic = true))
    }
}
