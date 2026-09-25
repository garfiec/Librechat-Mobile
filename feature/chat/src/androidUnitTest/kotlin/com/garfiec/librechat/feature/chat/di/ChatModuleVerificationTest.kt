package com.garfiec.librechat.feature.chat.di

import android.app.Application
import android.content.Context
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.network.ConnectivityObserver
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.ChatRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.DraftRepository
import com.garfiec.librechat.core.data.repository.EndpointTokenRepository
import com.garfiec.librechat.core.data.repository.FavoritesRepository
import com.garfiec.librechat.core.data.repository.FileRepository
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.data.repository.McpRepository
import com.garfiec.librechat.core.data.repository.MessageRepository
import com.garfiec.librechat.core.data.repository.PresetRepository
import com.garfiec.librechat.core.data.repository.PromptRepository
import com.garfiec.librechat.core.data.repository.QueuedTurnRepository
import com.garfiec.librechat.core.data.repository.ResumePinStore
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.ShareRepository
import com.garfiec.librechat.core.data.repository.SpeechRepository
import com.garfiec.librechat.core.data.repository.SubagentRepository
import com.garfiec.librechat.core.data.repository.TraceRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.data.util.PermissionGate
import com.garfiec.librechat.feature.chat.prompts.PromptsViewModel
import com.garfiec.librechat.feature.chat.viewmodel.SubagentThreadsViewModel
import com.garfiec.librechat.feature.chat.viewmodel.TraceViewerViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import io.mockk.mockk
import kotlin.test.assertTrue
import org.junit.Test
import org.koin.core.Koin
import org.koin.core.error.InstanceCreationException
import org.koin.core.error.NoDefinitionFoundException
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify

class ChatModuleVerificationTest {
    @Test
    fun verifyChatModule() {
        chatModule.verify(
            extraTypes = listOf(
                Context::class,
                Application::class,
                AgentRepository::class,
                ChatRepository::class,
                MessageRepository::class,
                ConfigRepository::class,
                ConversationRepository::class,
                DraftRepository::class,
                EndpointTokenRepository::class,
                FavoritesRepository::class,
                FileRepository::class,
                KeyRepository::class,
                PresetRepository::class,
                ResumePinStore::class,
                PromptRepository::class,
                QueuedTurnRepository::class,
                RoleRepository::class,
                PermissionGate::class,
                ShareRepository::class,
                SubagentRepository::class,
                TraceRepository::class,
                SpeechRepository::class,
                McpRepository::class,
                UserRepository::class,
                ConnectivityObserver::class,
                ActiveAccountProvider::class,
                ServerDataStore::class,
                SettingsDataStore::class,
                // Dispatchers are supplied via Koin qualifiers in explicit blocks; verify()
                // can't see qualifiers, so whitelist the type as externally provided.
                CoroutineDispatcher::class,
                // Provided by core:network NetworkModule (SSE/tool-call JSON parsing).
                Json::class,
            ),
        )
    }

    @Test
    fun everyViewModelTheScreensResolveByTypeIsRegistered() {
        // `verify()` above checks that REGISTERED definitions have satisfiable dependencies. It
        // cannot walk `koinViewModel<T>()` call sites, so a ViewModel nobody registered is
        // invisible to it — mis-wired is caught, unregistered is not, and the latter throws
        // NoDefinitionFoundException on the first tap with every gate green.
        val koin = koinApplication {
            modules(
                chatModule,
                module {
                    single<PromptRepository> { mockk(relaxed = true) }
                    single<SubagentRepository> { mockk(relaxed = true) }
                    single<TraceRepository> { mockk(relaxed = true) }
                },
            )
        }.koin

        assertTrue(koin.hasDefinitionFor { koin.get<PromptsViewModel>() }, "PromptsViewModel")
        assertTrue(koin.hasDefinitionFor { koin.get<SubagentThreadsViewModel>() }, "SubagentThreadsViewModel")
        assertTrue(koin.hasDefinitionFor { koin.get<TraceViewerViewModel>() }, "TraceViewerViewModel")
    }

    /**
     * Whether a definition exists, as opposed to whether it can be BUILT here.
     *
     * A ViewModel that reaches its own constructor has a definition; failing afterwards is a
     * JVM-test problem (no Main dispatcher, no Android framework) and not what this asks about.
     * Only the missing definition is the bug this test exists for, and that one is named
     * precisely.
     */
    private fun Koin.hasDefinitionFor(resolve: () -> Any?): Boolean =
        try {
            resolve()
            true
        } catch (_: NoDefinitionFoundException) {
            false
        } catch (_: InstanceCreationException) {
            true
        }
}
