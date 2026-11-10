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
import com.garfiec.librechat.core.notifications.resources.reply_body_failed
import com.garfiec.librechat.core.notifications.resources.reply_body_finished
import com.garfiec.librechat.core.notifications.resources.reply_public_body
import com.garfiec.librechat.core.notifications.resources.reply_title_fallback
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.getString
import java.util.Locale

internal const val REPLIES_CHANNEL_ID = "replies"
private const val REPLY_TAG_PREFIX = "reply:"
private const val REPLY_NOTIFICATION_ID = 1

class AndroidNotifier(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore,
) : PlatformNotifier {

    private val manager = NotificationManagerCompat.from(context)

    // The posting checks areNotificationsEnabled first; on 13+ that is false without POST_NOTIFICATIONS.
    @SuppressLint("MissingPermission")
    override suspend fun post(notification: ReplyNotification, link: String, silent: Boolean) {
        ensureChannel()
        if (!manager.areNotificationsEnabled()) return
        val title = notification.title?.takeIf { it.isNotBlank() } ?: getString(Res.string.reply_title_fallback)
        val body = when {
            notification.failed -> getString(Res.string.reply_body_failed)
            notification.preview.isNotBlank() -> notification.preview
            else -> getString(Res.string.reply_body_finished)
        }
        // Explicit to this app; the data differs per conversation, so each gets its own PendingIntent.
        val open = Intent(Intent.ACTION_VIEW, link.toUri()).setPackage(context.packageName)
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // What a locked screen shows when the user hides sensitive content: no title, no text.
        val publicVersion = NotificationCompat.Builder(context, REPLIES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reply)
            .setContentText(getString(Res.string.reply_public_body))
            .build()
        val built = NotificationCompat.Builder(context, REPLIES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reply)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setSilent(silent)
            .build()
        manager.notify(REPLY_TAG_PREFIX + notification.conversationId, REPLY_NOTIFICATION_ID, built)
    }

    override fun cancel(conversationId: String) {
        manager.cancel(REPLY_TAG_PREFIX + conversationId, REPLY_NOTIFICATION_ID)
    }

    override fun cancelAll() {
        manager.activeNotifications
            .filter { it.tag?.startsWith(REPLY_TAG_PREFIX) == true }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    /**
     * Creates the Replies channel, or renames it: an existing channel keeps the user's settings and
     * takes only the new name, so calling this before each post follows a change of app language.
     */
    suspend fun ensureChannel() {
        applyAppLanguage()
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(REPLIES_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(getString(Res.string.channel_replies))
                .setDescription(getString(Res.string.channel_replies_description))
                .build(),
        )
    }

    /**
     * Strings here resolve outside composition, from the process default locale. A process the OS
     * started for a background watch never composed, so `LocalAppLocale` never set it to the in-app
     * language; set it the same way.
     */
    private suspend fun applyAppLanguage() {
        val tag = settingsDataStore.selectedLanguage.first()
        if (tag != SettingsDataStore.DEFAULT_LANGUAGE) Locale.setDefault(Locale.forLanguageTag(tag))
    }
}
