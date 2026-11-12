package com.garfiec.librechat

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.identity.ShareLinkMatcher
import com.garfiec.librechat.core.common.network.ConnectivityObserver
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.model.SHORTCUT_CANDIDATE_FACTOR
import com.garfiec.librechat.core.model.offeredSpecs
import com.garfiec.librechat.core.model.reconciledWith
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.feature.chat.ShareIntentConsumer
import com.garfiec.librechat.feature.chat.SharedContent
import com.garfiec.librechat.feature.chat.components.artifact.artifactAppIntent
import com.garfiec.librechat.feature.chat.components.artifact.retargetPinnedArtifactShortcuts
import com.garfiec.librechat.navigation.LibreChatNavHost
import com.garfiec.librechat.navigation.toDeepLinkUri
import com.garfiec.librechat.shared.navigation.AppShortcuts
import com.garfiec.librechat.shared.navigation.DeepLinkInbox
import com.garfiec.librechat.shared.navigation.DeepLinkResolution
import com.garfiec.librechat.shared.navigation.DeepLinks
import com.garfiec.librechat.shortcuts.ModelShortcuts
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private const val KEY_PENDING_SHARED_LINK = "pending_shared_link"

private enum class IntentOutcome { Handled, ArtifactApp }

class MainActivity : ComponentActivity() {

    private val connectivityObserver: ConnectivityObserver by inject()
    private val settingsDataStore: SettingsDataStore by inject()
    private val configRepository: ConfigRepository by inject()
    private val deepLinkInbox: DeepLinkInbox by inject()

    /** Shared-in text that is exactly one `/share/` link, waiting for the nav host to route it. */
    private var sharedLinkText by mutableStateOf<String?>(null)

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Only process the launch intent on a genuinely fresh start. On recreation (a theme or
        // locale change, a restore) it is still sticky; re-processing would re-fire the deep link
        // and yank the user back to it. A link not yet placed survives a recreation in DeepLinkInbox.
        // Restore a shared-in link the nav host hadn't consumed (see onSaveInstanceState).
        if (savedInstanceState == null) {
            retargetPinnedArtifactShortcuts(this)
            // Started only to hand a pinned artifact to its own app: there is no Switchboard to show.
            if (handleIntent(intent) == IntentOutcome.ArtifactApp) {
                finishAndRemoveTask()
                return
            }
        } else {
            sharedLinkText = savedInstanceState.getString(KEY_PENDING_SHARED_LINK)
        }

        // Keep home-screen model shortcuts in sync with the account's most-used models. The flow
        // emits an empty list once the account resolves logged-out, which clears the shortcuts.
        // Spec entries are checked against the server's current specs, so a removed spec loses its
        // shortcut and a renamed one its old label; extra candidates refill the dropped slots.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    settingsDataStore.topUsedModels(ModelShortcuts.maxCount * SHORTCUT_CANDIDATE_FACTOR),
                    configRepository.startupConfig,
                ) { models, config -> models.reconciledWith(config.offeredSpecs).take(ModelShortcuts.maxCount) }
                    // The backing flows re-emit on every settings write and config refresh; only republish when
                    // the list actually changes to avoid redundant main-thread setDynamicShortcuts IPC.
                    .distinctUntilChanged()
                    .collect { models ->
                        ModelShortcuts.publish(this@MainActivity, models)
                    }
            }
        }

        setContent {
            val isConnected by connectivityObserver.isConnected.collectAsStateWithLifecycle(initialValue = true)
            SwitchboardTheme(window) { appLocale ->
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .clearFocusOnTap()
                        .semantics { testTagsAsResourceId = true },
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        AnimatedVisibility(
                            visible = !isConnected,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .windowInsetsPadding(WindowInsets.statusBars)
                                    .padding(vertical = 6.dp, horizontal = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.no_connection),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                        LibreChatNavHost(
                            sharedLinkText = sharedLinkText,
                            onSharedLinkConsume = { sharedLinkText = null },
                            appLocaleTag = appLocale,
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (!isConnected) {
                                        Modifier.consumeWindowInsets(WindowInsets.statusBars)
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Preserve a shared-in link the nav host hasn't consumed yet, so a recreation during the
        // warm-up gate doesn't drop it (restored in onCreate). A consumed link is already null.
        sharedLinkText?.let { outState.putString(KEY_PENDING_SHARED_LINK, it) }
    }

    // Ctrl/⌘+Shift+F is caught here, before Compose, which misses it while nothing holds focus (see AppShortcuts).
    // RestrictedApi: overriding dispatchKeyEvent is public Activity API; lint resolves the super call to
    // androidx.core's ComponentActivity override, which is marked library-group only.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val searchShortcut = event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
            event.keyCode == KeyEvent.KEYCODE_F && event.isShiftPressed && (event.isCtrlPressed || event.isMetaPressed)
        if (searchShortcut && AppShortcuts.openSearch?.invoke() == true) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Keep getIntent() pointing at the latest intent so a later recreation doesn't re-read a stale one.
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?): IntentOutcome {
        if (intent == null) return IntentOutcome.Handled

        when {
            intent.action == Intent.ACTION_SEND -> handleShareIntent(intent)
            intent.action == Intent.ACTION_SEND_MULTIPLE -> handleShareMultipleIntent(intent)
            intent.action == Intent.ACTION_VIEW && intent.data?.scheme == ContentResolver.SCHEME_CONTENT ->
                handleOpenWithIntent(intent)
            else -> return handleDeepLink(intent)
        }
        return IntentOutcome.Handled
    }

    private fun handleOpenWithIntent(intent: Intent) {
        val uri = intent.data ?: return
        Logger.d { "Open-with intent received" }
        ShareIntentConsumer.setPendingShare(SharedContent(fileUris = listOf(uri)))
    }

    private fun handleDeepLink(intent: Intent): IntentOutcome {
        val uri = intent.data?.takeIf { it.scheme == DeepLinks.SCHEME } ?: return IntentOutcome.Handled
        // Same resolver the nav host uses — the accept decision and the routing decision can't
        // drift because they're one source of truth. Anything it doesn't route is dropped here.
        val link = uri.toDeepLinkUri()
        when (val resolution = DeepLinks.resolve(link)) {
            DeepLinkResolution.None -> Logger.w { "Ignoring unhandled deep link: $uri" }
            // Icons pinned by older builds still target this activity.
            is DeepLinkResolution.ArtifactApp -> {
                startActivity(artifactAppIntent(this, resolution.snapshotId))
                return IntentOutcome.ArtifactApp
            }
            else -> deepLinkInbox.post(link)
        }
        return IntentOutcome.Handled
    }

    private fun handleShareIntent(intent: Intent) {
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)

        @Suppress("DEPRECATION")
        val sharedUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

        val fileUris = if (sharedUri != null) listOf(sharedUri) else emptyList()

        // Text that is exactly one shared-chat link goes to the nav host: only it can tell whether a
        // signed-in account is on that server, and it sends anything it can't open to the composer.
        if (sharedText != null && fileUris.isEmpty() && ShareLinkMatcher.parse(sharedText) != null) {
            Logger.d { "Share intent received: a shared-chat link" }
            sharedLinkText = sharedText
            return
        }

        if (sharedText != null || fileUris.isNotEmpty()) {
            Logger.d { "Share intent received: text=${sharedText != null}, uris=${fileUris.size}" }
            // Staged only — the nav host addresses it to whichever chat is on screen.
            ShareIntentConsumer.setPendingShare(
                SharedContent(text = sharedText, fileUris = fileUris),
            )
        }
    }

    private fun handleShareMultipleIntent(intent: Intent) {
        @Suppress("DEPRECATION")
        val sharedUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }

        if (!sharedUris.isNullOrEmpty()) {
            Logger.d { "Share multiple intent received: uris=${sharedUris.size}" }
            ShareIntentConsumer.setPendingShare(
                SharedContent(fileUris = sharedUris),
            )
        }
    }
}
