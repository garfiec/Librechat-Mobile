package com.garfiec.librechat.core.data.acl

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.FailureKind
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.PermissionsRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.model.AccessRole
import com.garfiec.librechat.core.model.AccessRoleIds
import com.garfiec.librechat.core.model.Principal
import com.garfiec.librechat.core.model.ResourcePermissions
import com.garfiec.librechat.core.model.ResourceType
import com.garfiec.librechat.core.model.UpdateResourcePermissionsRequest
import com.garfiec.librechat.core.model.acl.AclEntry
import com.garfiec.librechat.core.model.acl.AclError
import com.garfiec.librechat.core.model.acl.AclSearch
import com.garfiec.librechat.core.model.acl.AclState
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.permissions.hasAccessStrictOrDenied
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What an access editor works on: the resource type the `/api/permissions` routes take, the role
 * permission type that gates public sharing, and the roles with special meaning.
 *
 * @param ownerRoleId the creator's role: its grant is shown locked and never offered.
 * @param defaultRoleId the role a new grant and public access take.
 */
class AclPolicy(
    val resourceType: String,
    val permissionType: PermissionType,
    val ownerRoleId: String?,
    val defaultRoleId: String?,
) {
    companion object {
        /** A shared link: viewers only; the owner can't be changed (upstream rejects it with a 400). */
        val SharedLink = AclPolicy(
            resourceType = ResourceType.SHARED_LINK,
            permissionType = PermissionType.SHARED_LINKS,
            ownerRoleId = AccessRoleIds.SHARED_LINK_OWNER,
            defaultRoleId = AccessRoleIds.SHARED_LINK_VIEWER,
        )
    }
}

/**
 * Drives an access editor for one resource at a time over `/api/permissions`: reads its grants
 * and the roles it offers on [open], searches people to add, and grants, revokes or flips public
 * access on request. [state] is what the `:core:ui` `AclSharingSection` renders.
 *
 * Gates follow the server, fail-closed: public access needs the role's [AclPolicy.permissionType]
 * SHARE_PUBLIC, and people search needs any of PEOPLE_PICKER VIEW_USERS, VIEW_GROUPS or VIEW_ROLES:
 * the server searches only the types the role may see and answers `search-principals` with a 403
 * when it may see none. Who may open the editor at all is the host's call.
 *
 * Writes send `public` only when public access is what changes: the server checks SHARE_PUBLIC
 * whenever `public` is true, so echoing it on a grant would refuse anyone without SHARE_PUBLIC on
 * a link that is already public (the web client's rule too). After a write the grants are read
 * again, since the PUT answers with only what it granted.
 *
 * Results that land after the editor closed or moved to another resource are dropped.
 */
class AclController(
    private val policy: AclPolicy,
    private val permissionsRepository: PermissionsRepository,
    private val roleRepository: RoleRepository,
    private val scope: CoroutineScope,
) {
    private val phase = MutableStateFlow<Phase>(Phase.Closed)
    private val search = MutableStateFlow(AclSearch())

    /** Counts opens and closes; an async step writes back only while it still matches. */
    private var generation = 0L
    private var resourceId: String? = null
    private var searchJob: Job? = null

    val state: StateFlow<AclState> = combine(phase, search, roleRepository.userPermissions) { phase, search, role ->
        render(phase, search, gates(role))
    }.stateIn(scope, SharingStarted.Eagerly, AclState.Closed)

    /** Opens the editor on [resourceId] and reads its grants. */
    fun open(resourceId: String) {
        this.resourceId = resourceId
        val gen = ++generation
        resetSearch()
        phase.value = Phase.Loading
        scope.launch { load(gen, resourceId) }
    }

    /** Reads the grants again after a failed read. */
    fun retry() {
        val id = resourceId ?: return
        if (phase.value !is Phase.Failed) return
        val gen = ++generation
        phase.value = Phase.Loading
        scope.launch { load(gen, id) }
    }

    fun close() {
        generation++
        resourceId = null
        resetSearch()
        phase.value = Phase.Closed
    }

    /** Picks the role the next grant takes. Ignored for a role the resource doesn't offer. */
    fun setGrantRole(accessRoleId: String) {
        phase.update { current ->
            if (current is Phase.Ready && current.roles.any { it.accessRoleId == accessRoleId }) {
                current.copy(grantRoleId = accessRoleId)
            } else {
                current
            }
        }
    }

    /** Turns public access on (with the default role) or off. Needs SHARE_PUBLIC. */
    fun setPublic(enabled: Boolean) {
        val current = phase.value as? Phase.Ready ?: return
        if (current.busy || !currentGates().canSharePublic || enabled == current.isPublic) return
        val body = if (enabled) {
            val roleId = current.publicRoleId ?: current.defaultGrantRole() ?: return
            UpdateResourcePermissionsRequest(public = true, publicAccessRoleId = roleId)
        } else {
            UpdateResourcePermissionsRequest(public = false)
        }
        write(current, body)
    }

    /** Gives [principal] (a search result) the selected role. */
    fun grant(principal: Principal) {
        val current = phase.value as? Phase.Ready ?: return
        if (current.busy) return
        val roleId = current.grantRoleId ?: return
        if (current.principals.any { it.key == principal.key }) return
        write(
            current,
            UpdateResourcePermissionsRequest(updated = listOf(principal.copy(accessRoleId = roleId))),
            onSuccess = ::resetSearch,
        )
    }

    /** Takes [principal]'s access away. The owner's grant is locked. */
    fun revoke(principal: Principal) {
        val current = phase.value as? Phase.Ready ?: return
        if (current.busy || isOwner(principal)) return
        if (current.principals.none { it.key == principal.key }) return
        write(current, UpdateResourcePermissionsRequest(removed = listOf(principal)))
    }

    /** Searches people, groups and roles to add, debounced. Needs the people picker. */
    fun onSearchQueryChange(query: String) {
        searchJob?.cancel()
        // The server answers 400 to a too-short query, so one is never sent.
        if (!AclSearch.isSendable(query) || resourceId == null || !currentGates().canSearch) {
            search.value = AclSearch(query = query)
            return
        }
        // Searching from the first keystroke, so the debounce never reads as "no matches".
        search.value = search.value.copy(query = query, searching = true, failed = false)
        searchJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val result = permissionsRepository.searchPrincipals(query.trim())
            if (search.value.query != query) return@launch
            search.value = when (result) {
                is Result.Success -> AclSearch(query = query, results = result.data)
                is Result.Error -> AclSearch(query = query, failed = true)
                is Result.Loading -> return@launch
            }
        }
    }

    private fun resetSearch() {
        searchJob?.cancel()
        searchJob = null
        search.value = AclSearch()
    }

    private suspend fun load(gen: Long, id: String) {
        val roles = scope.async { permissionsRepository.getResourceRoles(policy.resourceType) }
        val grants = permissionsRepository.getResourcePermissions(policy.resourceType, id)
        val assignable = assignableRoles((roles.await() as? Result.Success)?.data.orEmpty())
        if (gen != generation) return
        phase.value = when (grants) {
            is Result.Success -> Phase.Ready(
                principals = grants.data.principals,
                isPublic = grants.data.public,
                publicRoleId = grants.data.publicAccessRoleId,
                roles = assignable,
                grantRoleId = assignable.firstOrNull { it.accessRoleId == policy.defaultRoleId }?.accessRoleId
                    ?: assignable.firstOrNull()?.accessRoleId,
            )
            is Result.Error -> Phase.Failed(grants.toAclError())
            is Result.Loading -> return
        }
    }

    /** The roles a grant may take: never the owner's; the default alone when the list didn't load. */
    private fun assignableRoles(serverRoles: List<AccessRole>): List<AccessRole> {
        val offered = serverRoles.filter { it.accessRoleId != policy.ownerRoleId }
        if (offered.isNotEmpty()) return offered
        return listOfNotNull(policy.defaultRoleId?.let { AccessRole(accessRoleId = it) })
    }

    private fun write(current: Phase.Ready, body: UpdateResourcePermissionsRequest, onSuccess: () -> Unit = {}) {
        val id = resourceId ?: return
        val gen = generation
        phase.value = current.copy(busy = true, error = null)
        scope.launch {
            val result = permissionsRepository.updateResourcePermissions(policy.resourceType, id, body)
            if (gen != generation) return@launch
            when (result) {
                is Result.Success -> {
                    val fresh = permissionsRepository.getResourcePermissions(policy.resourceType, id)
                    if (gen != generation) return@launch
                    val base = phase.value as? Phase.Ready ?: return@launch
                    val next = (fresh as? Result.Success)?.let { base.withGrants(it.data) } ?: base.applied(body)
                    phase.value = next.copy(busy = false, error = null)
                    onSuccess()
                }
                is Result.Error -> phase.update { now ->
                    (now as? Phase.Ready)?.copy(busy = false, error = result.toAclError()) ?: now
                }
                is Result.Loading -> Unit
            }
        }
    }

    private fun isOwner(principal: Principal): Boolean =
        policy.ownerRoleId != null && principal.accessRoleId == policy.ownerRoleId

    private fun Phase.Ready.defaultGrantRole(): String? =
        roles.firstOrNull { it.accessRoleId == policy.defaultRoleId }?.accessRoleId ?: roles.firstOrNull()?.accessRoleId

    private fun currentGates(): Gates = gates(roleRepository.userPermissions.value)

    private fun gates(role: UserRolePermissions?) = Gates(
        canSharePublic = role.hasAccessStrictOrDenied(policy.permissionType, Permission.SHARE_PUBLIC),
        canSearch = role.hasAccessStrictOrDenied(PermissionType.PEOPLE_PICKER, Permission.VIEW_USERS) ||
            role.hasAccessStrictOrDenied(PermissionType.PEOPLE_PICKER, Permission.VIEW_GROUPS) ||
            role.hasAccessStrictOrDenied(PermissionType.PEOPLE_PICKER, Permission.VIEW_ROLES),
    )

    private fun render(phase: Phase, search: AclSearch, gates: Gates): AclState = when (phase) {
        Phase.Closed -> AclState.Closed
        Phase.Loading -> AclState.Loading
        is Phase.Failed -> AclState.Failed(phase.error)
        is Phase.Ready -> {
            val granted = phase.principals.mapTo(HashSet()) { it.key }
            AclState.Ready(
                // The owner first, then everyone else in the server's order.
                entries = phase.principals
                    .map { AclEntry(principal = it, locked = isOwner(it)) }
                    .sortedByDescending { it.locked },
                isPublic = phase.isPublic,
                canSharePublic = gates.canSharePublic,
                canSearch = gates.canSearch,
                roles = phase.roles,
                grantRoleId = phase.grantRoleId,
                search = if (gates.canSearch) {
                    search.copy(results = search.results.filterNot { it.key in granted })
                } else {
                    AclSearch()
                },
                busy = phase.busy,
                error = phase.error,
            )
        }
    }

    private data class Gates(val canSharePublic: Boolean, val canSearch: Boolean)

    private sealed interface Phase {
        data object Closed : Phase
        data object Loading : Phase
        data class Failed(val error: AclError) : Phase
        data class Ready(
            val principals: List<Principal>,
            val isPublic: Boolean,
            val publicRoleId: String?,
            val roles: List<AccessRole>,
            val grantRoleId: String?,
            val busy: Boolean = false,
            val error: AclError? = null,
        ) : Phase {
            fun withGrants(grants: ResourcePermissions) = copy(
                principals = grants.principals,
                isPublic = grants.public,
                publicRoleId = grants.publicAccessRoleId,
            )

            /** What [body] did, for when the read-back after it failed: the write itself landed. */
            fun applied(body: UpdateResourcePermissionsRequest): Ready {
                val removed = body.removed.mapTo(HashSet()) { it.key }
                val updated = body.updated.associateBy { it.key }
                val kept = principals.filterNot { it.key in removed || it.key in updated }
                return copy(
                    principals = kept + updated.values,
                    isPublic = body.public ?: isPublic,
                    publicRoleId = when (body.public) {
                        true -> body.publicAccessRoleId
                        false -> null
                        null -> publicRoleId
                    },
                )
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}

/** A principal's identity across the grants list and search results (Entra hits may lack an id). */
internal val Principal.key: String get() = "$type:${id ?: idOnTheSource}"

/**
 * The 400 upstream answers any change touching a shared link's owner with
 * (`rejectSharedLinkOwnerPermissionChanges`). Mirrored by hand; see `scripts/mirrors.json`
 * `shared-link-owner-protection`.
 */
internal const val OWNER_PROTECTED_MESSAGE = "Shared link owner permissions cannot be changed"

@Serializable
private data class AclErrorBody(val message: String? = null)

private val errorJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/** How the access editor words this failure. */
internal fun Result.Error.toAclError(): AclError {
    val api = exception as? ApiException
    val bodyMessage = api?.body?.let { text ->
        runCatching { errorJson.decodeFromString(AclErrorBody.serializer(), text) }.getOrNull()?.message
    }
    return when {
        api?.statusCode == HTTP_BAD_REQUEST && bodyMessage == OWNER_PROTECTED_MESSAGE -> AclError.OWNER_PROTECTED
        api?.statusCode == HTTP_FORBIDDEN -> AclError.FORBIDDEN
        api?.statusCode == HTTP_NOT_FOUND -> AclError.NOT_FOUND
        kind == FailureKind.Network || kind == FailureKind.Timeout -> AclError.NETWORK
        else -> AclError.GENERIC
    }
}

private const val HTTP_BAD_REQUEST = 400
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
