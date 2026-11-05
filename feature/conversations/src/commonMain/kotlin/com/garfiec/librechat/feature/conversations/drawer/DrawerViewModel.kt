package com.garfiec.librechat.feature.conversations.drawer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.identity.AccountTransition
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.accountTransitions
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.common.network.ConnectivityObserver
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.bookmarks.BookmarkPickerController
import com.garfiec.librechat.core.data.repository.ChatRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.ProjectRepository
import com.garfiec.librechat.core.data.repository.PullRequestRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.TagRepository
import com.garfiec.librechat.core.data.repository.bookmarksEnabled
import com.garfiec.librechat.core.data.share.ShareLinkController
import com.garfiec.librechat.core.data.share.ShareLinkControllerFactory
import com.garfiec.librechat.core.model.ChatProject
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.SAVED_TAG
import com.garfiec.librechat.core.model.config.ChatProjectLimits
import com.garfiec.librechat.core.model.config.ConversationListLimits
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.config.isSchedulesEnabled
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.permissions.canCreateSharedLinks
import com.garfiec.librechat.core.model.permissions.canReachMemoriesSettings
import com.garfiec.librechat.core.model.permissions.hasAccessOrPermissive
import com.garfiec.librechat.core.model.pullrequest.ConversationPullRequest
import com.garfiec.librechat.core.model.resolveEndpointIconUrl
import com.garfiec.librechat.feature.conversations.components.message
import com.garfiec.librechat.feature.conversations.export.ConversationExporter
import com.garfiec.librechat.feature.conversations.export.ExportFormat
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.favorite_update_failed
import com.garfiec.librechat.feature.conversations.resources.mark_read_failed
import com.garfiec.librechat.feature.conversations.resources.mark_unread_failed
import com.garfiec.librechat.feature.conversations.resources.project_create_failed
import com.garfiec.librechat.feature.conversations.resources.project_move_failed
import com.garfiec.librechat.feature.conversations.viewmodel.ConversationListActionsDelegate
import com.garfiec.librechat.feature.conversations.viewmodel.ConversationListEvent
import com.garfiec.librechat.feature.conversations.viewmodel.ProjectActionsDelegate
import com.garfiec.librechat.feature.conversations.viewmodel.projectLimits
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Drawer-data half of the navigation shell, split out of `NavHostViewModel` so `:shared` stays nav
 * glue rather than a stealth feature module. Owns the drawer's conversation list, favorites/pinned sections,
 * long-press action menu, Projects tab, and the drawer's "Library" tab preference. Activity-scoped
 * (registered as a plain `viewModelOf`), so a single instance is shared by the phone drawer, the
 * tablet sidebar, and the nav host's active-conversation tracking.
 */
@Suppress("LongParameterList", "TooManyFunctions") // debt: 13 parameters, 37 functions
class DrawerViewModel(
    private val conversationRepository: ConversationRepository,
    private val roleRepository: RoleRepository,
    private val tagRepository: TagRepository,
    private val projectRepository: ProjectRepository,
    private val configRepository: ConfigRepository,
    shareLinkControllerFactory: ShareLinkControllerFactory,
    private val conversationExporter: ConversationExporter,
    private val activeAccountProvider: ActiveAccountProvider,
    private val preferences: DrawerPreferences,
    private val chatRepository: ChatRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val search: DrawerSearchSources,
    connectivityObserver: ConnectivityObserver,
    /** Holds the applied filters through process death; a cold start begins unfiltered. */
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val conversationListStateHolder = ConversationListStateHolder(
        conversationRepository = conversationRepository,
        scope = viewModelScope,
        remoteTitleSearch = search::remoteTitles,
    )

    private val messageSearch: Flow<DrawerMessageSearch> =
        search.messageSearch(conversationListStateHolder.searchQuery, activeAccountProvider)

    private val favoriteConversations: StateFlow<List<Conversation>> =
        conversationListStateHolder.recentConversations
            .map { list -> list.filter { SAVED_TAG in it.tags } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Conversations with a live generation job; see [activeJobsPoll]. Account-scoped: cleared on a switch. */
    private val _activeJobIds = MutableStateFlow<Set<String>>(emptySet())
    private val activeJobIds: StateFlow<Set<String>> = _activeJobIds.asStateFlow()

    private val pinnedConversations: StateFlow<List<Conversation>> =
        conversationListStateHolder.recentConversations
            .map { list -> list.filter { it.pinned == true } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Inputs for the drawer long-press action menu and the bookmark surfaces: the config-driven
    // shared-links flag and the SHARED_LINKS role permission that gate the Share action, and the one
    // bookmarks gate (BOOKMARKS USE and interface.bookmarks) behind the stars, Favorites and the
    // checklist.
    private val drawerActionMenuState: StateFlow<DrawerActionMenuState> =
        combine(
            configRepository.startupConfig,
            configRepository.detectedBackendVersion,
            roleRepository.userPermissions,
        ) { config, version, permissions ->
            // Gate fail-closed on unknown version so older servers don't surface actions
            // they'd 404 on. Thresholds differ: POST /api/convos/pin landed BETWEEN
            // v0.8.7-rc1 and v0.8.7 final (upstream 743f57f63), so pin needs the final;
            // /api/projects was already in v0.8.7-rc1.
            val pinSupported = version != null && BackendVersion.isCompatibleOrNewer(version, "0.8.7")
            val projectsSupported = version != null && BackendVersion.isCompatibleOrNewer(version, "0.8.7-rc1")
            // POST /api/convos/seen and /unread, and the GET /api/convos list facets, are v0.8.9-rc1.
            // Unknown version: off, as an older server answers the writes 404 and ignores the facets.
            val readStateSupported = version != null && BackendVersion.isCompatibleOrNewer(version, READ_STATE_MIN_VERSION)
            val canShare = permissions.canCreateSharedLinks(config?.sharedLinksEnabled ?: false)
            // Scheduled chats are NOT version-gated. The server announces them on `interface`, and
            // ABSENT means OFF there — the opposite of every other flag — so a version compare on
            // top would only add a second way to get it wrong. `isSchedulesEnabled` is the one
            // reader; do not fold it into a generic interface-flag helper.
            val schedulesEnabled = isSchedulesEnabled(config?.interfaceConfig?.schedules)
            DrawerActionMenuState(
                bookmarksEnabled = bookmarksEnabled(permissions, config?.interfaceConfig),
                sharedLinksEnabled = canShare,
                pinEnabled = pinSupported,
                projectsEnabled = projectsSupported,
                schedulesEnabled = schedulesEnabled,
                readStateEnabled = readStateSupported,
                filtersEnabled = readStateSupported,
                sharedLinksConfigured = config?.sharedLinksEnabled == true,
                maxEndpointFilters = (config?.conversationListLimits ?: ConversationListLimits.DEFAULT).maxEndpointFilters,
                maxEndpointNameLength = (config?.conversationListLimits ?: ConversationListLimits.DEFAULT).maxEndpointNameLength,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DrawerActionMenuState())

    // Chat Projects (v0.8.7). Loaded lazily for the move-to-project picker and the drawer's Projects
    // tab folder list, every page, in the user's Switchboard-only custom order (the server keeps none).
    private val _projects = MutableStateFlow<List<ChatProject>>(emptyList())
    val projects: StateFlow<List<ChatProject>> = _projects.asStateFlow()

    private var projectsLoadJob: Job? = null
    private var projectOrderSaveJob: Job? = null
    private var expandedChatsJob: Job? = null

    /** Parent of the star's writes still queued or out, which an account switch drops. */
    private val favoriteWrites = SupervisorJob(viewModelScope.coroutineContext[Job])

    /** Parent of the run-transition refreshes still out, which an account switch drops. */
    private val runRefreshes = SupervisorJob(viewModelScope.coroutineContext[Job])
    private var lastProjectsReload: TimeSource.Monotonic.ValueTimeMark? = null

    // Inline project-chat accordion for the drawer Projects tab. Network-direct per project (Room
    // can't filter by project — see ProjectChatsViewModel), single-expand: only the open project's
    // chats are held. Mapped to the drawer row type against the active id + endpoint configs so the
    // rows match the recents list.
    private val _expandedProjectId = MutableStateFlow<String?>(null)
    private val _expandedProjectChats = MutableStateFlow<List<Conversation>>(emptyList())
    private val _expandedProjectLoading = MutableStateFlow(false)

    // This transport snapshot needs no structural equality. A concrete type also keeps the
    // native optimizer from joining this configuration to unrelated generic Pair payloads.
    private class DrawerModelConfig(
        val endpoints: Map<String, EndpointConfig>,
        val specs: ModelSpecs?,
        val serverUrl: String,
        val pullRequests: Map<String, ConversationPullRequest> = emptyMap(),
    ) {
        fun display(convo: Conversation, activeId: String?, runningIds: Set<String> = emptySet()) =
            convo.toDrawerDisplayData(activeId, endpoints, serverUrl, specs, runningIds, pullRequests)
    }

    /** The rows' pull request marks and the lookups behind them. */
    private val pullRequests = DrawerPullRequests(configRepository, pullRequestRepository, viewModelScope)

    private val drawerModelConfig = combine(
        configRepository.endpointConfigs,
        // Only the specs matter here: any other startup-config change would re-map the whole list.
        configRepository.startupConfig.map { it?.modelSpecs }.distinctUntilChanged(),
        configRepository.serverUrl,
        pullRequests.marks,
    ) { endpoints, specs, serverUrl, marks -> DrawerModelConfig(endpoints, specs, serverUrl, marks) }

    val inlineProjectChats: StateFlow<InlineProjectChatsState> = combine(
        _expandedProjectId,
        _expandedProjectChats,
        _expandedProjectLoading,
        conversationListStateHolder.activeConversationId,
        drawerModelConfig,
    ) { expandedId, convos, loading, activeId, modelConfig ->
        InlineProjectChatsState(
            expandedProjectId = expandedId,
            conversations = convos.map { modelConfig.display(it, activeId) },
            isLoading = loading,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InlineProjectChatsState())

    // One-shot events for the drawer action menu (share-link copied, export-ready,
    // navigate-to-duplicate, errors). Reuses the conversation-list event type. extraBufferCapacity
    // lets emit() return immediately instead of suspending if the collector (ConversationActionEffects)
    // is momentarily absent — e.g. an action's result arriving as the drawer leaves composition.
    private val _events = MutableSharedFlow<ConversationListEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ConversationListEvent> = _events.asSharedFlow()

    /**
     * The long-press menu's "Bookmarks…" checklist, one chat at a time. A tick or create that fails
     * says so in the sheet; a load that closes it, or a save that fails after it closed, surfaces as
     * the drawer's error toasts.
     */
    val bookmarkPicker = BookmarkPickerController(tagRepository, viewModelScope)

    // Shared row-action logic (rename/archive/delete/share/duplicate/export) — the same delegate
    // ProjectChatsViewModel uses, so the drawer long-press menu surfaces
    // Result.Error failures (toast) instead of swallowing them in a dead try/catch around a
    // Result-returning repo call. Room-backed here, so onMutated is a no-op: the conversation
    // observe-Flow already re-emits after a mutation.
    private val conversationActions = ConversationListActionsDelegate(
        scope = viewModelScope,
        events = _events,
        conversationRepository = conversationRepository,
        tagRepository = tagRepository,
        shareLinkControllerFactory = shareLinkControllerFactory,
        conversationExporter = conversationExporter,
        // A filtered list holds rows Room may not (never paged in), so a mutation reloads it; the
        // cached list re-emits on its own.
        onMutated = {
            if (conversationListStateHolder.filters.value != null) conversationListStateHolder.refreshConversations()
        },
    )

    // Shared project CRUD (same delegate ProjectsViewModel uses).
    /** The drawer's project sheets cap the description here; the server refuses longer with a 400. */
    val projectLimits: StateFlow<ChatProjectLimits> = configRepository.projectLimits(viewModelScope)

    private val projectActions = ProjectActionsDelegate(
        scope = viewModelScope,
        projectRepository = projectRepository,
        emitError = { _events.emit(ConversationListEvent.ShowErrorRes(it)) },
    )

    // Drawer "Library" tab is optimistic local state (instant toggle) with DataStore as a write-behind
    // cache: hydrated once in init and written on change. Null = not yet hydrated; the drawer shows
    // Chats for the brief window until the persisted value loads, and the seed's update { it ?: ... }
    // lets a tap in that window win over the incoming persisted value.
    private val _drawerLibraryTab = MutableStateFlow<DrawerTab?>(null)
    val drawerLibraryTab: StateFlow<DrawerTab?> = _drawerLibraryTab.asStateFlow()

    fun setDrawerLibraryTab(tab: DrawerTab) {
        _drawerLibraryTab.value = tab
        viewModelScope.launch { preferences.settings.setDrawerLibraryTab(tab.toStorageString()) }
    }

    /**
     * Role-permission flags for drawer UI. Permissive-default (`?: true`) until the
     * role loads; once it does, denied permissions flip the flags off and the drawer
     * re-composes to hide the corresponding surfaces. Skills also needs the agents
     * endpoint's `skills` capability (see [isSkillsNavEnabled]).
     */
    private val drawerPermissionFlags: StateFlow<DrawerPermissionFlags> =
        combine(roleRepository.userPermissions, configRepository.endpointConfigs) { role, endpoints ->
            drawerPermissionFlags(role, endpoints[EndpointConstants.AGENTS]?.capabilities)
        }
            .stateIn(viewModelScope, SharingStarted.Eagerly, DrawerPermissionFlags())

    /**
     * Running chats no loaded row holds, fetched off the server and never cached (upstream
     * `useRunningConversationsQuery`): a chat filed in a project, or one past the loaded pages.
     * Pinned ones are already cached and are read from there. Account-scoped: cleared on a switch.
     */
    private val _fetchedUnlisted = MutableStateFlow<Map<String, Conversation>>(emptyMap())
    private val unlistedInFlight = mutableSetOf<String>()

    /** The unlisted running rows, only while nothing narrows the list (no query, no filters). */
    private val unlistedRunning: Flow<List<Conversation>> = combine(
        conversationListStateHolder.groupedConversations,
        activeJobIds,
        conversationListStateHolder.recentConversations,
        _fetchedUnlisted,
        combine(conversationListStateHolder.searchQuery, conversationListStateHolder.filters) { query, filters ->
            query.isBlank() && filters == null
        },
    ) { grouped, running, recent, fetched, unnarrowed ->
        if (!unnarrowed) return@combine emptyList()
        val missing = unlistedRunningIds(grouped, running)
        if (missing.isEmpty()) return@combine emptyList()
        val cached = recent.filter { it.conversationId in missing }.associateBy { it.conversationId }
        missing.mapNotNull { id -> cached[id] ?: fetched[id] }
    }.distinctUntilChanged()

    /** The filters the user applied, restored after process death. */
    private val _drawerFilters = MutableStateFlow(restoreFilters())

    private val isOnline: StateFlow<Boolean> = connectivityObserver.isConnected
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /**
     * All three conversation sections, mapped to row data.
     *
     * Kept deliberately upstream of `searchQuery`: that flow changes on every keystroke, and folding
     * it into this combine would re-map all three lists per character — including the grouped list,
     * which ConversationListStateHolder debounces at 300ms and which is therefore usually identical
     * to the previous keystroke's. Splitting it out means a keystroke only re-assembles
     * [DrawerUiState] from rows that were already mapped.
     */
    private val displayConversations: Flow<DrawerDisplaySnapshot> = combine(
        combine(conversationListStateHolder.groupedConversations, activeJobIds, unlistedRunning) { grouped, running, unlisted ->
            grouped.withRunningFirst(running, unlisted) to running
        },
        favoriteConversations,
        pinnedConversations,
        conversationListStateHolder.activeConversationId,
        drawerModelConfig,
    ) { (grouped, running), favConvos, pinnedConvos, activeId, modelConfig ->
        DrawerDisplaySnapshot(
            grouped = grouped.map { (group, convos) ->
                group to convos.map { modelConfig.display(it, activeId, running) }
            },
            favorites = favConvos.map { modelConfig.display(it, activeId, running) },
            pinned = pinnedConvos.map { modelConfig.display(it, activeId, running) },
        )
    }

    /** The search query as typed, for the field itself; [drawerUiState] carries it a beat later. */
    val searchQuery: StateFlow<String> = conversationListStateHolder.searchQuery

    /**
     * Where the drawer's cursor and list were. Unfolding a Fold moves the drawer from the phone sheet
     * to the tablet sidebar, a new place in the tree (and usually a recreated activity), so nothing
     * the old one remembered reaches the new one; this instance is shared by both.
     */
    val viewMemory = DrawerViewMemory()

    private val bookmarkOptions: Flow<List<FilterBookmarkOption>> = tagRepository.observeTags()
        .map { tags -> tags.mapNotNull { tag -> tag.tag?.takeIf(String::isNotBlank)?.let { FilterBookmarkOption(it, tag.count) } } }
        .distinctUntilChanged()

    private val filterOptions: Flow<DrawerFilterOptions> = combine(
        combine(configRepository.endpointConfigs, configRepository.serverUrl, ::Pair),
        drawerActionMenuState,
        _drawerFilters,
        isOnline,
        bookmarkOptions,
    ) { (endpoints, serverUrl), actionMenu, filters, online, bookmarks ->
        DrawerFilterOptions(
            endpoints = selectableEndpoints(endpoints.keys.toList(), filters.endpoints, actionMenu.maxEndpointNameLength)
                .map { name ->
                    val config = endpoints[name]
                    FilterEndpointOption(
                        name = name,
                        label = config?.modelDisplayLabel ?: name,
                        iconUrl = Conversation(endpoint = name).resolveEndpointIconUrl(endpoints, serverUrl),
                    )
                },
            maxEndpoints = actionMenu.maxEndpointFilters,
            sharedOnlyAvailable = actionMenu.sharedLinksConfigured,
            bookmarksAvailable = actionMenu.bookmarksEnabled,
            bookmarks = if (actionMenu.bookmarksEnabled) selectableBookmarks(bookmarks, filters.tags) else emptyList(),
            isOnline = online,
        )
    }.distinctUntilChanged()

    private val filterState: Flow<FilterState> = combine(
        _drawerFilters,
        conversationListStateHolder.filterLoadFailed,
        filterOptions,
    ) { filters, failed, options -> FilterState(filters, failed, options) }

    val drawerUiState: StateFlow<DrawerUiState> = combine(
        combine(displayConversations, messageSearch, filterState) { display, messages, filter ->
            Triple(display, messages, filter)
        },
        conversationListStateHolder.searchQuery,
        combine(
            conversationListStateHolder.isRefreshing,
            conversationListStateHolder.isLoadingMore,
            conversationListStateHolder.hasMore,
        ) { refreshing, loadingMore, hasMore ->
            Triple(refreshing, loadingMore, hasMore)
        },
        drawerPermissionFlags,
        drawerActionMenuState,
    ) { (display, messages, filter), query, refreshState, perms, actionMenu ->
        val (refreshing, loadingMore, hasMore) = refreshState
        DrawerUiState(
            messageSearch = if (query.isBlank()) DrawerMessageSearch.Idle else messages,
            groupedConversations = display.grouped,
            favoriteConversations = display.favorites,
            pinnedConversations = display.pinned,
            searchQuery = query,
            isRefreshing = refreshing,
            isLoadingMore = loadingMore,
            hasMore = hasMore,
            agentsEnabled = perms.agentsEnabled,
            marketplaceEnabled = perms.marketplaceEnabled,
            bookmarksEnabled = actionMenu.bookmarksEnabled,
            skillsEnabled = perms.skillsEnabled,
            promptsEnabled = perms.promptsEnabled,
            mcpServersEnabled = perms.mcpServersEnabled,
            memoriesEnabled = perms.memoriesEnabled,
            // BOTH halves, in upstream's order: the permission, then the server flag.
            schedulesEnabled = perms.schedulesPermitted && actionMenu.schedulesEnabled,
            sharedLinksEnabled = actionMenu.sharedLinksEnabled,
            pinEnabled = actionMenu.pinEnabled,
            projectsEnabled = actionMenu.projectsEnabled,
            readStateEnabled = actionMenu.readStateEnabled,
            filtersEnabled = actionMenu.filtersEnabled,
            filters = filter.filters,
            filterLoadFailed = filter.failed,
            filterOptions = filter.options,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DrawerUiState())

    private class FilterState(val filters: DrawerFilters, val failed: Boolean, val options: DrawerFilterOptions)

    /** The footer's Workspace row and tray: what the gates allow, split into pinned and the rest. */
    val workspace: StateFlow<WorkspaceUiState> =
        combine(drawerUiState.map { it.visibleWorkspace }.distinctUntilChanged(), preferences.pins.pinned, ::workspaceUiState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, WorkspaceUiState())

    /** Whether [accountId] is still the active account: a hit drawn before a switch is not opened. */
    fun isActiveAccount(accountId: String): Boolean = activeAccountProvider.currentAccountId()?.value == accountId

    private companion object {
        const val READ_STATE_MIN_VERSION = "0.8.9-rc1"
        const val FILTERS_KEY = "drawer_filters"
        val filtersJson = Json { ignoreUnknownKeys = true }
        private val PROJECTS_RELOAD_MERGE = 1.seconds
        const val ACTIVE_JOBS_POLL_MS = 5_000L
        const val ACTIVE_JOBS_GRACE_MS = 30_000L
    }

    private data class DrawerActionMenuState(
        val bookmarksEnabled: Boolean = true,
        val sharedLinksEnabled: Boolean = false,
        val pinEnabled: Boolean = false,
        val projectsEnabled: Boolean = false,
        val schedulesEnabled: Boolean = false,
        val readStateEnabled: Boolean = false,
        val filtersEnabled: Boolean = false,
        val sharedLinksConfigured: Boolean = false,
        val maxEndpointFilters: Int = ConversationListLimits.DEFAULT_MAX_ENDPOINT_FILTERS,
        val maxEndpointNameLength: Int = ConversationListLimits.DEFAULT_MAX_ENDPOINT_NAME_LENGTH,
    )

    /**
     * Whether a drawer surface is open and started; the active-jobs poll runs only then. Composition
     * alone is not the signal — see DrawerContent's `isOpen`.
     */
    private val drawerVisible = MutableStateFlow(false)

    fun setDrawerVisible(visible: Boolean) {
        drawerVisible.value = visible
    }

    /**
     * Upstream `useActiveJobs`: poll every [ACTIVE_JOBS_POLL_MS] while anything is running and for
     * [ACTIVE_JOBS_GRACE_MS] after the list empties (a queued successor is admitted a moment after
     * its predecessor ends), then go quiet until the conversation list itself moves. A failed read
     * keeps the last answer rather than dropping the group mid-run.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val activeJobsPoll: Flow<Set<String>> = drawerVisible.flatMapLatest { visible ->
        if (!visible) {
            emptyFlow()
        } else {
            flow {
                var lastListed: TimeSource.Monotonic.ValueTimeMark? = null
                while (true) {
                    val ids = (chatRepository.getActiveJobIds() as? Result.Success)?.data
                    if (ids != null) emit(ids)
                    if (!ids.isNullOrEmpty()) lastListed = TimeSource.Monotonic.markNow()
                    val inGrace = lastListed?.let { it.elapsedNow().inWholeMilliseconds < ACTIVE_JOBS_GRACE_MS } == true
                    if (inGrace) {
                        delay(ACTIVE_JOBS_POLL_MS)
                    } else {
                        conversationListStateHolder.recentConversations.drop(1).first()
                    }
                }
            }
        }
    }

    init {
        pullRequests.start(
            visible = drawerVisible,
            shown = combine(
                conversationListStateHolder.groupedConversations,
                pinnedConversations,
                favoriteConversations,
            ) { grouped, pinned, favorites -> pinned + favorites + grouped.flatMap { it.second } },
            running = activeJobIds,
        )
        viewModelScope.launch {
            activeJobsPoll.collect { ids ->
                val previous = _activeJobIds.value
                _activeJobIds.value = ids
                refreshRunTransitions((previous - ids) + (ids - previous))
                // Each tick also retries an unlisted row that wasn't written yet, so a short run isn't missed.
                fetchUnlistedRunning()
            }
        }
        // Filters apply once the server is known to take them; the ones restored after process
        // death reload here, and a server that turns out older drops them.
        viewModelScope.launch {
            combine(_drawerFilters, drawerActionMenuState.map { it.filtersEnabled }.distinctUntilChanged()) { filters, enabled ->
                filters.takeIf { enabled && it.isActive }
            }.distinctUntilChanged().collectLatest { applied -> applyToList(applied) }
        }
        viewModelScope.launch {
            val persisted = DrawerTab.fromString(preferences.settings.drawerLibraryTab.first())
            _drawerLibraryTab.update { it ?: persisted }
        }
        // Load the Chat Projects folders once the backend is known to support them (v0.8.7-rc1+).
        // detectedBackendVersion is a StateFlow (already conflated), so no distinctUntilChanged.
        viewModelScope.launch {
            configRepository.detectedBackendVersion.collect { version ->
                if (version != null && BackendVersion.isCompatibleOrNewer(version, "0.8.7-rc1")) {
                    loadProjects()
                }
            }
        }
        viewModelScope.launch {
            projectRepository.revision.drop(1).collect { reloadProjects(force = true) }
        }
        viewModelScope.launch {
            bookmarkPicker.failures.collect { _events.emit(ConversationListEvent.ShowErrorRes(it.message)) }
        }
        // Bookmarks turned off (role or server config) while the checklist was up: take it down.
        viewModelScope.launch {
            drawerActionMenuState.map { it.bookmarksEnabled }.distinctUntilChanged().collect { enabled ->
                if (!enabled) bookmarkPicker.close()
            }
        }
        // The active account changed underneath this Activity-scoped VM. This is the drawer half of
        // the reset the nav shell also performs (NavHostViewModel runs the nav/session half against
        // its own independent accountTransitions() subscription — the flow is cold per-collector, so
        // two subscribers are safe). Everything held in-memory here or in the singleton repos below
        // is account- or server-blind and must not survive the flip; Room-backed state re-filters
        // reactively via the active-account gate.
        viewModelScope.launch {
            activeAccountProvider.accountTransitions().collect { transition ->
                _activeJobIds.value = emptySet()
                _fetchedUnlisted.value = emptyMap()
                pullRequests.onAccountSwitched()
                unlistedInFlight.clear()
                // Filters name the outgoing account's bookmarks and endpoints.
                setFilters(DrawerFilters.None)
                // A share sheet open on the outgoing account's chat must not act on the new one.
                conversationActions.closeShareSheet()
                // Ticks still queued for the outgoing account's chat too: their legs would reach the
                // new account's server.
                bookmarkPicker.closeForAccountSwitch()
                favoriteWrites.cancelChildren()
                runRefreshes.cancelChildren()
                // A load started for the outgoing account would otherwise land after this reset.
                projectsLoadJob?.cancel()
                // An order not yet written for an account being signed out would outlive its purge.
                projectOrderSaveJob?.cancel()
                lastProjectsReload = null
                _projects.value = emptyList()
                closeExpandedProject()
                conversationListStateHolder.reset()
                // No tagRepository.clearCache() here: observeTags() is account-scoped, so the
                // outgoing account's tags never show, and RefreshTagsSessionTask (fired by the nav
                // shell's runAll on Switched) does an atomic replaceAllForAccount. A clearCache() in
                // this separate collector would race that write with no happens-before and could wipe
                // the freshly-fetched tags (they ran sequentially in one collector before the split).
                if (transition is AccountTransition.Switched) {
                    // The switch path never runs the login-side session machinery, so the incoming
                    // account's conversation list is fetched here. loadProjects() is NOT called here:
                    // it rides the version-gated collector above once the nav shell's re-detect
                    // re-emits detectedBackendVersion. Not refreshed on Ended (logout) — that would
                    // fire network calls with no active session.
                    conversationListStateHolder.refreshConversations()
                }
            }
        }
    }

    fun loadMoreConversations() {
        conversationListStateHolder.loadMoreConversations()
    }

    /**
     * Upstream `useReplyWatcher`: a chat that started or stopped running is re-read, so the cached
     * row takes the server's copy. Stopping is what stamps an unseen reply, and Finished and the dot
     * follow the stamp; starting catches a row archived elsewhere, which Running must not show.
     *
     * Only rows Room holds: an unlisted one would be inserted into the date groups. The open chat is
     * left to its own screen, whose seen write this read could land after and undo.
     */
    private fun refreshRunTransitions(changed: Set<String>) {
        if (changed.isEmpty()) return
        val cachedIds = conversationListStateHolder.recentConversations.value.mapNotNullTo(HashSet()) { it.conversationId }
        val openId = conversationListStateHolder.activeConversationId.value
        val account = activeAccountProvider.currentAccountId()
        for (id in changed) {
            if (id !in cachedIds || id == openId) continue
            viewModelScope.launch(runRefreshes) { conversationRepository.refreshConversation(id, originAccount = account) }
        }
    }

    /**
     * Fetches the unlisted running chats not yet held, one request per id at a time. Rows that
     * stopped running are dropped. Only while the list is unnarrowed, as they're shown only then.
     */
    private fun fetchUnlistedRunning() {
        val running = _activeJobIds.value
        _fetchedUnlisted.update { fetched -> fetched.filterKeys { it in running } }
        if (conversationListStateHolder.searchQuery.value.isNotBlank() || conversationListStateHolder.filters.value != null) return
        val missing = unlistedRunningIds(conversationListStateHolder.groupedConversations.value, running)
        val cachedIds = conversationListStateHolder.recentConversations.value.mapNotNullTo(HashSet()) { it.conversationId }
        for (id in missing) {
            if (id in cachedIds || id in _fetchedUnlisted.value || !unlistedInFlight.add(id)) continue
            viewModelScope.launch {
                val result = conversationRepository.fetchConversationUncached(id)
                unlistedInFlight.remove(id)
                if (result is Result.Success && id in _activeJobIds.value) {
                    _fetchedUnlisted.update { it + (id to result.data) }
                }
            }
        }
    }

    // --- List filters (v0.8.9-rc1) ---

    /** The sheet's Apply: [filters] replace the applied ones. Persisted until a cold start. */
    fun setFilters(filters: DrawerFilters) {
        _drawerFilters.value = filters
        savedStateHandle[FILTERS_KEY] = if (filters.isActive) {
            filtersJson.encodeToString(DrawerFilters.serializer(), filters)
        } else {
            null
        }
    }

    fun clearFilters() = setFilters(DrawerFilters.None)

    private fun restoreFilters(): DrawerFilters {
        val saved = savedStateHandle.get<String>(FILTERS_KEY) ?: return DrawerFilters.None
        return runCatching { filtersJson.decodeFromString(DrawerFilters.serializer(), saved) }
            .getOrDefault(DrawerFilters.None)
    }

    private suspend fun applyToList(applied: DrawerFilters?) {
        if (applied == null) {
            conversationListStateHolder.setFilters(null)
            return
        }
        val tags = if (applied.bookmarked && applied.tags.isEmpty()) {
            tagRepository.observeTags().first().filter { it.count > 0 }.mapNotNull { it.tag?.takeIf(String::isNotBlank) }
        } else {
            emptyList()
        }
        conversationListStateHolder.setFilters(
            applied.toListFilters(Clock.System.now(), TimeZone.currentSystemDefault(), tags),
        )
    }

    // --- Read state (v0.8.9-rc1) ---

    /**
     * The menu's "Mark as read": the same `/seen` the chat sends, naming the reply the row holds.
     * Failures say so; a newer reply landing meanwhile keeps the row unread.
     */
    fun markRead(conversationId: String) {
        if (!drawerUiState.value.readStateEnabled) return
        viewModelScope.launch {
            val row = findConversation(conversationId) ?: return@launch
            val stamp = row.lastResponseAt ?: return@launch
            val result = conversationRepository.markSeen(conversationId, stamp)
            if (result is Result.Error) {
                Logger.w(result.exception) { "Failed to mark conversation read" }
                _events.emit(ConversationListEvent.ShowErrorRes(Res.string.mark_read_failed))
            }
        }
    }

    fun markUnread(conversationId: String) {
        if (!drawerUiState.value.readStateEnabled) return
        viewModelScope.launch {
            val result = conversationRepository.markUnread(conversationId)
            if (result is Result.Error) {
                Logger.w(result.exception) { "Failed to mark conversation unread" }
                _events.emit(ConversationListEvent.ShowErrorRes(Res.string.mark_unread_failed))
            }
        }
    }

    private fun findConversation(id: String): Conversation? =
        conversationListStateHolder.recentConversations.value.firstOrNull { it.conversationId == id }
            ?: conversationListStateHolder.groupedConversations.value.firstNotNullOfOrNull { (_, rows) ->
                rows.firstOrNull { it.conversationId == id }
            }
            ?: _fetchedUnlisted.value[id]

    /**
     * Pull-to-refresh: refreshes the drawer conversation list + favorites + tags, and the shown rows'
     * pull request marks. The explicit tag refresh is wanted here because a manual refresh has no
     * session-task machinery behind it. Not while bookmarks are off: the tags route answers 403 to a
     * role without BOOKMARKS USE.
     */
    fun refreshConversations() {
        conversationListStateHolder.refreshConversations()
        pullRequests.refreshNow()
        // Read at the source, not from the drawer's WhileSubscribed state, which is permissive until collected.
        val enabled =
            bookmarksEnabled(roleRepository.userPermissions.value, configRepository.startupConfig.value?.interfaceConfig)
        if (enabled) viewModelScope.launch { tagRepository.refreshTags() }
    }

    /**
     * Fresh-login refresh (conversations + favorites only). accountTransitions() does not fire on
     * login-from-logged-out, so the nav host's onAuthComplete drives this. Tags/roles/favorites
     * session tasks already fired from AuthRepositoryImpl on the login success, so we must NOT
     * refresh tags here — that was the source of the double-fetch on fresh login.
     */
    fun refreshConversationsAfterLogin() {
        conversationListStateHolder.refreshConversations()
    }

    fun setActiveConversation(conversationId: String?) {
        conversationListStateHolder.setActiveConversation(conversationId)
    }

    fun onSearchQueryChanged(query: String) {
        conversationListStateHolder.onSearchQueryChanged(query)
    }

    /**
     * The star or the menu's Favorite on [conversationId]. [favorite] is what the user asked for (the
     * opposite of what the row showed), so a stale row or a repeat tap changes nothing.
     */
    fun setFavorite(conversationId: String, favorite: Boolean) {
        viewModelScope.launch(favoriteWrites) {
            val result = tagRepository.setConversationTag(conversationId, SAVED_TAG, attached = favorite)
            if (result is Result.Error) {
                Logger.w(result.exception) { "Failed to toggle favorite for $conversationId" }
                // Surface the failure like the row-action delegate does, instead of only logging —
                // otherwise a failed favorite from the drawer gives the user no feedback.
                _events.emit(ConversationListEvent.ShowErrorRes(Res.string.favorite_update_failed))
            }
        }
    }

    // --- Drawer conversation action menu (long-press). Row actions route through the shared
    // ConversationListActionsDelegate; favorite/pin stay local (different signatures), bookmarks
    // go through [bookmarkPicker]. ---

    fun renameConversation(id: String, newTitle: String) = conversationActions.renameConversation(id, newTitle)

    fun archiveConversation(id: String) = conversationActions.archiveConversation(id)

    fun pinConversation(id: String, pinned: Boolean) {
        viewModelScope.launch {
            val result = conversationRepository.pin(id, pinned)
            if (result is Result.Error) {
                Logger.e(result.exception) { "Failed to pin conversation" }
                _events.emit(ConversationListEvent.ShowError("Failed to pin conversation"))
            }
        }
    }

    /**
     * Toggles the inline accordion for [projectId] in the drawer's Projects tab. Expanding loads that
     * project's chats network-direct (single-expand: the previously open project collapses); tapping
     * the open project collapses it. [ChatProject.UNASSIGNED] is a valid id (loose chats).
     */
    fun toggleProjectExpanded(projectId: String) {
        if (_expandedProjectId.value == projectId) {
            closeExpandedProject()
            return
        }
        _expandedProjectId.value = projectId
        _expandedProjectChats.value = emptyList()
        _expandedProjectLoading.value = true
        loadExpandedChats(projectId)
    }

    /** Also run when a folder is picked up to drag, so every row is one line and the rows swap evenly. */
    fun closeExpandedProject() {
        expandedChatsJob?.cancel()
        _expandedProjectId.value = null
        _expandedProjectChats.value = emptyList()
        _expandedProjectLoading.value = false
    }

    /**
     * Loads the open folder's chats, latest wins. The spinner is cleared by whichever load finishes,
     * so a reload that replaces a folder's first load also ends that load's spinner.
     */
    private fun loadExpandedChats(projectId: String) {
        expandedChatsJob?.cancel()
        expandedChatsJob = viewModelScope.launch {
            val result = conversationRepository.getConversationsForProject(projectId = projectId)
            // The user may have collapsed or opened another folder while this was in flight.
            if (_expandedProjectId.value != projectId) return@launch
            when (result) {
                is Result.Success -> _expandedProjectChats.value = result.data.conversations
                is Result.Error -> Logger.w(result.exception) { "Failed to load project chats" }
                is Result.Loading -> Unit
            }
            _expandedProjectLoading.value = false
        }
    }

    /**
     * Reloads the folders and the open folder's chats, keeping the rows on screen meanwhile. Runs on
     * a project change (which includes a new chat filed into one) and when the Projects tab comes on
     * screen.
     */
    fun reloadProjects(force: Boolean = false) {
        if (!drawerUiState.value.projectsEnabled) return
        // The drawer opening and the panel sliding into view land within a moment of each other; one
        // fetch answers both. A project change is news, so it always reloads.
        val last = lastProjectsReload
        if (!force && last != null && last.elapsedNow() < PROJECTS_RELOAD_MERGE) return
        lastProjectsReload = TimeSource.Monotonic.markNow()
        loadProjects()
        _expandedProjectId.value?.let(::loadExpandedChats)
    }

    /** Loads the user's projects for the folder list and the move-to-project picker. */
    fun loadProjects() {
        // Latest wins: create-then-assign bumps the revision twice, and an older answer landing last
        // would leave a stale count.
        projectsLoadJob?.cancel()
        val pendingSave = projectOrderSaveJob
        projectsLoadJob = viewModelScope.launch {
            // Arranged against the saved order, so a drop still being written would be undone.
            pendingSave?.join()
            when (val result = projectRepository.listAllProjectsInCustomOrder()) {
                is Result.Success -> {
                    _projects.value = result.data.projects
                    // The open folder was deleted (here or on the Projects index): close it, which
                    // also drops the header's "New chat in" for it. Only a whole list can say a
                    // project is gone. Not in a project is never listed.
                    val expanded = _expandedProjectId.value
                    if (expanded != null && expanded != ChatProject.UNASSIGNED &&
                        result.data.isComplete &&
                        result.data.projects.none { it.id == expanded }
                    ) {
                        closeExpandedProject()
                    }
                }
                is Result.Error -> Logger.w(result.exception) { "Failed to load projects" }
                is Result.Loading -> Unit
            }
        }
    }

    /**
     * **Switchboard-only.** The user dropped a dragged folder: [orderedIds] is the drawer's new order.
     * Shown at once and saved on the device. A load in flight would be arranged against the old
     * order and land over the drop, so it is restarted behind the save instead.
     */
    fun reorderProjects(orderedIds: List<String>) {
        val current = _projects.value
        val byId = current.associateBy { it.id }
        val dropped = orderedIds.toSet()
        // A reload during the drag may have added a project the dragged list never had: it keeps
        // its place at the end rather than voiding the drop.
        val reordered = orderedIds.mapNotNull(byId::get) + current.filter { it.id !in dropped }
        if (reordered == current) return
        // The list on screen is this account's; the save must go to it even if it runs after a switch.
        val accountId = activeAccountProvider.currentAccountId() ?: return
        val loadWasRunning = projectsLoadJob?.isActive == true
        projectsLoadJob?.cancel()
        _projects.value = reordered
        val previousSave = projectOrderSaveJob
        projectOrderSaveJob = viewModelScope.launch {
            previousSave?.join()
            projectRepository.saveProjectOrder(accountId, reordered.map { it.id })
        }
        if (loadWasRunning) loadProjects()
    }

    /** Assigns [conversationId] to [projectId], or unassigns it when null. */
    fun moveConversationToProject(conversationId: String, projectId: String?) {
        viewModelScope.launch {
            when (val result = projectRepository.assignConversation(conversationId, projectId)) {
                is Result.Success -> Unit
                is Result.Error -> {
                    Logger.e(result.exception) { "Failed to move conversation to project" }
                    _events.emit(ConversationListEvent.ShowErrorRes(Res.string.project_move_failed))
                }
                is Result.Loading -> Unit
            }
        }
    }

    /** Creates a project named [name] and assigns [conversationId] to it. */
    fun createProjectAndAssign(conversationId: String, name: String) {
        viewModelScope.launch {
            when (val created = projectRepository.createProject(name)) {
                is Result.Success -> {
                    when (val assign = projectRepository.assignConversation(conversationId, created.data.id)) {
                        is Result.Error -> {
                            Logger.e(assign.exception) { "Failed to assign new project" }
                            _events.emit(ConversationListEvent.ShowErrorRes(Res.string.project_move_failed))
                        }
                        is Result.Success -> Unit
                        is Result.Loading -> Unit
                    }
                }
                is Result.Error -> {
                    Logger.e(created.exception) { "Failed to create project" }
                    _events.emit(ConversationListEvent.ShowErrorRes(Res.string.project_create_failed))
                }
                is Result.Loading -> Unit
            }
        }
    }

    fun createProject(name: String, description: String) = projectActions.create(name, description)

    fun editProject(projectId: String, name: String, description: String) =
        projectActions.edit(projectId, name, description)

    fun deleteProject(projectId: String) {
        // Collapse the inline accordion if the deleted project was open, so its stale chats clear.
        if (_expandedProjectId.value == projectId) closeExpandedProject()
        projectActions.delete(projectId)
    }

    fun deleteConversation(id: String) = conversationActions.deleteConversation(
        id = id,
        // See the delegate KDoc: isActive drives the navigate-off when the open chat is deleted.
        isActive = conversationListStateHolder.activeConversationId.value == id,
    )

    val shareLink: ShareLinkController get() = conversationActions.shareLink

    fun shareConversation(conversationId: String) = conversationActions.shareConversation(conversationId)

    fun closeShareSheet() = conversationActions.closeShareSheet()

    fun duplicateConversation(conversationId: String, title: String?) =
        conversationActions.duplicateConversation(conversationId, title)

    fun exportConversation(conversationId: String, title: String?, format: ExportFormat) =
        conversationActions.exportConversation(conversationId, title, format)

    /** Opens the bookmark checklist on [conversationId]: its bookmarks are read from the server first. */
    fun openBookmarks(conversationId: String) {
        if (!drawerUiState.value.bookmarksEnabled) return
        bookmarkPicker.open(conversationId)
    }
}

internal data class DrawerPermissionFlags(
    val agentsEnabled: Boolean = true,
    val marketplaceEnabled: Boolean = true,
    /** Fail-closed on the capability half, so false until the endpoint config arrives. */
    val skillsEnabled: Boolean = false,
    /** The permission half only. Scheduled chats need the server flag too — see the combine. */
    val schedulesPermitted: Boolean = true,
    val promptsEnabled: Boolean = true,
    val mcpServersEnabled: Boolean = true,
    val memoriesEnabled: Boolean = true,
)

/**
 * The role's drawer flags; a null role (still loading, failed, timed out) is permissive. Skills also
 * needs the agents endpoint's [agentsCapabilities] to include `skills` ([isSkillsNavEnabled]).
 */
internal fun drawerPermissionFlags(role: UserRolePermissions?, agentsCapabilities: List<String>?): DrawerPermissionFlags =
    DrawerPermissionFlags(
        agentsEnabled = role.hasAccessOrPermissive(PermissionType.AGENTS, Permission.USE),
        marketplaceEnabled = role.hasAccessOrPermissive(PermissionType.MARKETPLACE, Permission.USE),
        skillsEnabled = isSkillsNavEnabled(role, agentsCapabilities),
        schedulesPermitted = role.hasAccessOrPermissive(PermissionType.SCHEDULES, Permission.USE),
        promptsEnabled = role.hasAccessOrPermissive(PermissionType.PROMPTS, Permission.USE),
        mcpServersEnabled = role.hasAccessOrPermissive(PermissionType.MCP_SERVERS, Permission.USE),
        memoriesEnabled = role.canReachMemoriesSettings(),
    )
