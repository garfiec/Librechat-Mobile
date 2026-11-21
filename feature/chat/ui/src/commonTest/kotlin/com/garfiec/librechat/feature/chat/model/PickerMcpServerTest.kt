package com.garfiec.librechat.feature.chat.model

import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.core.model.mcp.McpUserVar
import kotlin.test.Test
import kotlin.test.assertEquals

class PickerMcpServerTest {

    private fun row(
        status: McpDisplayStatus,
        on: Boolean = false,
        locked: Boolean = false,
        requiresOAuth: Boolean = false,
        signInBusy: Boolean = false,
        userVars: List<McpUserVar> = emptyList(),
    ) = PickerMcpServer(
        server = McpServerDisplayData(
            name = "github",
            title = null,
            description = null,
            status = status,
            requiresOAuth = requiresOAuth,
            signInBusy = signInBusy,
            userVars = userVars,
        ),
        on = on,
        locked = locked,
    )

    @Test
    fun aConnectedServerSwitches() {
        assertEquals(McpPick.TOGGLE, row(McpDisplayStatus.CONNECTED).pick)
        assertEquals(McpPick.TOGGLE, row(McpDisplayStatus.CONNECTED, on = true).pick)
    }

    @Test
    fun aServerThatIsOnAlwaysSwitchesOffWhateverItsStatus() {
        assertEquals(McpPick.TOGGLE, row(McpDisplayStatus.NEEDS_SIGN_IN, on = true).pick)
        assertEquals(McpPick.TOGGLE, row(McpDisplayStatus.CONNECTING, on = true, requiresOAuth = true).pick)
    }

    @Test
    fun aServerThatNeedsSomethingFirstStartsIt() {
        assertEquals(McpPick.SIGN_IN, row(McpDisplayStatus.NEEDS_SIGN_IN).pick)
        assertEquals(McpPick.SIGN_IN, row(McpDisplayStatus.FAILED, requiresOAuth = true).pick)
        assertEquals(
            McpPick.SET_UP,
            row(McpDisplayStatus.NEEDS_SETUP, userVars = listOf(McpUserVar(key = "TOKEN", title = "Token", description = ""))).pick,
        )
    }

    @Test
    fun aLockedServerStillSignsInOrSetsUp() {
        assertEquals(McpPick.SIGN_IN, row(McpDisplayStatus.NEEDS_SIGN_IN, on = true, locked = true).pick)
        assertEquals(
            McpPick.SET_UP,
            row(
                McpDisplayStatus.NEEDS_SETUP,
                locked = true,
                userVars = listOf(McpUserVar(key = "TOKEN", title = "Token", description = "")),
            ).pick,
        )
    }

    @Test
    fun aLockedServerOrOneWhoseSignInIsOutHasNoPick() {
        assertEquals(McpPick.NONE, row(McpDisplayStatus.CONNECTED, on = true, locked = true).pick)
        assertEquals(McpPick.NONE, row(McpDisplayStatus.NEEDS_SIGN_IN, signInBusy = true).pick)
        assertEquals(McpPick.NONE, row(McpDisplayStatus.CONNECTING, requiresOAuth = true).pick)
    }

    @Test
    fun serversArePickableOnlyWhereTheSheetOffersThem() {
        val servers = listOf(
            McpServerDisplayData("github", null, null, McpDisplayStatus.CONNECTED),
            McpServerDisplayData("linear", null, null, McpDisplayStatus.CONNECTED),
        )
        val rows =
            pickerMcpServersOf(servers, on = setOf("github"), locked = setOf("linear"), toolbarShown = true, mcpAllowed = true)
        assertEquals(listOf(true to false, false to true), rows.map { it.on to it.locked })
        assertEquals(emptyList(), pickerMcpServersOf(servers, emptySet(), emptySet(), toolbarShown = false, mcpAllowed = true))
        assertEquals(emptyList(), pickerMcpServersOf(servers, emptySet(), emptySet(), toolbarShown = true, mcpAllowed = false))
    }
}
