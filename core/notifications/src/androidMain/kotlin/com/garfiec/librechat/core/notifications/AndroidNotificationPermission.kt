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

    private fun read(): NotificationPermissionStatus {
        val repliesBlocked = manager.getNotificationChannelCompat(REPLIES_CHANNEL_ID)
            ?.importance == NotificationManagerCompat.IMPORTANCE_NONE
        return when {
            manager.areNotificationsEnabled() && !repliesBlocked -> NotificationPermissionStatus.Allowed
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !promptShown && !repliesBlocked ->
                NotificationPermissionStatus.NotAsked
            else -> NotificationPermissionStatus.Off
        }
    }

    /** The Replies channel's page when only it is off, else the app's notification page. */
    override fun openSystemSettings() {
        val channelOnly = manager.areNotificationsEnabled() &&
            manager.getNotificationChannelCompat(REPLIES_CHANNEL_ID) != null
        val intent = if (channelOnly) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_CHANNEL_ID, REPLIES_CHANNEL_ID)
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
                status == NotificationPermissionStatus.Allowed -> currentOnResult(true)
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
