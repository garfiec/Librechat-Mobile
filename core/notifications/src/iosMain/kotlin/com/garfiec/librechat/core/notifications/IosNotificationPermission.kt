package com.garfiec.librechat.core.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.garfiec.librechat.core.common.lifecycle.ForegroundSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.compose.koinInject
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenNotificationSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

class IosNotificationPermission(
    foregroundSignal: ForegroundSignal,
    scope: CoroutineScope,
) : NotificationPermission {

    private val _status = MutableStateFlow(NotificationPermissionStatus.NotAsked)
    override val status: StateFlow<NotificationPermissionStatus> = _status.asStateFlow()

    init {
        refresh()
        foregroundSignal.isForeground.filter { it }.onEach { refresh() }.launchIn(scope)
    }

    override fun refresh() {
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            _status.value = when (settings?.authorizationStatus) {
                UNAuthorizationStatusAuthorized, UNAuthorizationStatusProvisional, UNAuthorizationStatusEphemeral ->
                    NotificationPermissionStatus.Allowed
                UNAuthorizationStatusNotDetermined -> NotificationPermissionStatus.NotAsked
                else -> NotificationPermissionStatus.Off
            }
        }
    }

    override fun openSystemSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenNotificationSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }

    /** iOS shows its prompt once per install; later calls answer from the stored decision. */
    suspend fun requestAuthorization(): Boolean = suspendCancellableCoroutine { continuation ->
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
        ) { granted, _ ->
            refresh()
            continuation.resume(granted)
        }
    }
}

/** Asks while iOS still will (status not determined), else opens the app's notification settings. */
@Composable
actual fun rememberNotificationPermissionRequest(onResult: (allowed: Boolean) -> Unit): () -> Unit {
    val permission = koinInject<NotificationPermission>()
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)
    return remember(permission, scope) {
        {
            when (permission.status.value) {
                NotificationPermissionStatus.Allowed -> currentOnResult(true)
                NotificationPermissionStatus.NotAsked -> scope.launch {
                    val granted = (permission as? IosNotificationPermission)?.requestAuthorization() ?: false
                    currentOnResult(granted)
                }
                NotificationPermissionStatus.Off -> {
                    permission.openSystemSettings()
                    currentOnResult(false)
                }
            }
        }
    }
}
