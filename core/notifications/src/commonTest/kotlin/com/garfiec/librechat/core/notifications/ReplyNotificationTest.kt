package com.garfiec.librechat.core.notifications

import com.garfiec.librechat.core.common.identity.AccountId
import kotlin.test.Test
import kotlin.test.assertEquals

class ReplyNotificationTest {

    private val alice = AccountId("server:alice")
    private val reply = ReplyNotification(alice, "c1", title = "Trip", preview = "Done", failed = false)

    private fun context(
        active: AccountId? = alice,
        foreground: Boolean = false,
        open: String? = null,
        enabled: Boolean = true,
        serverAllows: Boolean = true,
        systemAllows: Boolean = true,
    ) = ReplyDeliveryContext(active, foreground, open, enabled, serverAllows, systemAllows)

    @Test
    fun inTheBackground_postsASystemNotification() {
        assertEquals(ReplyDelivery.System, deliveryOf(reply, context()))
    }

    @Test
    fun inFrontOnAnotherScreen_showsASnackbar() {
        assertEquals(ReplyDelivery.InApp, deliveryOf(reply, context(foreground = true, open = "other")))
        assertEquals(ReplyDelivery.InApp, deliveryOf(reply, context(foreground = true, systemAllows = false)))
    }

    @Test
    fun inFrontOnThatChat_showsNothing() {
        assertEquals(ReplyDelivery.Drop, deliveryOf(reply, context(foreground = true, open = "c1")))
    }

    @Test
    fun theSameChatLeftOpenInTheBackground_stillNotifies() {
        assertEquals(ReplyDelivery.System, deliveryOf(reply, context(foreground = false, open = "c1")))
    }

    @Test
    fun anotherAccountsReply_isNeverShown() {
        assertEquals(ReplyDelivery.Drop, deliveryOf(reply, context(active = AccountId("server:bob"))))
        assertEquals(ReplyDelivery.Drop, deliveryOf(reply, context(active = null, foreground = true)))
    }

    @Test
    fun theUsersToggleTheServerAndTheSystemEachSilenceIt() {
        assertEquals(ReplyDelivery.Drop, deliveryOf(reply, context(enabled = false)))
        assertEquals(ReplyDelivery.Drop, deliveryOf(reply, context(serverAllows = false)))
        assertEquals(ReplyDelivery.Drop, deliveryOf(reply, context(serverAllows = false, foreground = true)))
        assertEquals(ReplyDelivery.Drop, deliveryOf(reply, context(systemAllows = false)))
    }

    @Test
    fun aScheduledRun_followsTheSameRules_onItsOwnChat() {
        val run = ScheduledRunNotification(
            alice,
            "s1",
            "Brief",
            conversationId = "c2",
            outcome = ScheduledRunOutcome.Finished,
        )
        assertEquals(ReplyDelivery.System, deliveryOf(run, context()))
        assertEquals(ReplyDelivery.Drop, deliveryOf(run, context(foreground = true, open = "c2")))
        val paused = run.copy(conversationId = null, outcome = ScheduledRunOutcome.Paused)
        assertEquals(ReplyDelivery.InApp, deliveryOf(paused, context(foreground = true, open = "c1")))
        // Off any chat, no chat is open either: a run without one still shows.
        assertEquals(ReplyDelivery.InApp, deliveryOf(paused, context(foreground = true, open = null)))
    }
}
