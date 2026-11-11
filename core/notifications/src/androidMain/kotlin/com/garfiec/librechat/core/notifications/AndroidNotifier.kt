package com.garfiec.librechat.core.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.notifications.resources.Res
import com.garfiec.librechat.core.notifications.resources.channel_replies
import com.garfiec.librechat.core.notifications.resources.channel_replies_description
import com.garfiec.librechat.core.notifications.resources.channel_scheduled
import com.garfiec.librechat.core.notifications.resources.channel_scheduled_description
import com.garfiec.librechat.core.notifications.resources.reply_public_body
import com.garfiec.librechat.core.notifications.resources.scheduled_public_body
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import java.util.Locale

private const val REPLIES_CHANNEL_ID = "replies"
private const val SCHEDULED_CHANNEL_ID = "scheduled"
private const val NOTIFICATION_ID = 1

/** Each topic's channel and tag prefix. A posted notification is `notify(<prefix><key>, 1)`. */
private enum class Channel(
    val topic: NotificationTopic,
    val id: String,
    val tagPrefix: String,
    val nameRes: StringResource,
    val descriptionRes: StringResource,
    val publicBodyRes: StringResource,
) {
    Replies(
        NotificationTopic.Replies, REPLIES_CHANNEL_ID, "reply:",
        Res.string.channel_replies, Res.string.channel_replies_description, Res.string.reply_public_body,
    ),
    Scheduled(
        NotificationTopic.ScheduledChats, SCHEDULED_CHANNEL_ID, "scheduled:",
        Res.string.channel_scheduled, Res.string.channel_scheduled_description, Res.string.scheduled_public_body,
    ),
}

internal val NotificationTopic.channelId: String
    get() = Channel.entries.first { it.topic == this }.id

class AndroidNotifier(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore,
) : PlatformNotifier {

    private val manager = NotificationManagerCompat.from(context)

    override suspend fun post(notification: ReplyNotification, link: String, silent: Boolean) =
        post(Channel.Replies, notification.conversationId, notification, link, silent)

    override suspend fun post(notification: ScheduledRunNotification, link: String, silent: Boolean) =
        post(Channel.Scheduled, notification.key, notification, link, silent)

    // Checks areNotificationsEnabled first; on 13+ that is false without POST_NOTIFICATIONS.
    @SuppressLint("MissingPermission")
    private suspend fun post(channel: Channel, key: String, notification: AppNotification, link: String, silent: Boolean) {
        ensureChannel(channel)
        if (!manager.areNotificationsEnabled()) return
        val body = notification.bodyText()
        // Explicit to this app; the data differs per link, so each gets its own PendingIntent.
        val open = Intent(Intent.ACTION_VIEW, link.toUri()).setPackage(context.packageName)
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // What a locked screen shows when the user hides sensitive content: no title, no text.
        val publicVersion = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_stat_reply)
            .setContentText(getString(channel.publicBodyRes))
            .build()
        val built = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_stat_reply)
            .setContentTitle(notification.titleText())
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setSilent(silent)
            .build()
        manager.notify(channel.tagPrefix + key, NOTIFICATION_ID, built)
    }

    override fun cancel(conversationId: String) {
        Channel.entries.forEach { manager.cancel(it.tagPrefix + conversationId, NOTIFICATION_ID) }
    }

    override fun cancelAll(topic: NotificationTopic?) {
        val prefixes = Channel.entries.filter { topic == null || it.topic == topic }.map { it.tagPrefix }
        manager.activeNotifications
            .filter { posted -> prefixes.any { posted.tag?.startsWith(it) == true } }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    /**
     * Creates the channel, or renames it: an existing channel keeps the user's settings and takes
     * only the new name, so calling this before each post follows a change of app language. A
     * channel appears only once something posts to it.
     */
    private suspend fun ensureChannel(channel: Channel) {
        applyAppLanguage()
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(channel.id, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(getString(channel.nameRes))
                .setDescription(getString(channel.descriptionRes))
                .build(),
        )
    }

    /**
     * Strings here resolve outside composition, from the process default locale. A process the OS
     * started for a background job never composed, so `LocalAppLocale` never set it to the in-app
     * language; set it the same way.
     */
    private suspend fun applyAppLanguage() {
        val tag = settingsDataStore.selectedLanguage.first()
        if (tag != SettingsDataStore.DEFAULT_LANGUAGE) Locale.setDefault(Locale.forLanguageTag(tag))
    }
}
