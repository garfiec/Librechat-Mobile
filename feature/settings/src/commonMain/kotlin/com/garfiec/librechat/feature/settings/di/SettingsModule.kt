package com.garfiec.librechat.feature.settings.di

import com.garfiec.librechat.core.common.di.KoinQualifiers
import com.garfiec.librechat.core.ui.media.AvatarImagePreparer
import com.garfiec.librechat.feature.settings.viewmodel.AccountSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.ApiKeysViewModel
import com.garfiec.librechat.feature.settings.viewmodel.AppearanceSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.ChatLayoutSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.ChatSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.ContextUsageSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.DataSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.FavoritesViewModel
import com.garfiec.librechat.feature.settings.viewmodel.GeneralSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.McpServerEditorViewModel
import com.garfiec.librechat.feature.settings.viewmodel.McpViewModel
import com.garfiec.librechat.feature.settings.viewmodel.MemoriesViewModel
import com.garfiec.librechat.feature.settings.viewmodel.PrefetchActivityViewModel
import com.garfiec.librechat.feature.settings.viewmodel.PrefetchSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.PresetManagerViewModel
import com.garfiec.librechat.feature.settings.viewmodel.ReleaseNotesViewModel
import com.garfiec.librechat.feature.settings.viewmodel.RoleMemoriesAdminViewModel
import com.garfiec.librechat.feature.settings.viewmodel.RoleSkillsAdminViewModel
import com.garfiec.librechat.feature.settings.viewmodel.ServerHeadersViewModel
import com.garfiec.librechat.feature.settings.viewmodel.SharedLinksViewModel
import com.garfiec.librechat.feature.settings.viewmodel.UpdateCheckViewModel
import com.garfiec.librechat.feature.settings.viewmodel.WhatsNewViewModel
import com.garfiec.librechat.feature.settings.viewmodel.providerkeys.ProviderKeysViewModel
import com.garfiec.librechat.feature.settings.viewmodel.providerkeys.SetProviderKeyViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

expect val settingsPlatformModule: Module

val settingsModule = module {
    includes(settingsPlatformModule)

    single { AvatarImagePreparer.Platform }

    // Explicit block (not viewModelOf) so the IO dispatcher can be resolved by
    // qualifier — constructor-DSL resolves CoroutineDispatcher by type only.
    viewModel {
        AccountSettingsViewModel(
            userRepository = get(),
            authRepository = get(),
            balanceRepository = get(),
            contentReader = get(),
            avatarImagePreparer = get(),
            configRepository = get(),
            serverDataStore = get(),
            keyRepository = get(),
            roleRepository = get(),
            ioDispatcher = get(KoinQualifiers.IO),
        )
    }
    viewModel {
        DataSettingsViewModel(
            cacheCleaner = get(),
            conversationRepository = get(),
            keyRepository = get(),
            diagnosticLogRepository = get(),
            mcpRepository = get(),
            memoryRepository = get(),
            roleRepository = get(),
            permissionGate = get(),
            configRepository = get(),
        )
    }
    viewModel {
        ChatSettingsViewModel(
            settingsDataStore = get(),
            preferenceStore = get(),
            speechSettingsFactory = get(),
        )
    }
    viewModelOf(::ApiKeysViewModel)
    viewModelOf(::AppearanceSettingsViewModel)
    viewModelOf(::ChatLayoutSettingsViewModel)
    viewModelOf(::ContextUsageSettingsViewModel)
    viewModelOf(::FavoritesViewModel)
    viewModelOf(::GeneralSettingsViewModel)
    viewModelOf(::MemoriesViewModel)
    viewModelOf(::McpViewModel)
    // viewModelOf can't read parametersOf: the editor takes the server it edits, or null to add one.
    viewModel { params -> McpServerEditorViewModel(get(), get(), params.getOrNull()) }
    viewModelOf(::PresetManagerViewModel)
    // Explicit block for the same reason as AccountSettingsViewModel: the IO dispatcher is resolved by
    // qualifier, which the constructor DSL cannot express.
    viewModel {
        PrefetchActivityViewModel(
            reporter = get(),
            controller = get(),
            cacheCleaner = get(),
            ioDispatcher = get(KoinQualifiers.IO),
        )
    }
    viewModelOf(::PrefetchSettingsViewModel)
    viewModelOf(::ProviderKeysViewModel)
    viewModelOf(::RoleMemoriesAdminViewModel)
    viewModelOf(::RoleSkillsAdminViewModel)
    viewModelOf(::ServerHeadersViewModel)
    viewModelOf(::SharedLinksViewModel)
    viewModelOf(::UpdateCheckViewModel)
    viewModelOf(::WhatsNewViewModel)
    viewModelOf(::ReleaseNotesViewModel)

    // viewModelOf has no overload that accepts ParametersHolder, so the runtime
    // endpointName parameter forces the lambda DSL. The SavedStateHandle comes from the
    // CreationExtras of the Nav3 entry's ViewModelStoreOwner (Koin intercepts the lookup).
    viewModel { params ->
        SetProviderKeyViewModel(
            endpointName = params.get(),
            keyRepository = get(),
            configRepository = get(),
            savedStateHandle = get(),
        )
    }
}
