package com.garfiec.librechat.core.notifications

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.conversation.OpenConversationRegistry
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.common.lifecycle.ForegroundSignal
import com.garfiec.librechat.core.common.result.getOrNull
import com.garfiec.librechat.core.data.datastore.AppPreferences
import com.garfiec.librechat.core.data.datastore.PreferenceStore
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.model.config.replyNotificationSoundAllowed
import com.garfiec.librechat.core.model.config.replyNotificationsAllowed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold

/**
 * Announces replies the user wasn't watching, and keeps the posted ones honest: they are removed
 * when the active account changes (one could otherwise open under the wrong account), when their
 * chat is opened, and when the user turns Replies off. Created at Koin start, so those guards also
 * run in a process the OS started for a background watch, which has no UI.
 */
class ReplyNotifications(
    private val notifier: PlatformNotifier,
    private val permission: NotificationPermission,
    private val preferences: PreferenceStore,
    private val configRepository: ConfigRepository,
    private val accounts: ActiveAccountProvider,
    private val foregroundSignal: ForegroundSignal,
    private val openConversation: OpenConversationRegistry,
    scope: CoroutineScope,
) {
    private val _inApp = MutableSharedFlow<AppNotification>(extraBufferCapacity = 4)

    /** What to show as a snackbar: the app is in front, on another screen. */
    val inApp: SharedFlow<AppNotification> = _inApp.asSharedFlow()

    init {
        // Only a move away from a signed-in account: the boot transition out of Warming is often the
        // tap on one of these notifications starting the app.
        accounts.state
            .runningFold(null as AccountState? to null as AccountState?) { (_, last), next -> last to next }
            .onEach { (previous, current) ->
                val left = (previous as? AccountState.Resolved)?.id ?: return@onEach
                if ((current as? AccountState.Resolved)?.id != left) notifier.cancelAll()
            }
            .launchIn(scope)

        combine(foregroundSignal.isForeground, openConversation.openConversationId) { foreground, open ->
            open.takeIf { foreground }
        }
            .distinctUntilChanged()
            .onEach { open -> open?.let(notifier::cancel) }
            .launchIn(scope)

        preferences.observe(AppPreferences.ReplyNotifications)
            .filter { enabled -> !enabled }
            .onEach { notifier.cancelAll(NotificationTopic.Replies) }
            .launchIn(scope)
        preferences.observe(AppPreferences.ScheduledChatNotifications)
            .filter { enabled -> !enabled }
            .onEach { notifier.cancelAll(NotificationTopic.ScheduledChats) }
            .launchIn(scope)
    }

    suspend fun announce(notification: AppNotification) {
        permission.refresh()
        // A process started just for the watch has no config yet. Without one the server's gate is
        // unknown, and an operator's `false` must win.
        val config = configRepository.startupConfig.value ?: configRepository.fetchStartupConfig().getOrNull()
        val topic = when (notification) {
            is ReplyNotification -> NotificationTopic.Replies
            is ScheduledRunNotification -> NotificationTopic.ScheduledChats
        }
        val context = ReplyDeliveryContext(
            activeAccountId = accounts.currentAccountId(),
            foreground = foregroundSignal.isForeground.value,
            openConversationId = openConversation.openConversationId.value,
            enabled = preferences.observe(topic.toggle).first(),
            serverAllows = config != null && config.interfaceConfig.replyNotificationsAllowed,
            systemAllows = permission.allows(topic),
        )
        when (deliveryOf(notification, context)) {
            ReplyDelivery.System -> {
                val silent = !config?.interfaceConfig.replyNotificationSoundAllowed
                when (notification) {
                    is ReplyNotification -> notifier.post(
                        notification = notification,
                        link = conversationLink(notification.conversationId, notification.accountId),
                        silent = silent,
                    )
                    is ScheduledRunNotification -> notifier.post(
                        notification = notification,
                        link = notification.conversationId?.let { conversationLink(it, notification.accountId) }
                            ?: schedulesLink(notification.accountId),
                        silent = silent,
                    )
                }
            }
            ReplyDelivery.InApp -> _inApp.tryEmit(notification)
            ReplyDelivery.Drop -> Logger.d { "Notification not shown" }
        }
    }
}
