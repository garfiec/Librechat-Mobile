package com.garfiec.librechat.core.model.permissions

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanOpenMemoriesTest {

    private fun memoriesRole(vararg bits: Pair<String, Boolean>) = UserRolePermissions(
        name = "USER",
        permissions = mapOf("MEMORIES" to mapOf(*bits)),
    )

    @Test
    fun anUnknownRoleMayOpen() {
        val role: UserRolePermissions? = null
        assertTrue(role.canOpenMemories())
    }

    @Test
    fun aRoleWithoutAMemoriesBlockMayOpen() {
        assertTrue(UserRolePermissions(name = "USER").canOpenMemories())
    }

    @Test
    fun aMissingReadKeyMayOpen() {
        assertTrue(memoriesRole("USE" to true).canOpenMemories())
    }

    @Test
    fun useAndReadTogetherMayOpen() {
        assertTrue(memoriesRole("USE" to true, "READ" to true).canOpenMemories())
    }

    @Test
    fun readDeniedMayNotOpen() {
        assertFalse(memoriesRole("USE" to true, "READ" to false).canOpenMemories())
    }

    @Test
    fun useDeniedMayNotOpenEvenWithRead() {
        assertFalse(memoriesRole("USE" to false, "READ" to true).canOpenMemories())
    }

    @Test
    fun theWriteBitsDoNotMatter() {
        assertTrue(memoriesRole("USE" to true, "READ" to true, "CREATE" to false, "UPDATE" to false).canOpenMemories())
    }
}
