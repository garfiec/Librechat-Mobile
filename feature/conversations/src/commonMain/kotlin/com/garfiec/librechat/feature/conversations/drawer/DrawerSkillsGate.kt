package com.garfiec.librechat.feature.conversations.drawer

import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.permissions.hasAccessOrPermissive

/**
 * Whether the drawer offers Skills: role SKILLS.USE AND the agents endpoint advertising the
 * `skills` capability, as web's side nav does (`useSideNavLinks.ts`: `hasAccessToSkills &&
 * skillsEnabled`).
 *
 * The role half is permissive until the role loads, like the drawer's other entries. The
 * capability half fails CLOSED on a config that hasn't arrived ([agentsCapabilities] null) and on
 * an empty list: without the capability the server injects no skill into any run, so the page
 * would manage skills nothing uses. Mutation gating lives fail-closed inside feature/skills.
 */
internal fun isSkillsNavEnabled(role: UserRolePermissions?, agentsCapabilities: List<String>?): Boolean =
    role.hasAccessOrPermissive(PermissionType.SKILLS, Permission.USE) &&
        agentsCapabilities?.contains(ToolConstants.SKILLS) == true
