package com.garfiec.librechat.core.data.skills

import com.garfiec.librechat.core.model.PermissionBits
import com.garfiec.librechat.core.model.User

/** `skill.source` values the client branches on. Absent means [INLINE], as upstream reads it. */
object SkillSource {
    const val INLINE = "inline"
    const val DEPLOYMENT = "deployment"
}

/**
 * Who is looking at a skill: the id an `author` is compared against, and whether they hold the
 * ADMIN system role.
 */
data class SkillViewer(val userId: String?, val isAdmin: Boolean)

/**
 * The viewer for [this] signed-in user. The id is `id ?: _id`: the user payload carries the
 * ObjectId as `id` and some servers only as `_id`, and skills store `author` as that ObjectId's
 * 24-hex string. A mismatch fails silently (owned skills read as shared, owners lose Edit), so
 * this is the only place the comparison key is derived.
 */
fun User.toSkillViewer(): SkillViewer = SkillViewer(
    userId = (id ?: mongoId)?.takeIf { it.isNotBlank() },
    isAdmin = role == ADMIN_ROLE,
)

private const val ADMIN_ROLE = "ADMIN"

/**
 * What the viewer may change on one skill, matching what the server enforces at e8f3be0:
 *
 * - `PATCH /:id` and `DELETE /:id` run `checkSkillCreate` (role SKILLS.CREATE) and then the ACL
 *   check for EDIT / DELETE (`routes/skills.js`).
 * - The file routes run only the ACL EDIT check, with no CREATE, and the upload handler refuses a
 *   skill whose source is not inline (`upload.ts`).
 * - Any non-VIEW route on a deployment skill answers 403 "Deployment skills are read-only", ahead
 *   of the ACL and of admin (`canAccessSkillResource.js`).
 * - `PUT /api/permissions/skill/:id` needs the ACL SHARE bit AND role SKILLS.SHARE
 *   (`accessPermissions.js` + `checkShareAccess`).
 *
 * Owner or ADMIN counts as holding every bit, as web's `useSkillPermissions` does.
 */
data class SkillAccess(
    val canEdit: Boolean = false,
    val canDelete: Boolean = false,
    val canEditFiles: Boolean = false,
    val canShare: Boolean = false,
) {
    companion object {
        val NONE = SkillAccess()
    }
}

/**
 * Resolves [SkillAccess]. [viewer] null (the user hasn't loaded or failed to) grants no
 * owner/admin privilege; the per-skill [permissionBits] still count. [canCreate] and [canShareRole]
 * are the role's SKILLS.CREATE and SKILLS.SHARE, read fail-closed by the caller.
 */
fun resolveSkillAccess(
    author: String?,
    source: String?,
    viewer: SkillViewer?,
    permissionBits: Int,
    canCreate: Boolean,
    canShareRole: Boolean,
): SkillAccess {
    if (source == SkillSource.DEPLOYMENT) return SkillAccess.NONE
    val privileged = viewer != null && (viewer.isAdmin || isSkillOwner(author, viewer.userId))
    fun has(bit: Int) = privileged || PermissionBits.has(permissionBits, bit)
    return SkillAccess(
        canEdit = canCreate && has(PermissionBits.EDIT),
        canDelete = canCreate && has(PermissionBits.DELETE),
        canEditFiles = (source ?: SkillSource.INLINE) == SkillSource.INLINE && has(PermissionBits.EDIT),
        canShare = canShareRole && has(PermissionBits.SHARE),
    )
}

/** Whether [userId] authored the skill. Both must be known; a blank id never matches. */
fun isSkillOwner(author: String?, userId: String?): Boolean =
    !author.isNullOrBlank() && !userId.isNullOrBlank() && author == userId
