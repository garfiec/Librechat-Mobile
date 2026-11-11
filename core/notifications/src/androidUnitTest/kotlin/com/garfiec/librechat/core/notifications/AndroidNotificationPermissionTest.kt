package com.garfiec.librechat.core.notifications

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.garfiec.librechat.core.common.lifecycle.ForegroundSignal
import com.garfiec.librechat.core.data.datastore.AppPreferences
import com.garfiec.librechat.core.data.datastore.PreferenceStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidNotificationPermissionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = NotificationManagerCompat.from(context)
    private val preferences = mockk<PreferenceStore> {
        every { observe(AppPreferences.NotificationAsksAnswered) } returns flowOf(listOf("system_prompt"))
    }

    private fun channel(topic: NotificationTopic, importance: Int) = manager.createNotificationChannel(
        NotificationChannelCompat.Builder(topic.channelId, importance).setName(topic.name).build(),
    )

    @Test
    fun oneTopicTurnedOff_blocksOnlyThatTopic() = runTest {
        channel(NotificationTopic.Replies, NotificationManagerCompat.IMPORTANCE_NONE)
        channel(NotificationTopic.ScheduledChats, NotificationManagerCompat.IMPORTANCE_HIGH)
        val permission = AndroidNotificationPermission(context, preferences, ForegroundSignal(), backgroundScope)
        runCurrent()

        assertEquals(NotificationPermissionStatus.Allowed, permission.status.value)
        assertFalse(permission.allows(NotificationTopic.Replies))
        assertTrue(permission.allows(NotificationTopic.ScheduledChats))
    }

    @Test
    fun repliesTurnedOff_beforeAnyScheduledChatPosted_stillAllowsScheduledChats() = runTest {
        channel(NotificationTopic.Replies, NotificationManagerCompat.IMPORTANCE_NONE)
        val permission = AndroidNotificationPermission(context, preferences, ForegroundSignal(), backgroundScope)
        runCurrent()

        assertFalse(permission.allows(NotificationTopic.Replies))
        assertTrue(permission.allows(NotificationTopic.ScheduledChats))
    }

    @Test
    fun everyTopicTurnedOff_readsAsOff() = runTest {
        channel(NotificationTopic.Replies, NotificationManagerCompat.IMPORTANCE_NONE)
        channel(NotificationTopic.ScheduledChats, NotificationManagerCompat.IMPORTANCE_NONE)
        val permission = AndroidNotificationPermission(context, preferences, ForegroundSignal(), backgroundScope)
        runCurrent()

        assertEquals(NotificationPermissionStatus.Off, permission.status.value)
    }
}
