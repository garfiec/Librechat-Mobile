package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.ShareRepository
import com.garfiec.librechat.core.model.SharedLink
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.hasAccessOrPermissive
import com.garfiec.librechat.feature.settings.model.SharedLinkDisplayData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class SharedLinksUiState(
    val links: List<SharedLinkDisplayData> = emptyList(),
    val nextCursor: String? = null,
    val hasNextPage: Boolean = false,
    val isLoading: Boolean = false,
    val serverUrl: String = "",
    /**
     * SHARED_LINKS/CREATE, which `PATCH /api/share/:shareId` now requires — updating a link
     * re-publishes the conversation, so revoking CREATE stops updates as well as creates.
     * Scoped to the update action only: DELETE is deliberately ungated server-side, so a role
     * that may no longer re-publish may still revoke.
     */
    val updateEnabled: Boolean = true,
    /**
     * Whether re-publishing keeps the link's id. v0.8.8-rc1's `updateSharedLink` writes no new
     * `shareId`; every earlier server mints one with `nanoid()` and orphans the URL already handed
     * out. Only the confirmation copy depends on this — the action itself is useful either way —
     * and it is fail-safe FALSE on an unresolved version, so an unknown server warns rather than
     * promising a guarantee it may not honour.
     */
    val updateKeepsUrl: Boolean = false,
    /** Transient error surfaced via snackbar; cleared by [SharedLinksViewModel.dismissError]. */
    val error: String? = null,
)

/** Backs Settings → Data → Shared links: lists, re-publishes and deletes the user's shared links. */
class SharedLinksViewModel(
    private val shareRepository: ShareRepository,
    serverDataStore: ServerDataStore,
    roleRepository: RoleRepository,
    configRepository: ConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SharedLinksUiState())

    val uiState: StateFlow<SharedLinksUiState> = combine(
        _uiState,
        serverDataStore.currentUrlFlow,
        roleRepository.userPermissions,
        configRepository.detectedBackendVersion,
    ) { state, serverUrl, role, version ->
        state.copy(
            serverUrl = serverUrl,
            // Permissive on unknown: an older server emits no such permission and still accepts
            // the call, and the server enforces with 403 either way.
            updateEnabled = role.hasAccessOrPermissive(PermissionType.SHARED_LINKS, Permission.CREATE),
            // Fail-safe false: only a CONFIRMED rc1+ server keeps the shareId across a re-publish,
            // so an unresolved version warns instead of promising it.
            updateKeepsUrl = version != null && BackendVersion.isCompatibleOrNewer(version, "0.8.8-rc1"),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SharedLinksUiState())

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = shareRepository.getSharedLinksPaginated()) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        links = result.data.links.map { link -> link.toDisplayData() },
                        nextCursor = result.data.nextCursor,
                        hasNextPage = result.data.hasNextPage ?: false,
                        isLoading = false,
                    )
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, error = result.message ?: "Failed to load shared links")
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun loadMore() {
        val current = _uiState.value
        if (current.isLoading) return
        val cursor = current.nextCursor ?: return
        // Set before launching so a second trigger in the same frame sees it and bails.
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            when (val result = shareRepository.getSharedLinksPaginated(cursor = cursor)) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        links = it.links + result.data.links.map { link -> link.toDisplayData() },
                        nextCursor = result.data.nextCursor,
                        hasNextPage = result.data.hasNextPage ?: false,
                        isLoading = false,
                    )
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, error = result.message ?: "Failed to load more shared links")
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    /**
     * Re-publishes a shared link against the conversation as it stands now.
     *
     * On v0.8.8-rc1+ the link's id and URL survive, so anything already handed out keeps working;
     * earlier servers mint a new id and orphan the old URL, which is what the confirmation copy
     * is gated on. Either way this changes what is behind the link, which is why the route now
     * demands SHARED_LINKS CREATE and why the caller confirms first.
     *
     * The response carries only `{_id, shareId, conversationId, targetMessageId}` — no title, no
     * `createdAt`, no `isPublic` — on BOTH versions, so the row is patched rather than replaced.
     * Rebuilding it from the response relabels every updated link "Untitled Conversation" and
     * drops its date; adopting the returned `shareId` is what keeps a pre-rc1 row pointing at the
     * link that now exists.
     */
    fun update(shareId: String) {
        viewModelScope.launch {
            when (val result = shareRepository.updateShareLink(shareId)) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        links = it.links.map { link ->
                            if (link.shareId == shareId) {
                                link.copy(shareId = result.data.shareId ?: link.shareId)
                            } else {
                                link
                            }
                        },
                    )
                }
                is Result.Error -> {
                    // 403 is a distinct, permanent outcome rather than a transient failure: the
                    // role may still hold SHARED_LINKS.USE (and may still DELETE), so a generic
                    // "failed" reads as something worth retrying when it never will be.
                    val forbidden = (result.exception as? ApiException)?.statusCode == HTTP_FORBIDDEN
                    _uiState.update {
                        it.copy(
                            error = if (forbidden) {
                                "You don't have permission to update shared links. " +
                                    "You can still delete this link."
                            } else {
                                result.message ?: "Failed to update the shared link"
                            },
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun delete(shareId: String) {
        viewModelScope.launch {
            when (val result = shareRepository.deleteShareLink(shareId)) {
                is Result.Success -> _uiState.update {
                    it.copy(links = it.links.filter { link -> link.shareId != shareId })
                }
                is Result.Error -> _uiState.update {
                    it.copy(error = result.message ?: "Failed to delete shared link")
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}

/** The route answers 403 when the caller's role lost SHARED_LINKS CREATE. */
private const val HTTP_FORBIDDEN = 403

private fun SharedLink.toDisplayData() = SharedLinkDisplayData(
    shareId = shareId ?: "",
    title = title ?: "Untitled Conversation",
    createdAt = createdAt,
    isPublic = isPublic,
)
