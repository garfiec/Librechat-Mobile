package com.garfiec.librechat.core.notifications.di

import com.garfiec.librechat.core.common.di.KoinQualifiers
import com.garfiec.librechat.core.notifications.IosNotificationPermission
import com.garfiec.librechat.core.notifications.IosNotifier
import com.garfiec.librechat.core.notifications.IosReplyWatchScheduler
import com.garfiec.librechat.core.notifications.IosScheduleCheckScheduler
import com.garfiec.librechat.core.notifications.NotificationPermission
import com.garfiec.librechat.core.notifications.PlatformNotifier
import com.garfiec.librechat.core.notifications.ReplyWatchScheduler
import com.garfiec.librechat.core.notifications.ScheduleCheckScheduler
import org.koin.core.module.Module
import org.koin.dsl.module

actual val notificationsPlatformModule: Module = module {
    single<PlatformNotifier> { IosNotifier() }
    single<NotificationPermission> {
        IosNotificationPermission(foregroundSignal = get(), scope = get(KoinQualifiers.ApplicationScope))
    }
    single<ReplyWatchScheduler> {
        IosReplyWatchScheduler(watcher = get(), scope = get(KoinQualifiers.ApplicationScope))
    }
    single<ScheduleCheckScheduler> { IosScheduleCheckScheduler() }
}
