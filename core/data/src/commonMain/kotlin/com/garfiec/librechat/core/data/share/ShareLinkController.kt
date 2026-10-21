package com.garfiec.librechat.core.data.share

import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.suspendRunCatching
import com.garfiec.librechat.core.data.acl.AclController
import com.garfiec.librechat.core.data.acl.AclPolicy
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.PermissionsRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.ShareRepository
import com.garfiec.librechat.core.model.PermissionBits
import com.garfiec.librechat.core.model.ResourceType
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.permissions.canCreateSharedLinks
import com.garfiec.librechat.core.model.permissions.hasAccessStrictOrDenied
import com.garfiec.librechat.core.model.response.ShareLinkLookup
import com.garfiec.librechat.core.model.share.FilesOption
import com.garfiec.librechat.core.model.share.ShareAudience
import com.garfiec.librechat.core.model.share.ShareSheetBusy
import com.garfiec.librechat.core.model.share.ShareSheetError
import com.garfiec.librechat.core.model.share.ShareSheetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Makes a [ShareLinkController] bound to its host's scope (a ViewModel's `viewModelScope`). */
class ShareLinkControllerFactory(
    private val shareRepository: ShareRepository,
    private val configRepository: ConfigRepository,
    private val roleRepository: RoleRepository,
    private val permissionsRepository: PermissionsRepository,
) {
    fun create(scope: CoroutineScope): ShareLinkController =
        ShareLinkController(shareRepository, configRepository, roleRepository, permissionsRepository, scope)
}

/**
 * Drives the share sheet for one conversation at a time: looks the link up on [open] (opening
 * never creates one), then creates, updates or deletes it on request. [state] is what the sheet
 * renders.
 *
 * Gates follow the server: Create and Update need `sharedLinksEnabled` and SHARED_LINKS CREATE
 * (permissive while the role is unknown, as the Share action is); Delete is never gated. The files
 * switch shows only when the server snapshots files (`sharedLinksSnapshotFilesEnabled`).
 *
 * The link's file choice is ALWAYS sent, on Create and on Update: the server reads an absent
 * `snapshotFiles` as "include files", so an update without it would turn files back on for a link
 * that had them off (scripts/mirrors.json `share-files-default-on`).
 *
 * Manage access (the sheet's second page) is offered as on web: the role's SHARED_LINKS SHARE
 * (fail-closed) AND the SHARE bit on this link's ACL (`/effective` on the lookup's `_id`). A
 * reduced link has no `_id`, so it never offers it. [access] drives that page.
 *
 * Results that land after the sheet closed or moved to another conversation are dropped.
 */
class ShareLinkController internal constructor(
    private val shareRepository: ShareRepository,
    private val configRepository: ConfigRepository,
    private val roleRepository: RoleRepository,
    private val permissionsRepository: PermissionsRepository,
    private val scope: CoroutineScope,
) {
    private val phase = MutableStateFlow<Phase>(Phase.Closed)

    /** The SHARE bit on the open link's ACL, once read; null until then. */
    private val linkShareBit = MutableStateFlow<LinkShareBit?>(null)

    /** The Manage access page: who can open the open link. Opened by [openAccess]. */
    val access: AclController = AclController(AclPolicy.SharedLink, permissionsRepository, roleRepository, scope)

    /** Counts opens and closes; an async step writes back only while it still matches. */
    private var generation = 0L
    private var session: Session? = null

    val state: StateFlow<ShareSheetState> = combine(
        phase,
        configRepository.startupConfig,
        roleRepository.userPermissions,
        configRepository.detectedBackendVersion,
        linkShareBit,
    ) { phase, config, role, version, shareBit ->
        render(phase, gates(config, role, version), shareBit)
    }.stateIn(scope, SharingStarted.Eagerly, ShareSheetState.Closed)

    init {
        // Reads the link's SHARE bit whenever a full link is open and the role may share links.
        // collectLatest drops a read the sheet moved on from.
        scope.launch {
            combine(
                phase.map { (it as? Phase.Linked)?.takeUnless { linked -> linked.reduced }?.resourceId },
                roleRepository.userPermissions.map { it.canShareLinks() },
            ) { resourceId, canShare -> resourceId?.takeIf { canShare } }
                .distinctUntilChanged()
                .collectLatest { resourceId ->
                    linkShareBit.value = null
                    if (resourceId == null) return@collectLatest
                    val bits = permissionsRepository.getEffectivePermissions(ResourceType.SHARED_LINK, resourceId)
                    linkShareBit.value = LinkShareBit(
                        resourceId = resourceId,
                        granted = bits is Result.Success && PermissionBits.has(bits.data, PermissionBits.SHARE),
                    )
                }
        }
    }

    /**
     * The open link's ACL resource id (the lookup's `_id`), for Manage access. Null when no full
     * link is open: a reduced link comes from the list, which carries no `_id`.
     */
    val linkResourceId: String?
        get() = (phase.value as? Phase.Linked)?.takeUnless { it.reduced }?.resourceId

    /**
     * Opens the sheet for [conversationId].
     *
     * @param target the open chat's branch-tail resolver; null shares without a target.
     * @param focusShareId the link a Shared chats card was opened on. When it isn't the
     *   conversation's newest live link (an older or targeted link made on the web) the sheet opens
     *   reduced: the list gives neither that link's `_id` nor its stored file choice, and updating
     *   it without the stored choice is exactly how files get turned back on.
     */
    fun open(conversationId: String, target: TargetResolver? = null, focusShareId: String? = null) {
        access.close()
        val next = Session(conversationId, target, focusShareId)
        session = next
        val gen = ++generation
        phase.value = Phase.Loading
        scope.launch { load(gen, next) }
    }

    /** Looks the link up again after a failed lookup. */
    fun retry() {
        val current = session ?: return
        if (phase.value !is Phase.Failed) return
        val gen = ++generation
        phase.value = Phase.Loading
        scope.launch { load(gen, current) }
    }

    fun close() {
        access.close()
        generation++
        session = null
        phase.value = Phase.Closed
    }

    /** Opens the Manage access page on the open link, when the sheet offers it. */
    fun openAccess() {
        val linked = state.value as? ShareSheetState.Linked ?: return
        val resourceId = linkResourceId ?: return
        if (!linked.canManageAccess || linked.busy != null) return
        access.open(resourceId)
    }

    /** Back from the Manage access page to the share page. */
    fun closeAccess() {
        access.close()
    }

    /** Flips the files switch. Nothing is sent until Create or Update. */
    fun setIncludeFiles(include: Boolean) {
        phase.value = when (val current = phase.value) {
            is Phase.NoLink -> if (current.busy == null) current.copy(includeFiles = include) else current
            is Phase.Linked ->
                if (current.busy == null && !current.reduced) current.copy(includeFiles = include) else current
            else -> current
        }
    }

    fun create() {
        val current = phase.value as? Phase.NoLink ?: return
        val active = session ?: return
        if (current.busy != null || !currentGates().canCreate) return
        val gen = generation
        phase.value = current.copy(busy = ShareSheetBusy.CREATING, error = null)
        scope.launch {
            val includeFiles = current.includeFiles
            val outcome = publish(active.target) { targetId ->
                shareRepository.createShareLink(active.conversationId, targetId, includeFiles)
            }
            if (gen != generation) return@launch
            when (outcome) {
                is Published.Ok -> {
                    val fallback = Phase.Linked(outcome.value, resourceId = null, includeFiles = includeFiles)
                    settle(gen, active, fallback)
                }
                is Published.Failed -> phase.value = current.copy(busy = null, error = outcome.error)
            }
        }
    }

    fun update() {
        val current = phase.value as? Phase.Linked ?: return
        val active = session ?: return
        if (current.busy != null || current.reduced || !currentGates().canCreate) return
        val gen = generation
        phase.value = current.copy(busy = ShareSheetBusy.UPDATING, error = null)
        scope.launch {
            val outcome = publish(active.target) { targetId ->
                shareRepository.updateShareLink(current.shareId, targetId, current.includeFiles)
                    .map { it.shareId ?: current.shareId }
            }
            if (gen != generation) return@launch
            when (outcome) {
                is Published.Ok -> {
                    // A pre-rc1 server minted a new id; the sheet follows the link, not the card.
                    val followed = active.copy(focusShareId = null)
                    session = followed
                    settle(gen, followed, current.copy(shareId = outcome.value, busy = null))
                }
                is Published.Failed ->
                    if (outcome.error == ShareSheetError.NotFound) {
                        // The link is gone, so it is treated as removed: what's left is to create
                        // one, and the sheet no longer follows the card it was opened from.
                        session = active.copy(focusShareId = null)
                        access.close()
                        phase.value = Phase.NoLink(error = ShareSheetError.NotFound)
                    } else {
                        phase.value = current.copy(busy = null, error = outcome.error)
                    }
            }
        }
    }

    fun delete() {
        val current = phase.value as? Phase.Linked ?: return
        val active = session ?: return
        if (current.busy != null) return
        access.close()
        val gen = generation
        phase.value = current.copy(busy = ShareSheetBusy.DELETING, error = null)
        scope.launch {
            val result = shareRepository.deleteShareLink(current.shareId)
            if (gen != generation) return@launch
            when (result) {
                is Result.Success ->
                    if (current.reduced) {
                        // The card's link is gone; the conversation's own link is another sheet.
                        close()
                    } else {
                        val cleared = active.copy(focusShareId = null)
                        session = cleared
                        settle(gen, cleared, Phase.NoLink())
                    }
                is Result.Error ->
                    phase.value = current.copy(busy = null, error = result.toShareFailure().toSheetError())
                is Result.Loading -> Unit
            }
        }
    }

    private suspend fun load(gen: Long, active: Session) {
        val next = when (val result = shareRepository.lookupShareLink(active.conversationId)) {
            is Result.Success -> phaseFor(result.data, active.focusShareId)
            is Result.Error -> Phase.Failed(result.toShareFailure().toSheetError())
            is Result.Loading -> return
        }
        if (gen == generation) phase.value = next
    }

    /**
     * After a change, shows what the server now holds: the lookup carries the link's `_id` and
     * stored file choice, which the create / update responses don't reliably. [fallback] stands in
     * when that lookup fails, so a change that succeeded never reads as a failure.
     */
    private suspend fun settle(gen: Long, active: Session, fallback: Phase) {
        val result = shareRepository.lookupShareLink(active.conversationId)
        if (gen != generation) return
        phase.value = (result as? Result.Success)?.let { phaseFor(it.data, active.focusShareId) } ?: fallback
    }

    private fun phaseFor(lookup: ShareLinkLookup, focusShareId: String?): Phase {
        val liveId = lookup.shareId?.takeIf { lookup.success }
        return when {
            focusShareId != null && focusShareId != liveId ->
                Phase.Linked(focusShareId, resourceId = null, includeFiles = true, reduced = true)
            liveId != null -> Phase.Linked(
                shareId = liveId,
                resourceId = lookup.id,
                // A legacy link that stored no choice includes files (the server reads `!== false`).
                includeFiles = lookup.snapshotFiles ?: true,
            )
            else -> Phase.NoLink()
        }
    }

    /**
     * Runs [call] with the resolved target, and once more with a re-resolved one when the target
     * wasn't saved yet or the chat had nothing to share — the web share dialog's single retry.
     * Without a resolver there is nothing to re-resolve, so there is no retry.
     */
    private suspend fun <T> publish(
        target: TargetResolver?,
        call: suspend (String?) -> Result<T>,
    ): Published<T> {
        val first = attempt(target, call)
        if (target == null || first !is Attempt.TargetRejected) return first.toPublished()
        return attempt(target, call).toPublished()
    }

    private suspend fun <T> attempt(target: TargetResolver?, call: suspend (String?) -> Result<T>): Attempt<T> {
        val targetId = if (target == null) {
            null
        } else {
            when (val resolved = suspendRunCatching { target.resolve() }.getOrNull()) {
                is ShareTarget.Saved -> resolved.messageId
                ShareTarget.NotSaved -> return Attempt.TargetRejected(ShareSheetError.TargetNotSaved)
                ShareTarget.NoMessages -> return Attempt.TargetRejected(ShareSheetError.NoMessages)
                null -> return Attempt.Failed(ShareSheetError.Generic)
            }
        }
        return when (val result = call(targetId)) {
            is Result.Success -> Attempt.Ok(result.data)
            is Result.Error -> {
                val failure = result.toShareFailure()
                if (failure.isTargetRejection) {
                    Attempt.TargetRejected(failure.toSheetError())
                } else {
                    Attempt.Failed(failure.toSheetError())
                }
            }
            is Result.Loading -> Attempt.Failed(ShareSheetError.Generic)
        }
    }

    private fun currentGates(): Gates = gates(
        configRepository.startupConfig.value,
        roleRepository.userPermissions.value,
        configRepository.detectedBackendVersion.value,
    )

    private fun render(phase: Phase, gates: Gates, shareBit: LinkShareBit?): ShareSheetState = when (phase) {
        Phase.Closed -> ShareSheetState.Closed
        Phase.Loading -> ShareSheetState.Loading
        is Phase.Failed -> ShareSheetState.Failed(phase.error)
        is Phase.NoLink -> ShareSheetState.NoLink(
            canCreate = gates.canCreate,
            filesOption = if (gates.filesEnabled) FilesOption(phase.includeFiles) else null,
            audience = gates.newLinkAudience,
            busy = phase.busy,
            error = phase.error,
        )
        is Phase.Linked -> ShareSheetState.Linked(
            url = shareRepository.shareUrl(phase.shareId),
            shareId = phase.shareId,
            canUpdate = gates.canCreate && !phase.reduced,
            updateKeepsUrl = gates.updateKeepsUrl,
            filesOption = if (gates.filesEnabled && !phase.reduced) FilesOption(phase.includeFiles) else null,
            audience = gates.publicAudience,
            canManageAccess = !phase.reduced &&
                phase.resourceId != null &&
                gates.canShareLinks &&
                shareBit?.resourceId == phase.resourceId &&
                shareBit.granted,
            // Any live link, reduced included: the owner can always read their own link.
            canPreview = true,
            reduced = phase.reduced,
            busy = phase.busy,
            error = phase.error,
        )
    }

    private data class Session(
        val conversationId: String,
        val target: TargetResolver?,
        val focusShareId: String?,
    )

    private data class Gates(
        val canCreate: Boolean,
        val filesEnabled: Boolean,
        /** Who a link created now can be opened by. */
        val newLinkAudience: ShareAudience,
        /** Who a link's public grant reaches. */
        val publicAudience: ShareAudience,
        val updateKeepsUrl: Boolean,
        val canShareLinks: Boolean,
    )

    private data class LinkShareBit(val resourceId: String, val granted: Boolean)

    /** SHARED_LINKS SHARE, fail-closed as on web (`useHasAccess`) and the server's `checkShareAccess`. */
    private fun UserRolePermissions?.canShareLinks(): Boolean =
        hasAccessStrictOrDenied(PermissionType.SHARED_LINKS, Permission.SHARE)

    private fun gates(config: StartupConfig?, role: UserRolePermissions?, version: String?): Gates {
        val publicAudience =
            if (config?.publicSharedLinksEnabled == true) ShareAudience.ANYONE else ShareAudience.SIGNED_IN
        return Gates(
            canCreate = role.canCreateSharedLinks(config?.sharedLinksEnabled ?: false),
            filesEnabled = config?.sharedLinksSnapshotFilesEnabled == true,
            newLinkAudience = newLinkAudience(publicAudience, role, version),
            publicAudience = publicAudience,
            // Fail-safe false: only a CONFIRMED rc1+ server keeps the shareId across a re-publish, so
            // an unresolved version warns instead of promising it.
            updateKeepsUrl = version != null && BackendVersion.isCompatibleOrNewer(version, V0_8_8_RC1),
            canShareLinks = role.canShareLinks(),
        )
    }

    /**
     * Who a link created now can be opened by. The create route gives the link its public viewer
     * grant only when the role's SHARED_LINKS.SHARE_PUBLIC is strictly true, and without that grant
     * nobody but the owner and the people they add gets in, whatever `publicSharedLinksEnabled`
     * says (scripts/mirrors.json `share-public-grant-rule`). Fail-closed on an unknown role, as
     * the Manage access switch is. The rule holds from 0.8.7-rc1, the first release with per-link
     * ACLs; a server confirmed older than that keeps the flag-only wording.
     */
    private fun newLinkAudience(
        publicAudience: ShareAudience,
        role: UserRolePermissions?,
        version: String?,
    ): ShareAudience {
        val beforeAcl = version != null && !BackendVersion.isCompatibleOrNewer(version, V0_8_7_RC1)
        return when {
            beforeAcl -> publicAudience
            role.hasAccessStrictOrDenied(PermissionType.SHARED_LINKS, Permission.SHARE_PUBLIC) -> publicAudience
            else -> ShareAudience.PEOPLE_ADDED
        }
    }

    private sealed interface Phase {
        data object Closed : Phase
        data object Loading : Phase
        data class Failed(val error: ShareSheetError) : Phase
        data class NoLink(
            val includeFiles: Boolean = FilesOption.NEW_LINK.includeFiles,
            val busy: ShareSheetBusy? = null,
            val error: ShareSheetError? = null,
        ) : Phase
        data class Linked(
            val shareId: String,
            val resourceId: String?,
            val includeFiles: Boolean,
            val reduced: Boolean = false,
            val busy: ShareSheetBusy? = null,
            val error: ShareSheetError? = null,
        ) : Phase
    }

    private sealed interface Attempt<out T> {
        data class Ok<T>(val value: T) : Attempt<T>
        data class TargetRejected(val error: ShareSheetError) : Attempt<Nothing>
        data class Failed(val error: ShareSheetError) : Attempt<Nothing>
    }

    private sealed interface Published<out T> {
        data class Ok<T>(val value: T) : Published<T>
        data class Failed(val error: ShareSheetError) : Published<Nothing>
    }

    private fun <T> Attempt<T>.toPublished(): Published<T> = when (this) {
        is Attempt.Ok -> Published.Ok(value)
        is Attempt.TargetRejected -> Published.Failed(error)
        is Attempt.Failed -> Published.Failed(error)
    }

    private inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> = when (this) {
        is Result.Success -> Result.Success(transform(data))
        is Result.Error -> this
        is Result.Loading -> this
    }
}

/** The first release whose Update keeps the shareId; 0.8.7's update mints a new one. */
private const val V0_8_8_RC1 = "0.8.8-rc1"

/** The first release with per-link ACLs, where the create route grants public access by SHARE_PUBLIC. */
private const val V0_8_7_RC1 = "0.8.7-rc1"
