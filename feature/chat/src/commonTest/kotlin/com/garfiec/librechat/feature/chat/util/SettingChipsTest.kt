package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.core.ui.components.ModelParameters
import com.garfiec.librechat.feature.chat.model.McpServerDisplayData
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.FeatureGatesState
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SettingChipsTest {

    private val allOpen = SETTING_TOOL_ORDER.toSet()
    private val thinking = ThinkingChip(
        key = "reasoning_effort",
        options = listOf("", "low", "high"),
        configured = "",
        pending = null,
        unsetIsOff = false,
    )
    private val linear =
        McpServerDisplayData(name = "linear", title = "Linear", description = null, status = McpDisplayStatus.CONNECTED)
    private val docs =
        McpServerDisplayData(name = "docs", title = null, description = null, status = McpDisplayStatus.NEEDS_SIGN_IN)

    private fun inputs(
        toolbarShown: Boolean = true,
        toolsOn: Set<String> = emptySet(),
        openTools: Set<String> = allOpen,
        pinned: List<String> = emptyList(),
        lockedTools: Set<String> = emptySet(),
        mcpServers: List<McpServerDisplayData> = emptyList(),
        mcpOn: Set<String> = emptySet(),
        lockedMcpServers: Set<String> = emptySet(),
        mcpAllowed: Boolean = true,
        specLabel: String? = null,
        thinking: ThinkingChip? = null,
    ) = SettingChipInputs(
        toolbarShown, toolsOn, openTools, pinned, lockedTools, mcpServers, mcpOn, lockedMcpServers, mcpAllowed, specLabel, thinking,
    )

    @Test
    fun aToolThatIsOnAndPinnedIsOneChip() {
        val chips = settingChipsOf(
            inputs(toolsOn = setOf(ToolConstants.WEB_SEARCH), pinned = listOf(ToolConstants.WEB_SEARCH, ToolConstants.WEB_SEARCH)),
        ).chips

        assertEquals(listOf<SettingChip>(SettingChip.Tool(ToolConstants.WEB_SEARCH, SettingChipState.ON, pinned = true)), chips)
    }

    @Test
    fun toolsFollowTheSheetsOrderNotThePinOrder() {
        val chips = settingChipsOf(
            inputs(
                toolsOn = setOf(ToolConstants.SKILLS, ToolConstants.WEB_SEARCH),
                pinned = listOf(ToolConstants.MEMORY, ToolConstants.EXECUTE_CODE),
            ),
        ).chips

        assertEquals(
            listOf<SettingChip>(
                SettingChip.Tool(ToolConstants.WEB_SEARCH, SettingChipState.ON, pinned = false),
                SettingChip.Tool(ToolConstants.CODE_INTERPRETER, SettingChipState.PINNED_OFF, pinned = true),
                SettingChip.Tool(ToolConstants.MEMORY, SettingChipState.PINNED_OFF, pinned = true),
                SettingChip.Tool(ToolConstants.SKILLS, SettingChipState.ON, pinned = false),
            ),
            chips,
        )
    }

    @Test
    fun aStoredFlagWhoseGateIsShutNeverShows() {
        val chips = settingChipsOf(
            inputs(
                toolsOn = setOf(ToolConstants.URL_CONTEXT, ToolConstants.MEMORY, ToolConstants.FILE_SEARCH),
                openTools = setOf(ToolConstants.FILE_SEARCH),
                pinned = listOf(ToolConstants.MEMORY),
            ),
        ).chips

        assertEquals(listOf<SettingChip>(SettingChip.Tool(ToolConstants.FILE_SEARCH, SettingChipState.ON, pinned = false)), chips)
    }

    @Test
    fun unknownPinnedKeysAreIgnored() {
        assertTrue(settingChipsOf(inputs(pinned = listOf("mcp", "shadcnui"))).isEmpty)
    }

    @Test
    fun aPinnedArtifactsKeyIsTheArtifactsTool() {
        assertEquals(ToolConstants.ARTIFACTS, pinnedToolKey(ToolConstants.ARTIFACTS))

        val chips = settingChipsOf(inputs(pinned = listOf(ToolConstants.ARTIFACTS))).chips

        assertEquals(listOf<SettingChip>(SettingChip.Tool(ToolConstants.ARTIFACTS, SettingChipState.PINNED_OFF, pinned = true)), chips)
    }

    @Test
    fun aSpecsOwnToolsAndServersAreLockedAndNamed() {
        val line = settingChipsOf(
            inputs(
                toolsOn = setOf(ToolConstants.WEB_SEARCH, ToolConstants.FILE_SEARCH),
                lockedTools = setOf(ToolConstants.WEB_SEARCH),
                mcpServers = listOf(linear, docs),
                mcpOn = setOf("linear", "docs"),
                lockedMcpServers = setOf("docs"),
                specLabel = "Research",
            ),
        )

        assertEquals(
            listOf<SettingChip>(
                SettingChip.Tool(ToolConstants.WEB_SEARCH, SettingChipState.LOCKED, pinned = false),
                SettingChip.Tool(ToolConstants.FILE_SEARCH, SettingChipState.ON, pinned = false),
                SettingChip.Mcp(linear, SettingChipState.ON),
                SettingChip.Mcp(docs, SettingChipState.LOCKED),
            ),
            line.chips,
        )
        assertEquals("Research", line.lockedBy)
    }

    @Test
    fun theSpecIsNamedOnlyWhenSomethingIsLocked() {
        val line = settingChipsOf(inputs(toolsOn = setOf(ToolConstants.WEB_SEARCH), specLabel = "Research"))

        assertNull(line.lockedBy)
    }

    @Test
    fun mcpServersShowInListOrderAndOnlyWhenTheRoleAllowsThem() {
        val allowed = settingChipsOf(inputs(mcpServers = listOf(docs, linear), mcpOn = setOf("linear", "docs"))).chips
        assertEquals(listOf("docs", "linear"), allowed.map { (it as SettingChip.Mcp).name })

        val denied = settingChipsOf(
            inputs(
                mcpServers = listOf(docs, linear),
                mcpOn = setOf("linear", "docs"),
                lockedMcpServers = setOf("docs"),
                mcpAllowed = false,
            ),
        ).chips
        assertEquals(listOf("docs"), denied.map { (it as SettingChip.Mcp).name })
    }

    @Test
    fun withoutTheToolbarOnlyThinkingStays() {
        val chips = settingChipsOf(
            inputs(
                toolbarShown = false,
                toolsOn = setOf(ToolConstants.WEB_SEARCH),
                pinned = listOf(ToolConstants.CODE_INTERPRETER),
                lockedTools = setOf(ToolConstants.WEB_SEARCH),
                mcpServers = listOf(linear),
                mcpOn = setOf("linear"),
                thinking = thinking,
            ),
        ).chips

        assertEquals(listOf<SettingChip>(SettingChip.Thinking(thinking)), chips)
    }

    @Test
    fun thinkingComesLast() {
        val chips = settingChipsOf(
            inputs(
                toolsOn = setOf(ToolConstants.CODE_INTERPRETER),
                mcpServers = listOf(linear),
                mcpOn = setOf("linear"),
                thinking = thinking,
            ),
        ).chips

        assertEquals(listOf("Tool", "Mcp", "Thinking"), chips.map { it::class.simpleName })
    }

    @Test
    fun nothingOnNothingPinnedNoThinkingIsEmpty() {
        assertSame(SettingChips.EMPTY, settingChipsOf(inputs()))
    }

    @Test
    fun aTapExplainsALockTurnsOnAPinnedToolAndShowsAnythingOnInTheSheet() {
        val line = settingChipsOf(
            inputs(
                toolsOn = setOf(ToolConstants.WEB_SEARCH, ToolConstants.CODE_INTERPRETER, ToolConstants.MEMORY),
                pinned = listOf(ToolConstants.CODE_INTERPRETER, ToolConstants.FILE_SEARCH),
                lockedTools = setOf(ToolConstants.WEB_SEARCH),
                mcpServers = listOf(linear),
                mcpOn = setOf("linear"),
                specLabel = "Research",
                thinking = thinking,
            ),
        )
        val byKey = line.chips.associateBy {
            when (it) {
                is SettingChip.Tool -> it.key
                is SettingChip.Mcp -> it.name
                is SettingChip.Thinking -> "thinking"
            }
        }

        assertEquals(SettingChipTap.ExplainLock("Research"), line.tapFor(byKey.getValue(ToolConstants.WEB_SEARCH)))
        // A tap never turns a thing off: one that is on, pinned or not, opens the sheet at its row.
        assertEquals(
            SettingChipTap.ShowInSheet(ToolsSheetFocus.Tool(ToolConstants.CODE_INTERPRETER)),
            line.tapFor(byKey.getValue(ToolConstants.CODE_INTERPRETER)),
        )
        // A pinned tool that is off turns on at once.
        assertEquals(SettingChipTap.TurnOn, line.tapFor(byKey.getValue(ToolConstants.FILE_SEARCH)))
        assertEquals(
            SettingChipTap.ShowInSheet(ToolsSheetFocus.Tool(ToolConstants.MEMORY)),
            line.tapFor(byKey.getValue(ToolConstants.MEMORY)),
        )
        assertEquals(SettingChipTap.ShowInSheet(ToolsSheetFocus.McpServer("linear")), line.tapFor(byKey.getValue("linear")))
        assertEquals(SettingChipTap.OpenThinking, line.tapFor(byKey.getValue("thinking")))
    }

    @Test
    fun anMcpServerThatNeedsSignInSignsInOnTapEvenWhenLocked() {
        val failed =
            McpServerDisplayData(name = "sentry", title = "Sentry", description = null, status = McpDisplayStatus.FAILED)
        val setup =
            McpServerDisplayData(name = "jira", title = "Jira", description = null, status = McpDisplayStatus.NEEDS_SETUP)
        val line = settingChipsOf(
            inputs(
                mcpServers = listOf(linear, docs, failed, setup),
                mcpOn = setOf("linear", "docs", "sentry", "jira"),
                lockedMcpServers = setOf("docs"),
                specLabel = "Research",
            ),
        )
        val byName = line.chips.filterIsInstance<SettingChip.Mcp>().associateBy { it.name }

        assertEquals(
            mapOf(
                "linear" to McpDisplayStatus.CONNECTED,
                "docs" to McpDisplayStatus.NEEDS_SIGN_IN,
                "sentry" to McpDisplayStatus.FAILED,
                "jira" to McpDisplayStatus.NEEDS_SETUP,
            ),
            byName.mapValues { it.value.status },
        )
        assertEquals(SettingChipTap.SignIn("docs"), line.tapFor(byName.getValue("docs")))
        // Only sign-in is something a tap here can fix; setup is on the web, a failure on the server.
        assertEquals(SettingChipTap.ShowInSheet(ToolsSheetFocus.McpServer("sentry")), line.tapFor(byName.getValue("sentry")))
        assertEquals(SettingChipTap.ShowInSheet(ToolsSheetFocus.McpServer("jira")), line.tapFor(byName.getValue("jira")))
        assertEquals(SettingChipTap.ShowInSheet(ToolsSheetFocus.McpServer("linear")), line.tapFor(byName.getValue("linear")))
    }

    @Test
    fun anOAuthServerThatFailedSignsInAgainAndOnePendingOpensItsMenu() {
        fun server(name: String, status: McpDisplayStatus, oauth: Boolean) =
            McpServerDisplayData(name = name, title = null, description = null, status = status, requiresOAuth = oauth)
        val pending = server("github", McpDisplayStatus.CONNECTING, oauth = true)
        val refused = server("drive", McpDisplayStatus.FAILED, oauth = true)
        val connecting = server("search", McpDisplayStatus.CONNECTING, oauth = false)
        val broken = server("sentry", McpDisplayStatus.FAILED, oauth = false)
        val line = settingChipsOf(
            inputs(
                mcpServers = listOf(pending, refused, connecting, broken),
                mcpOn = setOf("github", "drive", "search", "sentry"),
            ),
        )
        val byName = line.chips.filterIsInstance<SettingChip.Mcp>().associateBy { it.name }

        // A pending sign-in offers Cancel sign-in and Start over (the chip's own menu), never off.
        assertEquals(SettingChipTap.SignInMenu, line.tapFor(byName.getValue("github")))
        assertEquals(SettingChipTap.SignIn("drive"), line.tapFor(byName.getValue("drive")))
        // Not OAuth: nothing to sign in to, so the ordinary tap.
        assertEquals(SettingChipTap.ShowInSheet(ToolsSheetFocus.McpServer("search")), line.tapFor(byName.getValue("search")))
        assertEquals(SettingChipTap.ShowInSheet(ToolsSheetFocus.McpServer("sentry")), line.tapFor(byName.getValue("sentry")))
        // "· sign in" on the chip that signs in, "· connecting" on the one whose sign-in is pending.
        val names = listOf("github", "drive", "search", "sentry")
        assertEquals(listOf(false, true, false, false), names.map { byName.getValue(it).signsInOnTap })
        assertEquals(listOf(true, false, false, false), names.map { byName.getValue(it).signInPending })
    }

    @Test
    fun whileASignInOrCancelIsOutNoChipOffersASignIn() {
        fun server(name: String, status: McpDisplayStatus) = McpServerDisplayData(
            name = name,
            title = null,
            description = null,
            status = status,
            requiresOAuth = true,
            signInBusy = true,
        )
        val line = settingChipsOf(
            inputs(
                mcpServers = listOf(
                    server("github", McpDisplayStatus.CONNECTING),
                    server("drive", McpDisplayStatus.NEEDS_SIGN_IN),
                    server("linear", McpDisplayStatus.CONNECTED),
                ),
                mcpOn = setOf("github", "drive", "linear"),
            ),
        )
        val byName = line.chips.filterIsInstance<SettingChip.Mcp>().associateBy { it.name }

        // The reconnect or cancel out would ignore a Cancel sign-in, a Start over or a sign-in.
        assertEquals(SettingChipTap.WaitForSignIn, line.tapFor(byName.getValue("github")))
        assertEquals(SettingChipTap.WaitForSignIn, line.tapFor(byName.getValue("drive")))
        // A chip whose tap opens the sheet isn't held.
        assertEquals(SettingChipTap.ShowInSheet(ToolsSheetFocus.McpServer("linear")), line.tapFor(byName.getValue("linear")))
        assertEquals(listOf(true, true, false), listOf("github", "drive", "linear").map { byName.getValue(it).waitsForSignIn })
    }

    @Test
    fun aSpecLockedServerWhoseSignInIsPendingStillOpensItsMenu() {
        val pending = McpServerDisplayData(
            name = "docs",
            title = null,
            description = null,
            status = McpDisplayStatus.CONNECTING,
            requiresOAuth = true,
        )
        val line = settingChipsOf(
            inputs(mcpServers = listOf(pending), mcpOn = setOf("docs"), lockedMcpServers = setOf("docs"), specLabel = "Research"),
        )
        val chip = line.chips.filterIsInstance<SettingChip.Mcp>().single()

        assertEquals(SettingChipState.LOCKED, chip.state)
        assertEquals(SettingChipTap.SignInMenu, line.tapFor(chip))
    }

    @Test
    fun theMemoKeepsOneInstanceWhileTheInputsAreEqual() {
        val memo = SettingChipsMemo()
        val first = memo.resolve(listOf(Any())) { inputs(toolsOn = setOf(ToolConstants.WEB_SEARCH)) }

        assertSame(first, memo.resolve(listOf(Any())) { inputs(toolsOn = setOf(ToolConstants.WEB_SEARCH)) })
        assertEquals(
            SettingChipState.PINNED_OFF,
            (memo.resolve(listOf(Any())) { inputs(pinned = listOf(ToolConstants.WEB_SEARCH)) }.chips.single() as SettingChip.Tool).state,
        )
    }

    @Test
    fun aContentOnlyEmissionGathersNoInputs() {
        val memo = SettingChipsMemo()
        val state = ChatUiState(
            selection = ModelSelectionState(
                choice = ModelChoice.Raw("openAI", "gpt-5.1"),
                enabledTools = setOf(ToolConstants.CODE_INTERPRETER),
            ),
        )
        var gathered = 0
        val first = memo.resolve(state.settingChipSlices) { gathered++; state.settingChipInputs }

        // A streaming flush replaces the message slice and nothing the line reads.
        val flushed = state.copy(content = state.content.copy(isStreaming = true))
        assertSame(first, memo.resolve(flushed.settingChipSlices) { gathered++; flushed.settingChipInputs })
        assertEquals(1, gathered)

        // A tool toggle replaces the selection: gathered again.
        val toggled = state.copy(selection = state.selection.copy(enabledTools = emptySet()))
        assertTrue(memo.resolve(toggled.settingChipSlices) { gathered++; toggled.settingChipInputs }.isEmpty)
        assertEquals(2, gathered)
    }

    /** The chat state's gates, end to end: URL context and memory stored on, neither runnable here. */
    @Test
    fun theChatStatesGatesHideStoredFlagsTheServerCantRun() {
        val state = ChatUiState(
            selection = ModelSelectionState(
                choice = ModelChoice.Raw("openAI", "gpt-5.1"),
                enabledTools = setOf(ToolConstants.MEMORY, ToolConstants.CODE_INTERPRETER),
                modelParameters = ModelParameters(urlContext = true),
            ),
            gates = FeatureGatesState(pinnedTools = listOf(ToolConstants.URL_CONTEXT, ToolConstants.MEMORY)),
        )

        val chips = settingChipsOf(state.settingChipInputs).chips

        // Code: no agents config yet, which fails open (isCodeInterpreterAvailable). URL context needs
        // a Google model; memory needs the agents endpoint's `memory` capability, which fails closed.
        assertEquals(listOf<SettingChip>(SettingChip.Tool(ToolConstants.CODE_INTERPRETER, SettingChipState.ON, pinned = false)), chips)
    }
}
