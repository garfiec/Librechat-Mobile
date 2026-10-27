package com.garfiec.librechat.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.settings.screen.AccountSettingsScreen
import com.garfiec.librechat.feature.settings.screen.ApiKeysScreen
import com.garfiec.librechat.feature.settings.screen.ChatSettingsScreen
import com.garfiec.librechat.feature.settings.screen.DataSettingsScreen
import com.garfiec.librechat.feature.settings.screen.GeneralSettingsScreen
import com.garfiec.librechat.feature.settings.screen.PresetManagerScreen
import com.garfiec.librechat.feature.settings.screen.SharedLinksScreen
import com.garfiec.librechat.feature.settings.screen.TabbedSettingsScreen
import com.garfiec.librechat.feature.settings.screen.providerkeys.ProviderKeysScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.jetbrains.compose.resources.StringResource

@Serializable sealed interface SettingsRoute : NavKey

@Serializable data object SettingsTabbed : SettingsRoute

@Serializable data object SettingsGeneral : SettingsRoute

@Serializable data object SettingsChat : SettingsRoute

@Serializable data object SettingsAccount : SettingsRoute

@Serializable data object SettingsData : SettingsRoute

@Serializable data object SharedLinks : SettingsRoute

@Serializable data object PresetManager : SettingsRoute

@Serializable data object ApiKeys : SettingsRoute

/**
 * Provider API Keys list screen route.
 *
 * @property pendingDialogEndpoint when non-null, the screen auto-opens the Set Key
 *   bottom-sheet dialog for this endpoint on first composition. Used by the chat
 *   model-selector "Set API Key" CTA and the chat-send `UserKeyError` snackbar to
 *   land the user directly on the right form instead of forcing a second tap.
 *   Null on the regular Settings → Provider API Keys path (TabbedSettings,
 *   AccountSettings) — list view is shown without auto-opening any dialog.
 * @property returnAfterSave the screen was opened from chat: a successful save or revoke goes back
 *   to the chat. False on the Settings path, which stays on the list.
 */
@Serializable data class ProviderKeys(
    val pendingDialogEndpoint: String? = null,
    val returnAfterSave: Boolean = false,
) : SettingsRoute

fun EntryProviderScope<NavKey>.settingsEntries(
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToArchive: () -> Unit = {},
    /** Opens a conversation in the chat: a Shared chats card's "Open chat". */
    onOpenConversation: (conversationId: String) -> Unit = {},
    /** Back to the chat that opened Provider Keys, with the save's or revoke's confirmation. */
    onProviderKeyReturn: (message: StringResource) -> Unit = { onBack() },
) {
    // Hoisted: navigation to ProviderKeys() (no pending endpoint) is identical in Tabbed
    // and Account — share the resolver so callers don't construct it twice.
    val navigateToProviderKeys: () -> Unit = { onNavigate(ProviderKeys()) }
    entry<SettingsTabbed> {
        TabbedSettingsScreen(
            onNavigateBack = onBack,
            onLogout = onLogout,
            onNavigateToAppearance = { onNavigate(AppearanceSettings) },
            onNavigateToArchive = onNavigateToArchive,
            onNavigateToSharedLinks = { onNavigate(SharedLinks) },
            onNavigateToArtifactShortcuts = { onNavigate(ArtifactShortcuts) },
            onNavigateToPrefetchSettings = { onNavigate(PrefetchSettings) },
            onNavigateToMemories = { onNavigate(Memories) },
            onNavigateToMcpServers = { onNavigate(McpServers) },
            onNavigateToPresets = { onNavigate(PresetManager) },
            onNavigateToContextUsage = { onNavigate(ContextUsageSettings) },
            onNavigateToChatLayout = { onNavigate(ChatLayoutSettings) },
            onNavigateToApiKeys = { onNavigate(ApiKeys) },
            onNavigateToFavorites = { onNavigate(Favorites) },
            onNavigateToProviderKeys = navigateToProviderKeys,
            onNavigateToRoleSkillsAdmin = { onNavigate(RoleSkillsAdmin) },
            onNavigateToRoleMemoriesAdmin = { onNavigate(RoleMemoriesAdmin) },
            onNavigateToWhatsNew = { onNavigate(WhatsNew) },
            onNavigateToReleaseNotes = { onNavigate(ReleaseNotes) },
        )
    }
    entry<SettingsGeneral> {
        GeneralSettingsScreen(
            onNavigateBack = onBack,
            onNavigateToAppearance = { onNavigate(AppearanceSettings) },
            onNavigateToWhatsNew = { onNavigate(WhatsNew) },
            onNavigateToReleaseNotes = { onNavigate(ReleaseNotes) },
        )
    }
    entry<SettingsChat> {
        ChatSettingsScreen(
            onNavigateBack = onBack,
            onNavigateToPresets = { onNavigate(PresetManager) },
            onNavigateToContextUsage = { onNavigate(ContextUsageSettings) },
            onNavigateToChatLayout = { onNavigate(ChatLayoutSettings) },
        )
    }
    entry<SettingsAccount> {
        AccountSettingsScreen(
            onLogout = onLogout,
            onNavigateBack = onBack,
            onNavigateToApiKeys = { onNavigate(ApiKeys) },
            onNavigateToFavorites = { onNavigate(Favorites) },
            onNavigateToProviderKeys = navigateToProviderKeys,
            onNavigateToRoleSkillsAdmin = { onNavigate(RoleSkillsAdmin) },
            onNavigateToRoleMemoriesAdmin = { onNavigate(RoleMemoriesAdmin) },
        )
    }
    entry<SettingsData> {
        DataSettingsScreen(
            onNavigateBack = onBack,
            onNavigateToArchive = onNavigateToArchive,
            onNavigateToSharedLinks = { onNavigate(SharedLinks) },
            onNavigateToArtifactShortcuts = { onNavigate(ArtifactShortcuts) },
            onNavigateToPrefetchSettings = { onNavigate(PrefetchSettings) },
            onNavigateToMemories = { onNavigate(Memories) },
            onNavigateToMcpServers = { onNavigate(McpServers) },
        )
    }
    entry<SharedLinks> {
        SharedLinksScreen(onNavigateBack = onBack, onOpenConversation = onOpenConversation)
    }
    entry<PresetManager> {
        PresetManagerScreen(
            onNavigateBack = onBack,
        )
    }
    entry<ApiKeys> {
        ApiKeysScreen(
            onNavigateBack = onBack,
        )
    }
    entry<ProviderKeys> { route ->
        ProviderKeysScreen(
            onNavigateBack = onBack,
            pendingDialogEndpoint = route.pendingDialogEndpoint,
            returnAfterSave = route.returnAfterSave,
            onReturnAfterSave = onProviderKeyReturn,
        )
    }
    favoritesEntry(onBack = onBack)
    roleSkillsAdminEntry(onBack = onBack)
    roleMemoriesAdminEntry(onBack = onBack)
    artifactShortcutsEntry(onBack = onBack)
    prefetchEntries(onNavigate = onNavigate, onBack = onBack)
    contextUsageSettingsEntry(onBack = onBack)
    chatLayoutSettingsEntry(onBack = onBack)
    appearanceSettingsEntry(onBack = onBack)
    whatsNewEntry(onBack = onBack)
    releaseNotesEntry(onBack = onBack)
}

val settingsSerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(SettingsTabbed::class, SettingsTabbed.serializer())
        subclass(SettingsGeneral::class, SettingsGeneral.serializer())
        subclass(SettingsChat::class, SettingsChat.serializer())
        subclass(SettingsAccount::class, SettingsAccount.serializer())
        subclass(SettingsData::class, SettingsData.serializer())
        subclass(SharedLinks::class, SharedLinks.serializer())
        subclass(PresetManager::class, PresetManager.serializer())
        subclass(ApiKeys::class, ApiKeys.serializer())
        subclass(ProviderKeys::class, ProviderKeys.serializer())
        subclass(Memories::class, Memories.serializer())
        subclass(McpServers::class, McpServers.serializer())
        subclass(McpServerEditor::class, McpServerEditor.serializer())
        subclass(Favorites::class, Favorites.serializer())
        subclass(RoleSkillsAdmin::class, RoleSkillsAdmin.serializer())
        subclass(RoleMemoriesAdmin::class, RoleMemoriesAdmin.serializer())
        subclass(ArtifactShortcuts::class, ArtifactShortcuts.serializer())
        subclass(PrefetchSettings::class, PrefetchSettings.serializer())
        subclass(PrefetchActivity::class, PrefetchActivity.serializer())
        subclass(ContextUsageSettings::class, ContextUsageSettings.serializer())
        subclass(ChatLayoutSettings::class, ChatLayoutSettings.serializer())
        subclass(AppearanceSettings::class, AppearanceSettings.serializer())
        subclass(WhatsNew::class, WhatsNew.serializer())
        subclass(ReleaseNotes::class, ReleaseNotes.serializer())
    }
}
