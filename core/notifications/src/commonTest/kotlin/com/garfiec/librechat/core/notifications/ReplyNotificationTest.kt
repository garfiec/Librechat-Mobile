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
        assertEquals(ReplyDelivery.System, replyDelivery(reply, context()))
    }

    @Test
    fun inFrontOnAnotherScreen_showsASnackbar() {
        assertEquals(ReplyDelivery.InApp, replyDelivery(reply, context(foreground = true, open = "other")))
        assertEquals(ReplyDelivery.InApp, replyDelivery(reply, context(foreground = true, systemAllows = false)))
    }

    @Test
    fun inFrontOnThatChat_showsNothing() {
        assertEquals(ReplyDelivery.Drop, replyDelivery(reply, context(foreground = true, open = "c1")))
    }

    @Test
    fun theSameChatLeftOpenInTheBackground_stillNotifies() {
        assertEquals(ReplyDelivery.System, replyDelivery(reply, context(foreground = false, open = "c1")))
    }

    @Test
    fun anotherAccountsReply_isNeverShown() {
        assertEquals(ReplyDelivery.Drop, replyDelivery(reply, context(active = AccountId("server:bob"))))
        assertEquals(ReplyDelivery.Drop, replyDelivery(reply, context(active = null, foreground = true)))
    }

    @Test
    fun theUsersToggleTheServerAndTheSystemEachSilenceIt() {
        assertEquals(ReplyDelivery.Drop, replyDelivery(reply, context(enabled = false)))
        assertEquals(ReplyDelivery.Drop, replyDelivery(reply, context(serverAllows = false)))
        assertEquals(ReplyDelivery.Drop, replyDelivery(reply, context(serverAllows = false, foreground = true)))
        assertEquals(ReplyDelivery.Drop, replyDelivery(reply, context(systemAllows = false)))
    }
}
