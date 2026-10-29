package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.data.util.PermissionGate
import com.garfiec.librechat.core.data.util.RefreshTagsSessionTask
import com.garfiec.librechat.core.model.config.InterfaceConfig
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BookmarksGateTest {

    private fun role(use: Boolean?) = UserRolePermissions(
        name = "USER",
        permissions = if (use == null) emptyMap() else mapOf("BOOKMARKS" to mapOf("USE" to use)),
    )

    private fun iface(bookmarks: Boolean) = InterfaceConfig(bookmarks = bookmarks)

    @Test
    fun `truth table - role USE AND interface bookmarks, both failing open`() {
        // Role unknown (null) or the key missing is permissive; an absent interface block is enabled.
        assertThat(bookmarksEnabled(null, null)).isTrue()
        assertThat(bookmarksEnabled(role(null), null)).isTrue()
        assertThat(bookmarksEnabled(role(true), null)).isTrue()
        assertThat(bookmarksEnabled(role(true), iface(true))).isTrue()
        assertThat(bookmarksEnabled(null, iface(true))).isTrue()

        assertThat(bookmarksEnabled(role(false), null)).isFalse()
        assertThat(bookmarksEnabled(role(false), iface(true))).isFalse()
        assertThat(bookmarksEnabled(role(true), iface(false))).isFalse()
        assertThat(bookmarksEnabled(null, iface(false))).isFalse()
        assertThat(bookmarksEnabled(role(false), iface(false))).isFalse()
    }

    private val userPermissions = MutableStateFlow<UserRolePermissions?>(null)
    private val startupConfig = MutableStateFlow<StartupConfig?>(null)
    private val permissionGate = mockk<PermissionGate>()
    private val gate = BookmarksGate(
        roleRepository = mockk { every { userPermissions } returns this@BookmarksGateTest.userPermissions },
        configRepository = mockk { every { startupConfig } returns this@BookmarksGateTest.startupConfig },
        permissionGate = permissionGate,
    )

    @Test
    fun `enabled follows the live role and config`() = runTest {
        assertThat(gate.enabled.first()).isTrue()

        userPermissions.value = role(false)
        assertThat(gate.enabled.first()).isFalse()

        userPermissions.value = role(true)
        startupConfig.value = StartupConfig(interfaceConfig = iface(false))
        assertThat(gate.enabled.first()).isFalse()
    }

    @Test
    fun `the session task waits for the role and skips when bookmarks are off`() = runTest {
        val tags = mockk<TagRepository>(relaxed = true)
        val task = RefreshTagsSessionTask(tags, gate)

        coEvery { permissionGate.awaitRole() } returns role(false)
        task.run()
        coVerify(exactly = 0) { tags.refreshTags() }

        coEvery { permissionGate.awaitRole() } returns role(true)
        startupConfig.value = StartupConfig(interfaceConfig = iface(false))
        task.run()
        coVerify(exactly = 0) { tags.refreshTags() }
    }

    @Test
    fun `the session task refreshes when permitted, and when the role never loaded`() = runTest {
        val tags = mockk<TagRepository>(relaxed = true)
        val task = RefreshTagsSessionTask(tags, gate)

        coEvery { permissionGate.awaitRole() } returns role(true)
        task.run()
        coVerify(exactly = 1) { tags.refreshTags() }

        // awaitRole() answers null on timeout or a failed fetch: permissive, like every role gate.
        coEvery { permissionGate.awaitRole() } returns null
        task.run()
        coVerify(exactly = 2) { tags.refreshTags() }
    }
}
