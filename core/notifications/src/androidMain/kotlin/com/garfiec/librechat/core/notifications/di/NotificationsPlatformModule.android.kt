package com.garfiec.librechat.core.notifications.di

import com.garfiec.librechat.core.common.di.KoinQualifiers
import com.garfiec.librechat.core.notifications.AndroidNotificationPermission
import com.garfiec.librechat.core.notifications.AndroidNotifier
import com.garfiec.librechat.core.notifications.NotificationPermission
import com.garfiec.librechat.core.notifications.PlatformNotifier
import com.garfiec.librechat.core.notifications.ReplyWatchScheduler
import com.garfiec.librechat.core.notifications.ScheduleCheckScheduler
import com.garfiec.librechat.core.notifications.WorkManagerReplyWatchScheduler
import com.garfiec.librechat.core.notifications.WorkManagerScheduleCheckScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val notificationsPlatformModule: Module = module {
    single<PlatformNotifier> { AndroidNotifier(androidContext(), settingsDataStore = get()) }
    single<NotificationPermission> {
        AndroidNotificationPermission(
            context = androidContext(),
            preferences = get(),
            foregroundSignal = get(),
            scope = get(KoinQualifiers.ApplicationScope),
        )
    }
    single<ReplyWatchScheduler> { WorkManagerReplyWatchScheduler(androidContext()) }
    single<ScheduleCheckScheduler> { WorkManagerScheduleCheckScheduler(androidContext()) }
}
