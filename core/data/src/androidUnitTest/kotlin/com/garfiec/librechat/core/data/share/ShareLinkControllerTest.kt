package com.garfiec.librechat.core.data.share

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.PermissionsRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.ShareChangeSignal
import com.garfiec.librechat.core.data.repository.ShareRepository
import com.garfiec.librechat.core.data.repository.ShareRepositoryImpl
import com.garfiec.librechat.core.model.PermissionBits
import com.garfiec.librechat.core.model.ResourcePermissions
import com.garfiec.librechat.core.model.SharedLink
import com.garfiec.librechat.core.model.acl.AclState
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.request.UpdateShareRequest
import com.garfiec.librechat.core.model.response.ShareLinkLookup
import com.garfiec.librechat.core.model.share.FilesOption
import com.garfiec.librechat.core.model.share.ShareAudience
import com.garfiec.librechat.core.model.share.ShareSheetError
import com.garfiec.librechat.core.model.share.ShareSheetState
import com.garfiec.librechat.core.network.api.ShareApi
import com.garfiec.librechat.core.network.client.ServerUrlProvider
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

/**
 * The share sheet's controller, over the real repository and a mocked API, so error bodies travel
 * the same path they do in the app (HTTP exception → safeApiCall → share failure → sheet error).
 */
class ShareLinkControllerTest {

    private val shareApi = mockk<ShareApi>()
    private val config = MutableStateFlow<StartupConfig?>(
        StartupConfig(
            sharedLinksEnabled = true,
            publicSharedLinksEnabled = true,
            sharedLinksSnapshotFilesEnabled = true,
        ),
    )
    private val role = MutableStateFlow<UserRolePermissions?>(null)
    private val version = MutableStateFlow<String?>("0.8.8")
    private val configRepository = mockk<ConfigRepository> {
        every { startupConfig } returns config
        every { detectedBackendVersion } returns version
    }
    private val roleRepository = mockk<RoleRepository> {
        every { userPermissions } returns role
    }
    private val changes = ShareChangeSignal()

    /** No SHARE bit on any link unless a test grants one. */
    private val permissionsRepository = mockk<PermissionsRepository> {
        coEvery { getEffectivePermissions(any(), any()) } returns Result.Success(0)
        coEvery { getResourceRoles(any()) } returns Result.Success(emptyList())
    }
    private val repository = ShareRepositoryImpl(
        shareApi = shareApi,
        serverUrlProvider = object : ServerUrlProvider {
            override fun getBaseUrl() = "https://chat.example.com"
        },
        configRepository = configRepository,
        shareChanges = changes,
    )

    private val none = ShareLinkLookup(success = false)
    private fun live(shareId: String = "s1", snapshotFiles: Boolean? = null) =
        ShareLinkLookup(id = "acl-$shareId", success = true, shareId = shareId, snapshotFiles = snapshotFiles)

    private fun link(shareId: String) = SharedLink(conversationId = "convo", shareId = shareId)

    private fun TestScope.controller() =
        ShareLinkController(repository, configRepository, roleRepository, permissionsRepository, backgroundScope)

    private suspend fun ShareLinkController.await(predicate: (ShareSheetState) -> Boolean): ShareSheetState =
        state.first(predicate)

    private suspend fun ShareLinkController.awaitLinked(): ShareSheetState.Linked =
        await { it is ShareSheetState.Linked && it.busy == null } as ShareSheetState.Linked

    private suspend fun ShareLinkController.awaitNoLink(): ShareSheetState.NoLink =
        await { it is ShareSheetState.NoLink && it.busy == null } as ShareSheetState.NoLink

    /**
     * Runs everything due now. advanceUntilIdle stops once only background work is left, which
     * would skip the controller's own state collector.
     */
    private fun TestScope.settle() = testScheduler.runCurrent()

    private fun denyCreate() {
        role.value = UserRolePermissions(
            name = "USER",
            permissions = mapOf("SHARED_LINKS" to mapOf("CREATE" to false)),
        )
    }

    // ── open and gates ─────────────────────────────────────────────────────

    @Test
    fun `opening never creates, and a chat without a link offers Create with files on`() = runTest {
        coEvery { shareApi.getShareLink("convo") } returns none
        val controller = controller()

        controller.open("convo")
        val state = controller.awaitNoLink()

        assertThat(state.canCreate).isTrue()
        assertThat(state.filesOption).isEqualTo(FilesOption(includeFiles = true))
        assertThat(state.audience).isEqualTo(ShareAudience.ANYONE)
        coVerify(exactly = 0) { shareApi.createShareLink(any(), any(), any()) }
    }

    @Test
    fun `the files switch hides when the server doesn't snapshot files`() = runTest {
        config.value = StartupConfig(sharedLinksEnabled = true, sharedLinksSnapshotFilesEnabled = null)
        coEvery { shareApi.getShareLink("convo") } returns none
        val controller = controller()

        controller.open("convo")
        val state = controller.awaitNoLink()

        assertThat(state.filesOption).isNull()
        assertThat(state.audience).isEqualTo(ShareAudience.SIGNED_IN)
    }

    @Test
    fun `without CREATE the sheet can't create, and Create does nothing`() = runTest {
        denyCreate()
        coEvery { shareApi.getShareLink("convo") } returns none
        val controller = controller()

        controller.open("convo")
        assertThat(controller.awaitNoLink().canCreate).isFalse()
        controller.create()
        settle()

        coVerify(exactly = 0) { shareApi.createShareLink(any(), any(), any()) }
    }

    @Test
    fun `shared links off on the server blocks Create and Update but not Delete`() = runTest {
        config.value = StartupConfig(sharedLinksEnabled = false)
        coEvery { shareApi.getShareLink("convo") } returns live() andThen none
        coEvery { shareApi.deleteShareLink("s1") } just runs
        val controller = controller()

        controller.open("convo")
        val state = controller.awaitLinked()
        controller.update()
        settle()

        assertThat(state.canUpdate).isFalse()
        coVerify(exactly = 0) { shareApi.updateShareLink(any(), any()) }
        controller.delete()
        controller.await { it is ShareSheetState.NoLink }
        coVerify(exactly = 1) { shareApi.deleteShareLink("s1") }
    }

    @Test
    fun `a live link opens with its URL, its ACL id and the update copy for the server version`() = runTest {
        coEvery { shareApi.getShareLink("convo") } returns live("s1", snapshotFiles = false)
        val controller = controller()

        controller.open("convo")
        val state = controller.awaitLinked()

        assertThat(state.url).isEqualTo("https://chat.example.com/share/s1")
        assertThat(state.canUpdate).isTrue()
        assertThat(state.updateKeepsUrl).isTrue()
        assertThat(state.reduced).isFalse()
        assertThat(state.filesOption).isEqualTo(FilesOption(includeFiles = false))
        assertThat(controller.linkResourceId).isEqualTo("acl-s1")
    }

    @Test
    fun `an unresolved server version warns that Update issues a new URL`() = runTest {
        version.value = null
        coEvery { shareApi.getShareLink("convo") } returns live()
        val controller = controller()

        controller.open("convo")

        assertThat(controller.awaitLinked().updateKeepsUrl).isFalse()
    }

    @Test
    fun `a failed lookup offers Retry, which looks again`() = runTest {
        coEvery { shareApi.getShareLink("convo") } throws IOException("offline") andThen live()
        val controller = controller()

        controller.open("convo")
        val failed = controller.await { it is ShareSheetState.Failed } as ShareSheetState.Failed
        controller.retry()

        assertThat(failed.error).isEqualTo(ShareSheetError.Network)
        assertThat(controller.awaitLinked().shareId).isEqualTo("s1")
    }

    // ── create ─────────────────────────────────────────────────────────────

    @Test
    fun `Create sends the switch and shows the link the server now holds`() = runTest {
        coEvery { shareApi.getShareLink("convo") } returns none andThen live("new", snapshotFiles = false)
        coEvery { shareApi.createShareLink("convo", null, false) } returns link("new")
        val controller = controller()

        controller.open("convo")
        controller.awaitNoLink()
        controller.setIncludeFiles(false)
        controller.create()
        val state = controller.awaitLinked()

        assertThat(state.shareId).isEqualTo("new")
        assertThat(state.filesOption).isEqualTo(FilesOption(includeFiles = false))
        assertThat(changes.revision.value).isEqualTo(1)
    }

    @Test
    fun `Create sends the file choice even when the switch is hidden`() = runTest {
        config.value = StartupConfig(sharedLinksEnabled = true)
        coEvery { shareApi.getShareLink("convo") } returns none andThen live("new")
        coEvery { shareApi.createShareLink("convo", null, true) } returns link("new")
        val controller = controller()

        controller.open("convo")
        controller.awaitNoLink()
        controller.create()
        controller.awaitLinked()

        coVerify(exactly = 1) { shareApi.createShareLink("convo", null, true) }
    }

    @Test
    fun `a link made elsewhere since the lookup is adopted, not an error`() = runTest {
        coEvery { shareApi.getShareLink("convo") } returns none andThen live("other")
        coEvery { shareApi.createShareLink("convo", null, true) } throws
            ApiException(409, "Share already exists", body = """{"message":"x","code":"SHARE_EXISTS"}""")
        val controller = controller()

        controller.open("convo")
        controller.awaitNoLink()
        controller.create()

        assertThat(controller.awaitLinked().shareId).isEqualTo("other")
    }

    // ── saved target ───────────────────────────────────────────────────────

    @Test
    fun `from the chat, Create waits for a saved branch tail and retries once`() = runTest {
        coEvery { shareApi.getShareLink("convo") } returns none andThen live("new")
        coEvery { shareApi.createShareLink("convo", "m2", true) } returns link("new")
        val answers = ArrayDeque(listOf(ShareTarget.NotSaved, ShareTarget.Saved("m2")))
        val controller = controller()

        controller.open("convo", target = { answers.removeFirst() })
        controller.awaitNoLink()
        controller.create()

        assertThat(controller.awaitLinked().shareId).isEqualTo("new")
        coVerify(exactly = 1) { shareApi.createShareLink(any(), any(), any()) }
    }

    @Test
    fun `a server-side TARGET_MESSAGE_NOT_FOUND re-resolves the target and retries once`() = runTest {
        coEvery { shareApi.getShareLink("convo") } returns none andThen live("new")
        coEvery { shareApi.createShareLink("convo", "m1", true) } throws targetRejected("TARGET_MESSAGE_NOT_FOUND")
        coEvery { shareApi.createShareLink("convo", "m2", true) } returns link("new")
        val answers = ArrayDeque(listOf(ShareTarget.Saved("m1"), ShareTarget.Saved("m2")))
        val controller = controller()

        controller.open("convo", target = { answers.removeFirst() })
        controller.awaitNoLink()
        controller.create()

        assertThat(controller.awaitLinked().shareId).isEqualTo("new")
    }

    @Test
    fun `a target still unsaved after the retry says so`() = runTest {
        coEvery { shareApi.getShareLink("convo") } returns none
        coEvery { shareApi.createShareLink(any(), any(), any()) } throws targetRejected("TARGET_MESSAGE_NOT_FOUND")
        val controller = controller()

        controller.open("convo", target = { ShareTarget.Saved("m1") })
        controller.awaitNoLink()
        controller.create()
        val state = controller.await { it is ShareSheetState.NoLink && it.error != null } as ShareSheetState.NoLink

        assertThat(state.error).isEqualTo(ShareSheetError.TargetNotSaved)
        coVerify(exactly = 2) { shareApi.createShareLink(any(), any(), any()) }
    }

    @Test
    fun `a chat with nothing saved says so without calling the server twice`() = runTest {
        coEvery { shareApi.getShareLink("convo") } returns none
        val controller = controller()

        controller.open("convo", target = { ShareTarget.NoMessages })
        controller.awaitNoLink()
        controller.create()
        val state = controller.await { it is ShareSheetState.NoLink && it.error != null } as ShareSheetState.NoLink

        assertThat(state.error).isEqualTo(ShareSheetError.NoMessages)
        coVerify(exactly = 0) { shareApi.createShareLink(any(), any(), any()) }
    }

    @Test
    fun `without a resolver a target rejection is not retried`() = runTest {
        coEvery { shareApi.getShareLink("convo") } returns none
        coEvery { shareApi.createShareLink(any(), any(), any()) } throws targetRejected("NO_MESSAGES")
        val controller = controller()

        controller.open("convo")
        controller.awaitNoLink()
        controller.create()
        val state = controller.await { it is ShareSheetState.NoLink && it.error != null } as ShareSheetState.NoLink

        assertThat(state.error).isEqualTo(ShareSheetError.NoMessages)
        coVerify(exactly = 1) { shareApi.createShareLink("convo", null, true) }
    }

    // ── update ─────────────────────────────────────────────────────────────

    @Test
    fun `Update sends the link's stored file choice, never an empty body`() = runTest {
        coEvery { shareApi.getShareLink("convo") } returns live("s1", snapshotFiles = false)
        coEvery { shareApi.updateShareLink("s1", any()) } returns link("s1")
        val controller = controller()

        controller.open("convo")
        controller.awaitLinked()
        controller.update()
        controller.await { it is ShareSheetState.Linked && it.busy == null && changes.revision.value == 1L }

        coVerify(exactly = 1) { shareApi.updateShareLink("s1", UpdateShareRequest(null, false)) }
    }

    @Test
    fun `a legacy link with no stored choice keeps files on when updated`() = runTest {
        config.value = StartupConfig(sharedLinksEnabled = true)
        coEvery { shareApi.getShareLink("convo") } returns live("s1", snapshotFiles = null)
        coEvery { shareApi.updateShareLink("s1", any()) } returns link("s1")
        val controller = controller()

        controller.open("convo")
        controller.awaitLinked()
        controller.update()
        controller.await { it is ShareSheetState.Linked && it.busy == null && changes.revision.value == 1L }

        coVerify(exactly = 1) { shareApi.updateShareLink("s1", UpdateShareRequest(null, true)) }
    }

    @Test
    fun `Update sends the toggled choice and the chat's branch tail`() = runTest {
        coEvery { shareApi.getShareLink("convo") } returns live("s1", snapshotFiles = true)
        coEvery { shareApi.updateShareLink("s1", any()) } returns link("s1")
        val controller = controller()

        controller.open("convo", target = { ShareTarget.Saved("tail") })
        controller.awaitLinked()
        controller.setIncludeFiles(false)
        controller.update()
        controller.await { it is ShareSheetState.Linked && it.busy == null && changes.revision.value == 1L }

        coVerify(exactly = 1) { shareApi.updateShareLink("s1", UpdateShareRequest("tail", false)) }
    }

    @Test
    fun `updating a link that is gone treats it as removed`() = runTest {
        coEvery { shareApi.getShareLink("convo") } returns live("s1")
        coEvery { shareApi.updateShareLink("s1", any()) } throws ApiException(404, "Not Found")
        val controller = controller()

        controller.open("convo")
        controller.awaitLinked()
        controller.update()
        val state = controller.await { it is ShareSheetState.NoLink } as ShareSheetState.NoLink

        assertThat(state.error).isEqualTo(ShareSheetError.NotFound)
        assertThat(changes.revision.value).isEqualTo(1)
    }

    @Test
    fun `after a card's link turns out gone, a new link opens in full`() = runTest {
        coEvery { shareApi.getShareLink("convo") } returns live("s1") andThen live("s2")
        coEvery { shareApi.updateShareLink("s1", any()) } throws ApiException(404, "Not Found")
        coEvery { shareApi.createShareLink("convo", null, true) } returns link("s2")
        val controller = controller()

        controller.open("convo", focusShareId = "s1")
        controller.awaitLinked()
        controller.update()
        controller.await { it is ShareSheetState.NoLink }
        controller.create()
        val state = controller.awaitLinked()

        assertThat(state.shareId).isEqualTo("s2")
        assertThat(state.reduced).isFalse()
    }

    @Test
    fun `a forbidden Update keeps the link and says why`() = runTest {
        coEvery { shareApi.getShareLink("convo") } returns live("s1")
        coEvery { shareApi.updateShareLink("s1", any()) } throws ApiException(403, "Forbidden")
        val controller = controller()

        controller.open("convo")
        controller.awaitLinked()
        controller.update()
        val state = controller.await { it is ShareSheetState.Linked && it.error != null } as ShareSheetState.Linked

        assertThat(state.error).isEqualTo(ShareSheetError.Forbidden)
        assertThat(state.shareId).isEqualTo("s1")
    }

    @Test
    fun `a content filter refusal carries the server's sentence`() = runTest {
        coEvery { shareApi.getShareLink("convo") } returns live("s1")
        val message = "Submitted content contains a phone number. Remove it and try again."
        coEvery { shareApi.updateShareLink("s1", any()) } throws ApiException(
            statusCode = 400,
            message = message,
            body = """{"error":"content_filter_block","message":"$message","source":"message","field":"text"}""",
            serverAuthored = true,
        )
        val controller = controller()

        controller.open("convo")
        controller.awaitLinked()
        controller.update()
        val state = controller.await { it is ShareSheetState.Linked && it.error != null } as ShareSheetState.Linked

        assertThat(state.error).isEqualTo(ShareSheetError.ContentBlocked(message))
    }

    @Test
    fun `too many requests reads as rate limited`() = runTest {
        coEvery { shareApi.getShareLink("convo") } returns live("s1")
        coEvery { shareApi.updateShareLink("s1", any()) } throws ApiException(429, "Too many requests")
        val controller = controller()

        controller.open("convo")
        controller.awaitLinked()
        controller.update()
        val state = controller.await { it is ShareSheetState.Linked && it.error != null } as ShareSheetState.Linked

        assertThat(state.error).isEqualTo(ShareSheetError.RateLimited)
    }

    // ── delete ─────────────────────────────────────────────────────────────

    @Test
    fun `Delete of a link already gone succeeds and leaves Create`() = runTest {
        coEvery { shareApi.getShareLink("convo") } returns live("s1") andThen none
        coEvery { shareApi.deleteShareLink("s1") } throws ApiException(404, "Share not found")
        val controller = controller()

        controller.open("convo")
        controller.awaitLinked()
        controller.delete()
        val state = controller.awaitNoLink()

        assertThat(state.error).isNull()
        assertThat(changes.revision.value).isEqualTo(1)
    }

    // ── reduced mode ───────────────────────────────────────────────────────

    @Test
    fun `a card for a link that isn't the newest opens reduced`() = runTest {
        coEvery { shareApi.getShareLink("convo") } returns live("newest", snapshotFiles = false)
        val controller = controller()

        controller.open("convo", focusShareId = "older")
        val state = controller.awaitLinked()

        assertThat(state.reduced).isTrue()
        assertThat(state.shareId).isEqualTo("older")
        assertThat(state.url).isEqualTo("https://chat.example.com/share/older")
        assertThat(state.canUpdate).isFalse()
        assertThat(state.filesOption).isNull()
        assertThat(controller.linkResourceId).isNull()
        controller.update()
        settle()
        coVerify(exactly = 0) { shareApi.updateShareLink(any(), any()) }
    }

    @Test
    fun `a card for the newest link opens in full`() = runTest {
        coEvery { shareApi.getShareLink("convo") } returns live("s1", snapshotFiles = true)
        val controller = controller()

        controller.open("convo", focusShareId = "s1")
        val state = controller.awaitLinked()

        assertThat(state.reduced).isFalse()
        assertThat(state.canUpdate).isTrue()
    }

    @Test
    fun `deleting a reduced link closes the sheet`() = runTest {
        coEvery { shareApi.getShareLink("convo") } returns live("newest")
        coEvery { shareApi.deleteShareLink("older") } just runs
        val controller = controller()

        controller.open("convo", focusShareId = "older")
        controller.awaitLinked()
        controller.delete()

        controller.await { it is ShareSheetState.Closed }
        coVerify(exactly = 1) { shareApi.deleteShareLink("older") }
    }

    // ── stale results ──────────────────────────────────────────────────────

    // Over a mocked repository: nothing leaves the test dispatcher, so "dropped" is not "not yet".
    private fun TestScope.controllerOver(shares: ShareRepository) =
        ShareLinkController(shares, configRepository, roleRepository, permissionsRepository, backgroundScope)

    private fun slowRepository(): ShareRepository = mockk {
        every { shareUrl(any()) } answers { "https://chat.example.com/share/${firstArg<String>()}" }
    }

    @Test
    fun `a lookup that lands after another open is dropped`() = runTest {
        val slow = CompletableDeferred<Result<ShareLinkLookup>>()
        val shares = slowRepository()
        coEvery { shares.lookupShareLink("first") } coAnswers { slow.await() }
        coEvery { shares.lookupShareLink("second") } returns Result.Success(live("s2"))
        val controller = controllerOver(shares)
        controller.open("first")
        settle()
        controller.open("second")
        settle()
        slow.complete(Result.Success(live("s1")))
        settle()

        assertThat((controller.state.value as ShareSheetState.Linked).shareId).isEqualTo("s2")
    }

    @Test
    fun `a lookup that lands after close is dropped`() = runTest {
        val slow = CompletableDeferred<Result<ShareLinkLookup>>()
        val shares = slowRepository()
        coEvery { shares.lookupShareLink("convo") } coAnswers { slow.await() }
        val controller = controllerOver(shares)

        controller.open("convo")
        settle()
        controller.close()
        slow.complete(Result.Success(live("s1")))
        settle()

        assertThat(controller.state.value).isEqualTo(ShareSheetState.Closed)
    }

    @Test
    fun `a create that lands after the sheet moved on is dropped`() = runTest {
        val slow = CompletableDeferred<Result<String>>()
        val shares = slowRepository()
        coEvery { shares.lookupShareLink("first") } returns Result.Success(none)
        coEvery { shares.lookupShareLink("second") } returns Result.Success(none)
        coEvery { shares.createShareLink("first", null, true) } coAnswers { slow.await() }
        val controller = controllerOver(shares)

        controller.open("first")
        settle()
        controller.create()
        settle()
        controller.open("second")
        settle()
        slow.complete(Result.Success("s1"))
        settle()

        val state = controller.state.value as ShareSheetState.NoLink
        assertThat(state.busy).isNull()
        coVerify(exactly = 1) { shares.lookupShareLink("first") } // the open's, no settle after the dropped create
    }

    // ── manage access ──────────────────────────────────────────────────────

    private fun allowShare(share: Boolean = true) {
        role.value = UserRolePermissions(
            name = "USER",
            permissions = mapOf("SHARED_LINKS" to mapOf("CREATE" to true, "SHARE" to share)),
        )
    }

    private fun grantShareBit(resourceId: String) {
        coEvery { permissionsRepository.getEffectivePermissions("sharedLink", resourceId) } returns
            Result.Success(PermissionBits.VIEW or PermissionBits.SHARE)
    }

    @Test
    fun `manage access is offered with the role's SHARE and the link's SHARE bit`() = runTest {
        allowShare()
        grantShareBit("acl-s1")
        coEvery { shareApi.getShareLink("convo") } returns live("s1")
        val controller = controller()

        controller.open("convo")

        controller.await { it is ShareSheetState.Linked && it.canManageAccess }
        coVerify { permissionsRepository.getEffectivePermissions("sharedLink", "acl-s1") }
    }

    @Test
    fun `manage access stays hidden without the link's SHARE bit`() = runTest {
        allowShare()
        coEvery { permissionsRepository.getEffectivePermissions("sharedLink", "acl-s1") } returns
            Result.Success(PermissionBits.VIEW)
        coEvery { shareApi.getShareLink("convo") } returns live("s1")
        val controller = controller()

        controller.open("convo")
        controller.awaitLinked()
        settle()

        assertThat((controller.state.value as ShareSheetState.Linked).canManageAccess).isFalse()
    }

    @Test
    fun `manage access fails closed on an unknown role and never asks for the bit`() = runTest {
        grantShareBit("acl-s1")
        coEvery { shareApi.getShareLink("convo") } returns live("s1")
        val controller = controller()

        controller.open("convo")
        controller.awaitLinked()
        settle()

        assertThat((controller.state.value as ShareSheetState.Linked).canManageAccess).isFalse()
        coVerify(exactly = 0) { permissionsRepository.getEffectivePermissions(any(), any()) }
    }

    @Test
    fun `manage access stays hidden when the role denies SHARE`() = runTest {
        allowShare(share = false)
        grantShareBit("acl-s1")
        coEvery { shareApi.getShareLink("convo") } returns live("s1")
        val controller = controller()

        controller.open("convo")
        controller.awaitLinked()
        settle()

        assertThat((controller.state.value as ShareSheetState.Linked).canManageAccess).isFalse()
    }

    @Test
    fun `manage access stays hidden when the bit can't be read`() = runTest {
        allowShare()
        coEvery { permissionsRepository.getEffectivePermissions("sharedLink", "acl-s1") } returns
            Result.Error(IOException("offline"))
        coEvery { shareApi.getShareLink("convo") } returns live("s1")
        val controller = controller()

        controller.open("convo")
        controller.awaitLinked()
        settle()

        assertThat((controller.state.value as ShareSheetState.Linked).canManageAccess).isFalse()
    }

    @Test
    fun `a reduced link never offers manage access`() = runTest {
        allowShare()
        grantShareBit("acl-newest")
        coEvery { shareApi.getShareLink("convo") } returns live("newest")
        val controller = controller()

        controller.open("convo", focusShareId = "older")
        controller.awaitLinked()
        settle()

        val state = controller.state.value as ShareSheetState.Linked
        assertThat(state.reduced).isTrue()
        assertThat(state.canManageAccess).isFalse()
        coVerify(exactly = 0) { permissionsRepository.getEffectivePermissions(any(), any()) }
    }

    @Test
    fun `openAccess reads the link's grants by its lookup id, and closing the sheet closes it`() = runTest {
        allowShare()
        grantShareBit("acl-s1")
        coEvery { permissionsRepository.getResourcePermissions("sharedLink", "acl-s1") } returns
            Result.Success(ResourcePermissions())
        coEvery { shareApi.getShareLink("convo") } returns live("s1")
        val controller = controller()
        controller.open("convo")
        controller.await { it is ShareSheetState.Linked && it.canManageAccess }

        controller.openAccess()
        controller.access.state.first { it is AclState.Ready }
        controller.close()

        assertThat(controller.access.state.first { it == AclState.Closed }).isEqualTo(AclState.Closed)
        coVerify(exactly = 1) { permissionsRepository.getResourcePermissions("sharedLink", "acl-s1") }
    }

    @Test
    fun `openAccess does nothing while manage access isn't offered`() = runTest {
        coEvery { shareApi.getShareLink("convo") } returns live("s1")
        val controller = controller()
        controller.open("convo")
        controller.awaitLinked()
        settle()

        controller.openAccess()
        settle()

        assertThat(controller.access.state.value).isEqualTo(AclState.Closed)
        coVerify(exactly = 0) { permissionsRepository.getResourcePermissions(any(), any()) }
    }

    private fun targetRejected(code: String) = ApiException(
        statusCode = 400,
        message = "rejected",
        body = """{"message":"rejected","code":"$code"}""",
        serverAuthored = true,
    )
}
