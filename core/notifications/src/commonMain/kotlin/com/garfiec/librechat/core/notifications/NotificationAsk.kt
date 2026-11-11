package com.garfiec.librechat.core.notifications

import com.garfiec.librechat.core.data.datastore.AppPreferences
import com.garfiec.librechat.core.data.datastore.PreferenceStore
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.model.config.replyNotificationsAllowed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** A moment at which the app may ask for notifications. Stored by [key]: never rename one. */
enum class NotificationAskTrigger(val key: String, internal val topic: NotificationTopic) {
    /** A reply has been streaming long enough that the user may leave before it ends. */
    LongReply("long_reply", NotificationTopic.Replies),

    /** The Schedules screen opened: its runs happen while the app is closed. */
    SchedulesOpened("schedules_opened", NotificationTopic.ScheduledChats),
}

/**
 * Whether to offer turning notifications on at a [NotificationAskTrigger]: only when its kind can't show,
 * the server allows them, the user wants that trigger's kind, and this trigger hasn't been answered.
 * Each trigger asks once, whatever the answer, and nothing asks at launch: a feature calls
 * [shouldOffer] at its moment.
 */
class NotificationAsk(
    private val preferences: PreferenceStore,
    private val permission: NotificationPermission,
    private val configRepository: ConfigRepository,
) {
    fun shouldOffer(trigger: NotificationAskTrigger): Flow<Boolean> = combine(
        permission.status,
        configRepository.startupConfig,
        preferences.observe(trigger.topic.toggle),
        preferences.observe(AppPreferences.NotificationAsksAnswered),
    ) { _, config, enabled, answered ->
        !permission.allows(trigger.topic) &&
            config != null && config.interfaceConfig.replyNotificationsAllowed &&
            enabled && trigger.key !in answered
    }

    suspend fun answered(trigger: NotificationAskTrigger) {
        preferences.update(AppPreferences.NotificationAsksAnswered) { answered ->
            if (trigger.key in answered) answered else answered + trigger.key
        }
    }
}
