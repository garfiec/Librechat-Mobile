package com.garfiec.librechat.core.data.skills

import com.garfiec.librechat.core.model.PermissionBits
import com.garfiec.librechat.core.model.SkillSummary
import com.garfiec.librechat.core.model.User
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * The per-skill gates against the server's route order at e8f3be0, fed with real-shaped JSON: the
 * owner branches compare the skill's 24-hex `author` with the user's `id ?: _id`, and a mismatch
 * in either shape fails silently (owners lose Edit, owned skills read as shared).
 */
class SkillAccessTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val ownerId = "65f0c0ffee0000000000abcd"

    /** `GET /api/user` as the server sends it: the ObjectId as `id`. */
    private val userWithId = json.decodeFromString(
        User.serializer(),
        """{"id":"$ownerId","name":"Ada","username":"ada","email":"ada@example.com","role":"USER",""" +
            """"provider":"local","emailVerified":true,"createdAt":"2026-01-01T00:00:00.000Z"}""",
    )

    /** The login payload shape, which carries only `_id`. */
    private val userWithMongoIdOnly = json.decodeFromString(
        User.serializer(),
        """{"_id":"$ownerId","email":"ada@example.com","name":"Ada","role":"USER"}""",
    )

    private val admin = json.decodeFromString(
        User.serializer(),
        """{"id":"65f0c0ffee00000000001111","email":"root@example.com","role":"ADMIN"}""",
    )

    private val otherUser = json.decodeFromString(
        User.serializer(),
        """{"id":"65f0c0ffee00000000002222","email":"bob@example.com","role":"USER"}""",
    )

    private fun skill(source: String? = "inline") = json.decodeFromString(
        SkillSummary.serializer(),
        """{"_id":"6650f1a2b3c4d5e6f7a8b9c0","name":"pdf-tools","author":"$ownerId","authorName":"Ada",""" +
            (if (source != null) """"source":"$source",""" else "") +
            """"fileCount":1,"version":2}""",
    )

    private fun access(
        user: User?,
        bits: Int = 0,
        canCreate: Boolean = true,
        canShareRole: Boolean = true,
        source: String? = "inline",
    ): SkillAccess {
        val s = skill(source)
        return resolveSkillAccess(
            author = s.author,
            source = s.source,
            viewer = user?.toSkillViewer(),
            permissionBits = bits,
            canCreate = canCreate,
            canShareRole = canShareRole,
        )
    }

    private val all = SkillAccess(canEdit = true, canDelete = true, canEditFiles = true, canShare = true)

    @Test
    fun `the owner holds every gate whichever id field the user payload carries`() {
        assertThat(access(userWithId)).isEqualTo(all)
        assertThat(access(userWithMongoIdOnly)).isEqualTo(all)
    }

    @Test
    fun `an admin holds every gate on a skill they do not own`() {
        assertThat(access(admin)).isEqualTo(all)
    }

    @Test
    fun `a shared viewer gets nothing`() {
        assertThat(access(otherUser, bits = PermissionBits.VIEW)).isEqualTo(SkillAccess.NONE)
    }

    @Test
    fun `a shared editor edits the skill and its files but cannot delete or share`() {
        assertThat(access(otherUser, bits = PermissionBits.VIEW or PermissionBits.EDIT)).isEqualTo(
            SkillAccess(canEdit = true, canDelete = false, canEditFiles = true, canShare = false),
        )
    }

    @Test
    fun `a shared owner role holds the gates its bits give`() {
        val bits = PermissionBits.VIEW or PermissionBits.EDIT or PermissionBits.DELETE or PermissionBits.SHARE
        assertThat(access(otherUser, bits = bits)).isEqualTo(all)
    }

    @Test
    fun `without role CREATE edit and delete close but files stay editable`() {
        // PATCH and DELETE run checkSkillCreate first; the file routes don't.
        assertThat(access(userWithId, canCreate = false)).isEqualTo(
            SkillAccess(canEdit = false, canDelete = false, canEditFiles = true, canShare = true),
        )
    }

    @Test
    fun `without role SHARE the share gate closes even for the owner`() {
        assertThat(access(userWithId, canShareRole = false).canShare).isFalse()
    }

    @Test
    fun `a deployment skill is read-only even for an admin with every bit`() {
        assertThat(access(admin, bits = 15, source = "deployment")).isEqualTo(SkillAccess.NONE)
    }

    @Test
    fun `an externally synced skill keeps edit but its files are read-only`() {
        val synced = access(userWithId, source = "github")
        assertThat(synced.canEdit).isTrue()
        assertThat(synced.canEditFiles).isFalse()
    }

    @Test
    fun `an absent source reads as inline`() {
        assertThat(access(userWithId, source = null).canEditFiles).isTrue()
    }

    @Test
    fun `an unknown viewer has no owner or admin privilege but keeps its bits`() {
        assertThat(access(user = null)).isEqualTo(SkillAccess.NONE)
        assertThat(access(user = null, bits = PermissionBits.EDIT).canEdit).isTrue()
    }

    @Test
    fun `a user without any id never owns a skill`() {
        val idless = json.decodeFromString(User.serializer(), """{"email":"x@example.com","role":"USER"}""")
        assertThat(idless.toSkillViewer().userId).isNull()
        assertThat(access(idless)).isEqualTo(SkillAccess.NONE)
    }

    @Test
    fun `the viewer prefers id over _id`() {
        val both = json.decodeFromString(
            User.serializer(),
            """{"id":"$ownerId","_id":"65f0c0ffee00000000009999","email":"a@example.com"}""",
        )
        assertThat(both.toSkillViewer().userId).isEqualTo(ownerId)
    }
}
