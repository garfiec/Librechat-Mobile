package com.garfiec.librechat.core.model.permissions

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanReachMemoriesSettingsTest {

    private fun memoriesRole(vararg bits: Pair<String, Boolean>) = UserRolePermissions(
        name = "USER",
        permissions = mapOf("MEMORIES" to mapOf(*bits)),
    )

    @Test
    fun anUnknownRoleMayReach() {
        val role: UserRolePermissions? = null
        assertTrue(role.canReachMemoriesSettings())
    }

    @Test
    fun aRoleWithoutAMemoriesBlockMayReach() {
        assertTrue(UserRolePermissions(name = "USER").canReachMemoriesSettings())
    }

    @Test
    fun readAloneIsEnough() {
        assertTrue(memoriesRole("USE" to true, "READ" to true, "OPT_OUT" to false).canReachMemoriesSettings())
    }

    @Test
    fun optOutAloneIsEnough() {
        assertTrue(memoriesRole("USE" to true, "READ" to false, "OPT_OUT" to true).canReachMemoriesSettings())
    }

    @Test
    fun aMissingOptOutKeyIsPermissive() {
        assertTrue(memoriesRole("USE" to true, "READ" to false).canReachMemoriesSettings())
    }

    @Test
    fun neitherReadNorOptOutMayNotReach() {
        assertFalse(memoriesRole("USE" to true, "READ" to false, "OPT_OUT" to false).canReachMemoriesSettings())
    }

    @Test
    fun useDeniedMayNotReachEvenWithBoth() {
        assertFalse(memoriesRole("USE" to false, "READ" to true, "OPT_OUT" to true).canReachMemoriesSettings())
    }
}
