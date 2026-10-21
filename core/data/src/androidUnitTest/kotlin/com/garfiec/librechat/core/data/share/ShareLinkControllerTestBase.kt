package com.garfiec.librechat.core.data.share

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.PermissionsRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.ShareChangeSignal
import com.garfiec.librechat.core.data.repository.ShareRepositoryImpl
import com.garfiec.librechat.core.model.SharedLink
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.response.ShareLinkLookup
import com.garfiec.librechat.core.model.share.ShareSheetState
import com.garfiec.librechat.core.network.api.ShareApi
import com.garfiec.librechat.core.network.client.ServerUrlProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope

/** The share sheet controller over the real repository and a mocked API; see [ShareLinkControllerTest]. */
abstract class ShareLinkControllerTestBase {

    protected val shareApi = mockk<ShareApi>()
    protected val config = MutableStateFlow<StartupConfig?>(
        StartupConfig(
            sharedLinksEnabled = true,
            publicSharedLinksEnabled = true,
            sharedLinksSnapshotFilesEnabled = true,
        ),
    )
    protected val role = MutableStateFlow<UserRolePermissions?>(null)
    protected val version = MutableStateFlow<String?>("0.8.8")
    protected val configRepository = mockk<ConfigRepository> {
        every { startupConfig } returns config
        every { detectedBackendVersion } returns version
    }
    protected val roleRepository = mockk<RoleRepository> {
        every { userPermissions } returns role
    }
    protected val changes = ShareChangeSignal()

    /** No SHARE bit on any link unless a test grants one. */
    protected val permissionsRepository = mockk<PermissionsRepository> {
        coEvery { getEffectivePermissions(any(), any()) } returns Result.Success(0)
        coEvery { getResourceRoles(any()) } returns Result.Success(emptyList())
    }
    protected val repository = ShareRepositoryImpl(
        shareApi = shareApi,
        serverUrlProvider = object : ServerUrlProvider {
            override fun getBaseUrl() = "https://chat.example.com"
        },
        configRepository = configRepository,
        shareChanges = changes,
    )

    protected val none = ShareLinkLookup(success = false)
    protected fun live(shareId: String = "s1", snapshotFiles: Boolean? = null) =
        ShareLinkLookup(id = "acl-$shareId", success = true, shareId = shareId, snapshotFiles = snapshotFiles)

    protected fun link(shareId: String) = SharedLink(conversationId = "convo", shareId = shareId)

    protected fun TestScope.controller() =
        ShareLinkController(repository, configRepository, roleRepository, permissionsRepository, backgroundScope)

    protected suspend fun ShareLinkController.await(predicate: (ShareSheetState) -> Boolean): ShareSheetState =
        state.first(predicate)

    protected suspend fun ShareLinkController.awaitLinked(): ShareSheetState.Linked =
        await { it is ShareSheetState.Linked && it.busy == null } as ShareSheetState.Linked

    protected suspend fun ShareLinkController.awaitNoLink(): ShareSheetState.NoLink =
        await { it is ShareSheetState.NoLink && it.busy == null } as ShareSheetState.NoLink

    /**
     * Runs everything due now. advanceUntilIdle stops once only background work is left, which
     * would skip the controller's own state collector.
     */
    protected fun TestScope.settle() = testScheduler.runCurrent()

    protected fun denyCreate() {
        role.value = UserRolePermissions(
            name = "USER",
            permissions = mapOf("SHARED_LINKS" to mapOf("CREATE" to false)),
        )
    }

    protected fun allowSharePublic(sharePublic: Boolean = true) {
        role.value = UserRolePermissions(
            name = "USER",
            permissions = mapOf("SHARED_LINKS" to mapOf("CREATE" to true, "SHARE_PUBLIC" to sharePublic)),
        )
    }
}
