package com.garfiec.librechat.core.notifications.di

import com.garfiec.librechat.core.common.di.KoinQualifiers
import com.garfiec.librechat.core.notifications.NotificationAsk
import com.garfiec.librechat.core.notifications.ReplyNotifications
import com.garfiec.librechat.core.notifications.ReplyWatcher
import org.koin.core.module.Module
import org.koin.dsl.module

val notificationsModule = module {
    includes(notificationsPlatformModule)
    // Eager: its guards clear stale notifications in a process started only for a background watch.
    single(createdAtStart = true) {
        ReplyNotifications(
            notifier = get(),
            permission = get(),
            preferences = get(),
            configRepository = get(),
            accounts = get(),
            foregroundSignal = get(),
            openConversation = get(),
            scope = get(KoinQualifiers.ApplicationScope),
        )
    }
    single { NotificationAsk(preferences = get(), permission = get(), configRepository = get()) }
    single {
        ReplyWatcher(
            chatRepository = get(),
            conversationRepository = get(),
            messageRepository = get(),
            accounts = get(),
            notifications = get(),
        )
    }
}

/** Binds `PlatformNotifier`, `NotificationPermission` and `ReplyWatchScheduler`. */
expect val notificationsPlatformModule: Module
