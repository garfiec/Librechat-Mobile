package com.garfiec.librechat.feature.conversations.di

import android.app.Application
import android.content.Context
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.data.datastore.PreferenceStore
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.BookmarksGate
import com.garfiec.librechat.core.data.repository.ChatRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.MessageRepository
import com.garfiec.librechat.core.data.repository.ProjectRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.SearchCapabilityRepository
import com.garfiec.librechat.core.data.repository.SearchRepository
import com.garfiec.librechat.core.data.repository.TagRepository
import com.garfiec.librechat.core.data.search.MessageSearchRepository
import com.garfiec.librechat.core.data.share.ShareLinkControllerFactory
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.Test
import org.koin.test.verify.verify

class ConversationsModuleVerificationTest {
    @Test
    fun verifyConversationsModule() {
        conversationsModule.verify(
            extraTypes = listOf(
                Context::class,
                Application::class,
                ConversationRepository::class,
                ChatRepository::class,
                MessageRepository::class,
                AgentRepository::class,
                TagRepository::class,
                ShareLinkControllerFactory::class,
                SearchRepository::class,
                ConfigRepository::class,
                ProjectRepository::class,
                RoleRepository::class,
                ServerDataStore::class,
                CoroutineDispatcher::class,
                // DrawerViewModel deps sourced from other modules.
                ActiveAccountProvider::class,
                SettingsDataStore::class,
                MessageSearchRepository::class,
                PreferenceStore::class,
                SearchCapabilityRepository::class,
                // BookmarksViewModel's gate, from the data module.
                BookmarksGate::class,
            ),
        )
    }
}
