package com.garfiec.librechat.core.notifications

import com.garfiec.librechat.core.data.datastore.AppPreferences
import com.garfiec.librechat.core.data.datastore.PreferenceStore
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.model.config.replyNotificationsAllowed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** A moment at which the app may ask for notifications. Stored by [key]: never rename one. */
enum class NotificationAskTrigger(val key: String) {
    /** A reply has been streaming long enough that the user may leave before it ends. */
    LongReply("long_reply"),
}

/**
 * Whether to offer turning notifications on at a [NotificationAskTrigger]: only when they are off,
 * the server allows them, the user wants reply notifications, and this trigger hasn't been answered.
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
        preferences.observe(AppPreferences.ReplyNotifications),
        preferences.observe(AppPreferences.NotificationAsksAnswered),
    ) { status, config, enabled, answered ->
        status != NotificationPermissionStatus.Allowed &&
            config != null && config.interfaceConfig.replyNotificationsAllowed &&
            enabled && trigger.key !in answered
    }

    suspend fun answered(trigger: NotificationAskTrigger) {
        preferences.update(AppPreferences.NotificationAsksAnswered) { answered ->
            if (trigger.key in answered) answered else answered + trigger.key
        }
    }
}
