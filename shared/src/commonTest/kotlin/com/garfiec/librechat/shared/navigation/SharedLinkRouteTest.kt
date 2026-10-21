package com.garfiec.librechat.shared.navigation

import com.garfiec.librechat.core.data.datastore.AccountEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedLinkRouteTest {

    private val work = AccountEntry("work", "https://chat.work.example", "Ada", lastActiveAt = 3)
    private val home = AccountEntry("home", "https://home.example/librechat", "ada@home", lastActiveAt = 2)
    private val homeOld = AccountEntry("home-old", "https://home.example/librechat", "old", lastActiveAt = 1)
    private val all = listOf(work, home, homeOld)
    private val live = setOf("work", "home", "home-old")

    @Test
    fun aLinkOnTheActiveServerOpensTheViewer() {
        assertEquals(
            SharedLinkRoute.Open("abc"),
            sharedLinkRoute("https://chat.work.example/share/abc", all, activeId = "work", liveIds = live),
        )
    }

    @Test
    fun aLinkOnAnotherSignedInServerOffersTheSwitch() {
        assertEquals(
            SharedLinkRoute.OfferSwitch(
                url = "https://home.example/librechat/share/abc",
                shareId = "abc",
                accountId = "home",
                accountLabel = "ada@home",
                serverHost = "home.example",
            ),
            sharedLinkRoute("https://home.example/librechat/share/abc", all, activeId = "work", liveIds = live),
        )
    }

    @Test
    fun theMostRecentOfTwoAccountsOnOneServerIsOffered() {
        val route = sharedLinkRoute("https://home.example/librechat/share/abc", all, "work", live)
        assertEquals("home", (route as SharedLinkRoute.OfferSwitch).accountId)
    }

    @Test
    fun theActiveAccountWinsOverAnotherOnTheSameServer() {
        assertEquals(
            SharedLinkRoute.Open("abc"),
            sharedLinkRoute("https://home.example/librechat/share/abc", all, activeId = "home-old", liveIds = live),
        )
    }

    @Test
    fun anExpiredAccountIsNeverOffered() {
        assertEquals(
            SharedLinkRoute.NotOurs,
            sharedLinkRoute(
                "https://home.example/librechat/share/abc",
                all,
                activeId = "work",
                liveIds = setOf("work"),
            ),
        )
    }

    @Test
    fun anUnknownServerOrANonShareLinkIsNotOurs() {
        assertEquals(SharedLinkRoute.NotOurs, sharedLinkRoute("https://other.example/share/abc", all, "work", live))
        assertEquals(SharedLinkRoute.NotOurs, sharedLinkRoute("https://chat.work.example/c/abc", all, "work", live))
        // The host root is not the subpath deployment.
        assertEquals(SharedLinkRoute.NotOurs, sharedLinkRoute("https://home.example/share/abc", all, "work", live))
    }
}
