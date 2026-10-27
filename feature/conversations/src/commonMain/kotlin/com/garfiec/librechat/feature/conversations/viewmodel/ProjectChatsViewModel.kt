package com.garfiec.librechat.feature.conversations.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.datetime.DateGroup
import com.garfiec.librechat.core.common.extensions.RelativeTimeReference
import com.garfiec.librechat.core.common.extensions.dayBoundaryReferences
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.ProjectRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.TagRepository
import com.garfiec.librechat.core.data.share.ShareLinkController
import com.garfiec.librechat.core.data.share.ShareLinkControllerFactory
import com.garfiec.librechat.core.model.ChatProject
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.model.SAVED_TAG
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.canCreateSharedLinks
import com.garfiec.librechat.core.model.permissions.hasAccessOrPermissive
import com.garfiec.librechat.feature.conversations.components.ConversationDisplayData
import com.garfiec.librechat.feature.conversations.export.ConversationExporter
import com.garfiec.librechat.feature.conversations.export.ExportFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Sort field for the project chat list (v0.8.7), matching the convos endpoint. */
enum class ProjectChatsSort(val sortBy: String) {
    UPDATED("updatedAt"),
    CREATED("createdAt"),
}

@Immutable
data class ProjectChatsUiState(
    val groupedConversations: List<Pair<DateGroup, List<ConversationDisplayData>>> = emptyList(),
    val conversationCount: Int = 0,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val nextCursor: String? = null,
    val hasMore: Boolean = true,
    val sort: ProjectChatsSort = ProjectChatsSort.UPDATED,
    val tags: List<ConversationTag> = emptyList(),
    val bookmarksEnabled: Boolean = true,
    /** Share shows on rows: the server has shared links on and the role may create them. */
    val sharedLinksEnabled: Boolean = false,
    /** The project itself, for the header and Edit. Null while loading, and always for Unassigned. */
    val project: ChatProject? = null,
    /** The server no longer has this project (deleted elsewhere): the page has nothing to show. */
    val projectDeleted: Boolean = false,
)

/**
 * Project-filtered conversation list (v0.8.7 "Show all"). Network-direct: loads pages via
 * [ConversationRepository.getConversationsForProject] and holds them itself, since Room can't
 * filter by project. Mirrors [ConversationListViewModel]'s action surface so the screen can
 * reuse the same row + action-sheet machinery.
 */
private const val HTTP_NOT_FOUND = 404
private const val PAGE_SIZE = 25

class ProjectChatsViewModel(
    private val projectId: String,
    private val conversationRepository: ConversationRepository,
    private val tagRepository: TagRepository,
    shareLinkControllerFactory: ShareLinkControllerFactory,
    private val conversationExporter: ConversationExporter,
    private val roleRepository: RoleRepository,
    private val configRepository: ConfigRepository,
    private val projectRepository: ProjectRepository,
    // Defaulted rather than Koin-provided: this one is built by an explicit `viewModel { }` block
    // (projectId arrives via parametersOf), so a default costs no DI wiring. Tests override it —
    // the production flow never completes, which would hang `advanceUntilIdle()`.
    private val dayBoundaries: Flow<RelativeTimeReference> = dayBoundaryReferences(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectChatsUiState())
    val uiState: StateFlow<ProjectChatsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ConversationListEvent>()
    val events: SharedFlow<ConversationListEvent> = _events.asSharedFlow()

    private val pager = CursorPager(
        scope = viewModelScope,
        idOf = { it.conversationId.orEmpty() },
        onError = { message -> _uiState.value = _uiState.value.copy(error = message) },
    ) { cursor, limit ->
        when (
            val result = conversationRepository.getConversationsForProject(
                projectId = projectId,
                cursor = cursor,
                limit = limit ?: PAGE_SIZE,
                sortBy = _uiState.value.sort.sortBy,
                sortDirection = "desc",
            )
        ) {
            is Result.Success -> Result.Success(CursorPage(result.data.conversations, result.data.nextCursor))
            is Result.Error -> result
            is Result.Loading -> result
        }
    }

    private val actions = ConversationListActionsDelegate(
        scope = viewModelScope,
        events = _events,
        conversationRepository = conversationRepository,
        tagRepository = tagRepository,
        shareLinkControllerFactory = shareLinkControllerFactory,
        conversationExporter = conversationExporter,
        // Network-direct list: re-fetch after a mutation so the change is reflected.
        onMutated = { pager.reloadQuietly() },
    )

    private val projectActions = ProjectActionsDelegate(
        scope = viewModelScope,
        projectRepository = projectRepository,
        emitError = { _events.emit(ConversationListEvent.ShowError(it)) },
    )

    private val isRealProject = projectId != ChatProject.UNASSIGNED

    private var projectJob: Job? = null

    init {
        observePaging()
        load()
        loadProject()
        observeGrouped()
        observeTags()
        observePermissions()
        viewModelScope.launch {
            projectRepository.revision.drop(1).collect {
                loadProject()
                pager.reloadQuietly()
            }
        }
    }

    private fun loadProject() {
        if (!isRealProject) return
        projectJob?.cancel()
        projectJob = viewModelScope.launch {
            when (val result = projectRepository.getProject(projectId)) {
                is Result.Success -> _uiState.value = _uiState.value.copy(project = result.data)
                is Result.Error ->
                    if ((result.exception as? ApiException)?.statusCode == HTTP_NOT_FOUND) {
                        _uiState.value = _uiState.value.copy(projectDeleted = true)
                    } else {
                        Logger.w(result.exception) { "Failed to load project $projectId" }
                    }
                is Result.Loading -> Unit
            }
        }
    }

    /**
     * Back from a chat started here: Nav3 kept this ViewModel alive and nothing else reloads it, so
     * the new chat would be missing. Skips while a first page is already loading.
     */
    fun refreshOnReturn() {
        retryProjectIfMissing()
        if (!pager.isLoadingFirstPage) pager.reloadQuietly()
    }

    /** The project failed to load (offline, say): a pull or a return tries again. */
    private fun retryProjectIfMissing() {
        val state = _uiState.value
        if (state.project == null && !state.projectDeleted && projectJob?.isActive != true) loadProject()
    }

    fun editProject(name: String, description: String) = projectActions.edit(projectId, name, description)

    private fun observeGrouped() {
        viewModelScope.launch {
            // dayBoundaryReferences() re-groups at local midnight: date sections are clock-dependent
            // and membership (not just the label) changes when the date rolls over.
            combine(
                pager.items,
                configRepository.endpointConfigs,
                configRepository.serverUrl,
                dayBoundaries,
            ) { convos, configs, serverUrl, reference ->
                groupConversationsByDate(convos, configs, serverUrl, reference) to convos.size
            }.collect { (grouped, count) ->
                _uiState.value = _uiState.value.copy(groupedConversations = grouped, conversationCount = count)
            }
        }
    }

    private fun observeTags() {
        viewModelScope.launch {
            tagRepository.observeTags().collect { tags ->
                _uiState.value = _uiState.value.copy(tags = tags.filter { it.count > 0 && it.tag != SAVED_TAG })
            }
        }
    }

    private fun observePermissions() {
        viewModelScope.launch {
            combine(roleRepository.userPermissions, configRepository.startupConfig) { role, config ->
                role to config
            }.collect { (role, config) ->
                _uiState.update {
                    it.copy(
                        bookmarksEnabled = role.hasAccessOrPermissive(PermissionType.BOOKMARKS, Permission.USE),
                        sharedLinksEnabled = role.canCreateSharedLinks(config?.sharedLinksEnabled ?: false),
                    )
                }
            }
        }
    }

    fun getConversation(conversationId: String): Conversation? =
        pager.items.value.firstOrNull { it.conversationId == conversationId }

    private fun observePaging() {
        viewModelScope.launch {
            pager.state.collect { paging ->
                _uiState.value = _uiState.value.copy(
                    isLoading = paging.isLoading,
                    isRefreshing = paging.isRefreshing,
                    nextCursor = paging.nextCursor,
                    hasMore = paging.nextCursor != null,
                )
            }
        }
    }

    fun load() = pager.load()

    fun loadMore() = pager.loadMore()

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        retryProjectIfMissing()
        pager.refresh()
    }

    fun setSort(sort: ProjectChatsSort) {
        if (sort == _uiState.value.sort) return
        _uiState.value = _uiState.value.copy(sort = sort)
        load()
    }

    fun toggleFavorite(conversation: Conversation) = actions.toggleFavorite(conversation)

    fun renameConversation(id: String, newTitle: String) = actions.renameConversation(id, newTitle)

    fun archiveConversation(id: String) = actions.archiveConversation(id)

    fun deleteConversation(id: String) = actions.deleteConversation(id)

    val shareLink: ShareLinkController get() = actions.shareLink

    fun shareConversation(conversationId: String) = actions.shareConversation(conversationId)

    fun closeShareSheet() = actions.closeShareSheet()

    fun duplicateConversation(conversationId: String, title: String?) =
        actions.duplicateConversation(conversationId, title)

    fun updateConversationTags(conversation: Conversation, userTags: List<String>) =
        actions.updateConversationTags(conversation, userTags)

    fun exportConversation(conversationId: String, title: String?, format: ExportFormat) =
        actions.exportConversation(conversationId, title, format)

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
