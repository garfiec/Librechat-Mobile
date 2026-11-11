package com.garfiec.librechat.core.notifications

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.app.NotificationManagerCompat
import com.garfiec.librechat.core.common.lifecycle.ForegroundSignal
import com.garfiec.librechat.core.data.datastore.AppPreferences
import com.garfiec.librechat.core.data.datastore.PreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Recorded in [AppPreferences.NotificationAsksAnswered] once Android's own prompt has been shown. */
private const val SYSTEM_PROMPT_SHOWN = "system_prompt"

class AndroidNotificationPermission(
    private val context: Context,
    private val preferences: PreferenceStore,
    foregroundSignal: ForegroundSignal,
    scope: CoroutineScope,
) : NotificationPermission {

    private val manager = NotificationManagerCompat.from(context)

    @Volatile
    private var promptShown = false

    // First read comes from the preference collector below, keeping binder calls out of Koin startup.
    private val _status = MutableStateFlow(NotificationPermissionStatus.NotAsked)
    override val status: StateFlow<NotificationPermissionStatus> = _status.asStateFlow()

    init {
        preferences.observe(AppPreferences.NotificationAsksAnswered)
            .onEach { answered ->
                promptShown = SYSTEM_PROMPT_SHOWN in answered
                refresh()
            }
            .launchIn(scope)
        foregroundSignal.isForeground.filter { it }.onEach { refresh() }.launchIn(scope)
    }

    override fun refresh() {
        _status.value = read()
    }

    // Not from [status]: a channel appears on its first post, so with Replies off and no Scheduled
    // channel yet, [status] reads Off while scheduled chats can still show.
    override fun allows(topic: NotificationTopic): Boolean = manager.areNotificationsEnabled() && !blocked(topic)

    /** A channel exists once something posted to it; a missing one is not off. */
    private fun blocked(topic: NotificationTopic): Boolean =
        manager.getNotificationChannelCompat(topic.channelId)?.importance == NotificationManagerCompat.IMPORTANCE_NONE

    /** Off when every topic's channel the user has seen is turned off, not just one of them. */
    private fun read(): NotificationPermissionStatus {
        val created = NotificationTopic.entries.filter { manager.getNotificationChannelCompat(it.channelId) != null }
        val allBlocked = created.isNotEmpty() && created.all(::blocked)
        return when {
            manager.areNotificationsEnabled() && !allBlocked -> NotificationPermissionStatus.Allowed
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !promptShown && !allBlocked ->
                NotificationPermissionStatus.NotAsked
            else -> NotificationPermissionStatus.Off
        }
    }

    /** The turned-off channel's page when notifications are on but one channel is off, else the app's page. */
    override fun openSystemSettings() {
        val offChannel = NotificationTopic.entries.filter(::blocked).singleOrNull()
            ?.takeIf { manager.areNotificationsEnabled() }
        val intent = if (offChannel != null) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_CHANNEL_ID, offChannel.channelId)
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        }
        context.startActivity(
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    internal suspend fun markPromptShown() {
        promptShown = true
        preferences.update(AppPreferences.NotificationAsksAnswered) { answered ->
            if (SYSTEM_PROMPT_SHOWN in answered) answered else answered + SYSTEM_PROMPT_SHOWN
        }
    }
}

/**
 * Android 13+ shows the system prompt while it still will: never shown, or denied once (the system
 * then offers it again, which `shouldShowRequestPermissionRationale` reports). After that, and below
 * 13 where there is no prompt, only the system settings can turn notifications on.
 */
@Composable
actual fun rememberNotificationPermissionRequest(onResult: (allowed: Boolean) -> Unit): () -> Unit {
    val permission = koinInject<NotificationPermission>()
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permission.refresh()
        currentOnResult(permission.status.value == NotificationPermissionStatus.Allowed)
    }
    return remember(permission, activity, launcher, scope) {
        {
            permission.refresh()
            val status = permission.status.value
            val systemWillPrompt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && (
                status == NotificationPermissionStatus.NotAsked ||
                    activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
                )
            when {
                // Allowed with one topic's channel off: only that channel's page can turn it on.
                status == NotificationPermissionStatus.Allowed && NotificationTopic.entries.all(permission::allows) ->
                    currentOnResult(true)
                systemWillPrompt -> {
                    scope.launch { (permission as? AndroidNotificationPermission)?.markPromptShown() }
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    permission.openSystemSettings()
                    currentOnResult(false)
                }
            }
        }
    }
}
