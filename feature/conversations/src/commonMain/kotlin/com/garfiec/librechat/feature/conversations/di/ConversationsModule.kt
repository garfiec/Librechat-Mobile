package com.garfiec.librechat.feature.conversations.di

import com.garfiec.librechat.core.common.di.KoinQualifiers
import com.garfiec.librechat.feature.conversations.drawer.DrawerPreferences
import com.garfiec.librechat.feature.conversations.drawer.DrawerSearchSources
import com.garfiec.librechat.feature.conversations.drawer.DrawerViewModel
import com.garfiec.librechat.feature.conversations.drawer.WorkspacePinsViewModel
import com.garfiec.librechat.feature.conversations.export.ConversationExporter
import com.garfiec.librechat.feature.conversations.export.ConversationImporter
import com.garfiec.librechat.feature.conversations.search.SearchViewModel
import com.garfiec.librechat.feature.conversations.viewmodel.ArchivedConversationsViewModel
import com.garfiec.librechat.feature.conversations.viewmodel.BookmarkChatsViewModel
import com.garfiec.librechat.feature.conversations.viewmodel.BookmarksViewModel
import com.garfiec.librechat.feature.conversations.viewmodel.ProjectChatsViewModel
import com.garfiec.librechat.feature.conversations.viewmodel.ProjectsViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val conversationsModule = module {
    single {
        ConversationExporter(
            conversationRepository = get(),
            messageRepository = get(),
            ioDispatcher = get(KoinQualifiers.IO),
        )
    }
    single {
        ConversationImporter(
            ioDispatcher = get(KoinQualifiers.IO),
        )
    }

    viewModelOf(::ArchivedConversationsViewModel)
    viewModelOf(::ProjectsViewModel)
    viewModelOf(::BookmarksViewModel)
    // Drawer-data half of the nav shell's NavHostViewModel.
    factoryOf(::DrawerSearchSources)
    factoryOf(::DrawerPreferences)
    viewModelOf(::DrawerViewModel)
    viewModelOf(::WorkspacePinsViewModel)
    // The Search screen's initial query arrives from the navigation layer via parametersOf.
    viewModel { params ->
        SearchViewModel(
            initialQuery = params.get(),
            sources = get(),
            conversationRepository = get(),
            messageRepository = get(),
            agentRepository = get(),
            activeAccountProvider = get(),
            defaultDispatcher = get(KoinQualifiers.Default),
            preferenceStore = get(),
        )
    }
    // projectId arrives from the navigation layer via parametersOf.
    viewModel { params ->
        ProjectChatsViewModel(
            projectId = params.get(),
            conversationRepository = get(),
            tagRepository = get(),
            shareLinkControllerFactory = get(),
            conversationExporter = get(),
            roleRepository = get(),
            configRepository = get(),
            projectRepository = get(),
        )
    }
    // The bookmark's name arrives from the navigation layer via parametersOf.
    viewModel { params ->
        BookmarkChatsViewModel(
            tag = params.get(),
            conversationRepository = get(),
            tagRepository = get(),
            shareLinkControllerFactory = get(),
            conversationExporter = get(),
            bookmarksGate = get(),
            roleRepository = get(),
            configRepository = get(),
        )
    }
}
