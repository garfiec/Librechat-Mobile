package com.garfiec.librechat.core.notifications.di

import com.garfiec.librechat.core.common.di.KoinQualifiers
import com.garfiec.librechat.core.notifications.NotificationAsk
import com.garfiec.librechat.core.notifications.ReplyNotifications
import com.garfiec.librechat.core.notifications.ReplyReader
import com.garfiec.librechat.core.notifications.ReplyWatcher
import com.garfiec.librechat.core.notifications.ScheduledRunWatcher
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
    single { ReplyReader(conversationRepository = get(), messageRepository = get()) }
    single { ReplyWatcher(chatRepository = get(), replyReader = get(), accounts = get(), notifications = get()) }
    // Eager: it re-arms the next schedule check in any process, including one started for a check.
    single(createdAtStart = true) {
        ScheduledRunWatcher(
            scheduleRepository = get(),
            watchList = get(),
            configRepository = get(),
            accounts = get(),
            replyReader = get(),
            notifications = get(),
            scheduler = get(),
            foregroundSignal = get(),
            scope = get(KoinQualifiers.ApplicationScope),
        )
    }
}

/** Binds `PlatformNotifier`, `NotificationPermission`, `ReplyWatchScheduler` and `ScheduleCheckScheduler`. */
expect val notificationsPlatformModule: Module
