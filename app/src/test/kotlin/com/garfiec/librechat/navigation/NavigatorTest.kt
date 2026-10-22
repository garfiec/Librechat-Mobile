package com.garfiec.librechat.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.agents.navigation.AgentDetail
import com.garfiec.librechat.feature.agents.navigation.AgentEditorEdit
import com.garfiec.librechat.feature.agents.navigation.AgentMarketplace
import com.garfiec.librechat.feature.auth.navigation.AddAccountLogin
import com.garfiec.librechat.feature.auth.navigation.AddAccountServerUrl
import com.garfiec.librechat.feature.auth.navigation.Login
import com.garfiec.librechat.feature.auth.navigation.Register
import com.garfiec.librechat.feature.auth.navigation.ServerUrl
import com.garfiec.librechat.feature.chat.navigation.Chat
import com.garfiec.librechat.feature.chat.navigation.NewChat
import com.garfiec.librechat.feature.conversations.navigation.ProjectChats
import com.garfiec.librechat.feature.conversations.navigation.Projects
import com.garfiec.librechat.feature.conversations.navigation.Search
import com.garfiec.librechat.feature.settings.navigation.Memories
import com.garfiec.librechat.feature.settings.navigation.ProviderKeys
import com.garfiec.librechat.feature.settings.navigation.RoleMemoriesAdmin
import com.garfiec.librechat.feature.settings.navigation.RoleSkillsAdmin
import com.garfiec.librechat.feature.settings.navigation.SettingsTabbed
import com.garfiec.librechat.shared.navigation.Navigator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigatorTest {

    private fun createNavigator(vararg keys: NavKey): Navigator {
        return Navigator(NavBackStack(*keys))
    }

    @Test
    fun `currentRoute returns last entry`() {
        val navigator = createNavigator(NewChat())
        assertEquals(NewChat(), navigator.currentRoute)
    }

    @Test
    fun `currentRoute returns null for empty stack`() {
        val navigator = createNavigator()
        assertNull(navigator.currentRoute)
    }

    @Test
    fun `isInAuthFlow returns true for auth routes`() {
        val navigator = createNavigator(ServerUrl)
        assertTrue(navigator.isInAuthFlow)
    }

    @Test
    fun `isInAuthFlow returns true for nested auth routes`() {
        val navigator = createNavigator(ServerUrl, Login)
        assertTrue(navigator.isInAuthFlow)
    }

    @Test
    fun `isInAuthFlow returns false for non-auth routes`() {
        val navigator = createNavigator(NewChat())
        assertFalse(navigator.isInAuthFlow)
    }

    @Test
    fun `navigate adds route to back stack`() {
        val navigator = createNavigator(NewChat())
        navigator.navigate(SettingsTabbed)
        assertEquals(listOf(NewChat(), SettingsTabbed), navigator.backStack.toList())
    }

    @Test
    fun `a second tap on a route already on top pushes nothing`() {
        val navigator = createNavigator(Chat("c1"))
        navigator.navigate(Memories)
        navigator.navigate(Memories)
        assertEquals(listOf(Chat("c1"), Memories), navigator.backStack.toList())
    }

    @Test
    fun `the role editors are not stacked twice either`() {
        val navigator = createNavigator(NewChat(), SettingsTabbed)
        navigator.navigate(RoleMemoriesAdmin)
        navigator.navigate(RoleMemoriesAdmin)
        navigator.goBack()
        navigator.navigate(RoleSkillsAdmin)
        navigator.navigate(RoleSkillsAdmin)
        assertEquals(listOf(NewChat(), SettingsTabbed, RoleSkillsAdmin), navigator.backStack.toList())
    }

    @Test
    fun `a route further down the stack is still pushed`() {
        val navigator = createNavigator(NewChat(), Memories, SettingsTabbed)
        navigator.navigate(Memories)
        assertEquals(listOf(NewChat(), Memories, SettingsTabbed, Memories), navigator.backStack.toList())
    }

    @Test
    fun `focus is dropped whenever a different screen comes on top, and only then`() {
        var drops = 0
        val navigator = Navigator(NavBackStack(NewChat()), onScreenChange = { drops++ })

        navigator.navigateToChat("c1")
        assertEquals(1, drops)
        navigator.navigate(Memories)
        assertEquals(2, drops)
        navigator.navigate(Memories)
        assertEquals("a deduped push changes no screen", 2, drops)
        navigator.goBack()
        assertEquals(3, drops)
        navigator.navigateToSearch("tea")
        assertEquals(4, drops)
        navigator.navigateToMessage("c2", "m1", "tea")
        assertEquals(5, drops)
        navigator.navigateToTopLevel(NewChat())
        assertEquals(6, drops)
        navigator.navigateToTopLevel(NewChat())
        assertEquals("re-selecting the landing changes no screen", 6, drops)
        navigator.navigate(Memories)
        navigator.navigate(SettingsTabbed)
        assertEquals(8, drops)
        navigator.remove(Memories)
        assertEquals("removing a screen below the top changes no screen", 8, drops)
    }

    @Test
    fun `a chat started from a project landing replaces it, so back returns to the project`() {
        val project = NewChat(chatProjectId = "p1", chatProjectName = "Thesis")
        val navigator = createNavigator(NewChat(), SettingsTabbed, project)
        navigator.navigateToChat("c1")
        assertEquals(listOf(NewChat(), SettingsTabbed, Chat("c1")), navigator.backStack.toList())
    }

    @Test
    fun `a chat started from the plain landing keeps it underneath`() {
        val navigator = createNavigator(NewChat())
        navigator.navigateToChat("c1")
        assertEquals(listOf(NewChat(), Chat("c1")), navigator.backStack.toList())
    }

    @Test
    fun `a new chat in a project is pushed, and replaces a project landing already on top`() {
        val navigator = createNavigator(NewChat(), SettingsTabbed)
        navigator.navigateToNewChatInProject("p1", "Thesis")
        navigator.navigateToNewChatInProject("p2", "Recipes")
        assertEquals(
            listOf(NewChat(), SettingsTabbed, NewChat(chatProjectId = "p2", chatProjectName = "Recipes")),
            navigator.backStack.toList(),
        )
    }

    @Test
    fun `a new chat in a project replaces a chat on top rather than stacking over it`() {
        val navigator = createNavigator(NewChat(), Chat("a"))
        navigator.navigateToNewChatInProject("p1", "Thesis")
        navigator.navigateToChat("b")
        assertEquals(listOf(NewChat(), Chat("b")), navigator.backStack.toList())
    }

    @Test
    fun `a project's landing further down is moved up rather than duplicated`() {
        val landing = NewChat(chatProjectId = "p1", chatProjectName = "Thesis")
        val navigator = createNavigator(NewChat(), landing, SettingsTabbed)
        navigator.navigateToNewChatInProject("p1", "Thesis")
        assertEquals(listOf(NewChat(), SettingsTabbed, landing), navigator.backStack.toList())
    }

    @Test
    fun `a search hit opened from a project landing replaces it, as a chat does`() {
        val navigator = createNavigator(NewChat(), NewChat(chatProjectId = "p1", chatProjectName = "Thesis"))
        navigator.navigateToMessage("c1", "m1", "query")
        assertEquals(NewChat(), navigator.backStack.first())
        assertEquals(2, navigator.backStack.size)
        assertTrue(navigator.currentRoute is Chat)
    }

    @Test
    fun `asking again for the project on top keeps that landing`() {
        val navigator = createNavigator(NewChat())
        navigator.navigateToNewChatInProject("p1", "Thesis")
        navigator.navigateToNewChatInProject("p1", "Thesis")
        assertEquals(
            listOf(NewChat(), NewChat(chatProjectId = "p1", chatProjectName = "Thesis")),
            navigator.backStack.toList(),
        )
    }

    @Test
    fun `goBack removes top entry`() {
        val navigator = createNavigator(NewChat(), SettingsTabbed)
        navigator.goBack()
        assertEquals(listOf(NewChat()), navigator.backStack.toList())
    }

    @Test
    fun `goBack on empty stack does not crash`() {
        val navigator = createNavigator()
        navigator.goBack()
        assertTrue(navigator.backStack.isEmpty())
    }

    @Test
    fun `navigateToChat with conversationId adds Chat route`() {
        val navigator = createNavigator(NewChat())
        navigator.navigateToChat("conv-123")
        assertEquals(
            listOf(NewChat(), Chat(conversationId = "conv-123")),
            navigator.backStack.toList(),
        )
    }

    @Test
    fun `navigateToChat replaces current chat`() {
        val navigator = createNavigator(NewChat(), Chat("conv-1"))
        navigator.navigateToChat("conv-2")
        assertEquals(
            listOf(NewChat(), Chat(conversationId = "conv-2")),
            navigator.backStack.toList(),
        )
    }

    @Test
    fun `navigateToChat does not replace non-chat route`() {
        val navigator = createNavigator(NewChat(), SettingsTabbed)
        navigator.navigateToChat("conv-1")
        assertEquals(
            listOf(NewChat(), SettingsTabbed, Chat(conversationId = "conv-1")),
            navigator.backStack.toList(),
        )
    }

    @Test
    fun `navigateToTopLevel pops to root and replaces`() {
        val navigator = createNavigator(NewChat(), Chat("conv-1"), SettingsTabbed)
        navigator.navigateToTopLevel(AgentMarketplace)
        assertEquals(listOf(AgentMarketplace), navigator.backStack.toList())
    }

    @Test
    fun `navigateToTopLevel with same route does not duplicate`() {
        val navigator = createNavigator(NewChat())
        navigator.navigateToTopLevel(NewChat())
        assertEquals(listOf(NewChat()), navigator.backStack.toList())
    }

    @Test
    fun `navigateToTopLevel replaces different root`() {
        val navigator = createNavigator(NewChat())
        navigator.navigateToTopLevel(AgentMarketplace)
        assertEquals(listOf(AgentMarketplace), navigator.backStack.toList())
    }

    @Test
    fun `navigateToTopLevel carries NewChat agentId over a bare NewChat root (F2)`() {
        // Regression: Start Chat on an agent → NewChat(agentId) must REPLACE the bare
        // landing NewChat(), not be dropped by a class-only dedup.
        val navigator = createNavigator(NewChat())
        navigator.navigateToTopLevel(NewChat(agentId = "agent_X"))
        assertEquals(listOf(NewChat(agentId = "agent_X")), navigator.backStack.toList())
        assertEquals("agent_X", (navigator.backStack.last() as NewChat).agentId)
    }

    @Test
    fun `navigateToTopLevel with identical NewChat agentId is a no-op`() {
        val navigator = createNavigator(NewChat(agentId = "agent_X"))
        navigator.navigateToTopLevel(NewChat(agentId = "agent_X"))
        assertEquals(listOf(NewChat(agentId = "agent_X")), navigator.backStack.toList())
    }

    @Test
    fun `navigateToAuth clears stack and adds ServerUrl`() {
        val navigator = createNavigator(NewChat(), Chat("conv-1"), SettingsTabbed)
        navigator.navigateToAuth()
        assertEquals(listOf(ServerUrl), navigator.backStack.toList())
    }

    @Test
    fun `navigateToAuth is a no-op deeper in the auth flow`() {
        val navigator = createNavigator(ServerUrl, Login)
        navigator.navigateToAuth()
        assertEquals(listOf(ServerUrl, Login), navigator.backStack.toList())
    }

    @Test
    fun `navigateToAuth is a no-op when already on ServerUrl`() {
        val navigator = createNavigator(ServerUrl)
        navigator.navigateToAuth()
        assertEquals(listOf(ServerUrl), navigator.backStack.toList())
    }

    @Test
    fun `navigateToAuth still resets out of an add-account flow`() {
        // The add flow belongs to the session that just ended, and the nav host cancels the pending
        // add by watching these routes leave the stack — so the guard must not apply here.
        val navigator = createNavigator(NewChat(), SettingsTabbed, AddAccountServerUrl, AddAccountLogin)
        navigator.navigateToAuth()
        assertEquals(listOf(ServerUrl), navigator.backStack.toList())
    }

    @Test
    fun `navigateToAuth resets from a shared route stacked above an add-account login`() {
        // Register/2FA/forgot-password are shared routes reached from add-mode login; they sit above
        // an AddAccountLogin entry, so the whole stack has to be checked, not just the top.
        val navigator = createNavigator(AddAccountServerUrl, AddAccountLogin, Register)
        navigator.navigateToAuth()
        assertEquals(listOf(ServerUrl), navigator.backStack.toList())
    }

    @Test
    fun `navigateToChat no-arg clears stack and adds NewChat()`() {
        val navigator = createNavigator(ServerUrl, Login)
        navigator.navigateToChat()
        assertEquals(listOf(NewChat()), navigator.backStack.toList())
    }

    @Test
    fun `navigateToProviderKeys adds route when not on top`() {
        val navigator = createNavigator(NewChat())
        navigator.navigateToProviderKeys("openAI")
        assertEquals(
            listOf(NewChat(), ProviderKeys(pendingDialogEndpoint = "openAI")),
            navigator.backStack.toList(),
        )
    }

    @Test
    fun `navigateToProviderKeys is no-op when same endpoint already on top`() {
        val navigator = createNavigator(NewChat(), ProviderKeys(pendingDialogEndpoint = "openAI"))
        navigator.navigateToProviderKeys("openAI")
        assertEquals(
            listOf(NewChat(), ProviderKeys(pendingDialogEndpoint = "openAI")),
            navigator.backStack.toList(),
        )
    }

    @Test
    fun `navigateToProviderKeys replaces top when different endpoint already on top`() {
        val navigator = createNavigator(NewChat(), ProviderKeys(pendingDialogEndpoint = "openAI"))
        navigator.navigateToProviderKeys("anthropic")
        assertEquals(
            listOf(NewChat(), ProviderKeys(pendingDialogEndpoint = "anthropic")),
            navigator.backStack.toList(),
        )
    }

    @Test
    fun `navigateToProviderKeys with null endpoint dedupes against null-top`() {
        val navigator = createNavigator(NewChat(), ProviderKeys(pendingDialogEndpoint = null))
        navigator.navigateToProviderKeys(null)
        assertEquals(
            listOf(NewChat(), ProviderKeys(pendingDialogEndpoint = null)),
            navigator.backStack.toList(),
        )
    }

    /**
     * An account flip must not hand the new account the old account's landing. Nav3 keeps an
     * entry's ViewModelStore until its key leaves the back stack; a reset that put an EQUAL landing
     * back kept the outgoing account's ChatViewModel alive — its model and another server's agents
     * in the selector until a cold start.
     */
    @Test
    fun `an account flip resets to a landing that is a different entry`() {
        val navigator = createNavigator(NewChat(accountScope = "srv:user-a"), Chat("conv-a"))
        val before = navigator.backStack.single { it is NewChat }

        navigator.navigateToChat(accountScope = "srv:user-b")

        assertEquals(listOf<NavKey>(NewChat(accountScope = "srv:user-b")), navigator.backStack.toList())
        assertTrue(navigator.backStack.single() != before)
    }

    /** The first flip after a cold start, whose landing carries no scope yet. */
    @Test
    fun `an account flip from an unscoped landing is a different entry too`() {
        val navigator = createNavigator(NewChat())

        navigator.navigateToChat(accountScope = "srv:user-b")

        assertTrue(navigator.backStack.single() != NewChat())
    }

    /**
     * New chat on the landing right after a flip. The landing is scoped to the account and every
     * New chat caller passes an unscoped NewChat(); read as a different route, the tap replaced the
     * landing — a new entry, a new ViewModel, and the model the user had just picked was gone.
     */
    @Test
    fun `New chat on a scoped landing is a no-op`() {
        val landing = NewChat(accountScope = "srv:user-b")
        val navigator = createNavigator(landing)

        navigator.navigateToTopLevel(NewChat())

        assertEquals(listOf<NavKey>(landing), navigator.backStack.toList())
    }

    /** From a conversation it pops back to the scoped landing and keeps it, not a fresh one. */
    @Test
    fun `New chat from a conversation returns to the scoped landing`() {
        val landing = NewChat(accountScope = "srv:user-b")
        val navigator = createNavigator(landing, Chat("conv-b"))

        navigator.navigateToTopLevel(NewChat())

        assertEquals(listOf<NavKey>(landing), navigator.backStack.toList())
    }

    /** A payload still replaces the scoped landing, as it replaces a bare one (see F2). */
    @Test
    fun `a payload-carrying NewChat still replaces a scoped landing`() {
        val navigator = createNavigator(NewChat(accountScope = "srv:user-b"))

        navigator.navigateToTopLevel(NewChat(agentId = "agent_X"))

        assertEquals(listOf<NavKey>(NewChat(agentId = "agent_X")), navigator.backStack.toList())
    }

    @Test
    fun `navigateToSearch pushes Search above the current screen`() {
        val navigator = createNavigator(NewChat(), Chat("c1"))

        navigator.navigateToSearch("kotlin")

        assertEquals(listOf(NewChat(), Chat("c1"), Search("kotlin")), navigator.backStack.toList())
    }

    @Test
    fun `navigateToSearch replaces an open Search instead of stacking another`() {
        val navigator = createNavigator(NewChat(), Search("old"))

        navigator.navigateToSearch("new")

        assertEquals(listOf(NewChat(), Search("new")), navigator.backStack.toList())
    }

    @Test
    fun `the Search shortcut opens Search once and never while signing in`() {
        val navigator = createNavigator(NewChat(), Chat("c1"))

        assertTrue(navigator.openSearchFromShortcut())
        assertTrue(navigator.openSearchFromShortcut())
        assertEquals(listOf(NewChat(), Chat("c1"), Search("")), navigator.backStack.toList())

        val signingIn = createNavigator(ServerUrl)
        assertFalse(signingIn.openSearchFromShortcut())
        assertEquals(listOf(ServerUrl), signingIn.backStack.toList())
    }

    @Test
    fun `a jump to a reasoning hit says so on the chat route`() {
        val navigator = createNavigator(NewChat(), Search("hashcat"))

        navigator.navigateToMessage("c1", "m1", "hashcat", inReasoning = true)

        assertEquals(
            Chat("c1", targetMessageId = "m1", highlightQuery = "hashcat", targetInReasoning = true),
            navigator.backStack.last(),
        )
    }

    @Test
    fun `navigateToMessage opens the chat at the hit and keeps Search beneath`() {
        val navigator = createNavigator(NewChat(), Search("pelican"))

        navigator.navigateToMessage("c1", "m1", "pelican")

        assertEquals(
            listOf(NewChat(), Search("pelican"), Chat("c1", targetMessageId = "m1", highlightQuery = "pelican")),
            navigator.backStack.toList(),
        )
    }

    @Test
    fun `remove takes out a screen below the top and leaves the top alone`() {
        val landing = NewChat(chatProjectId = "p1", chatProjectName = "Thesis")
        val navigator = createNavigator(NewChat(), landing, Search(""))

        navigator.remove(landing)

        assertEquals(listOf(NewChat(), Search("")), navigator.backStack.toList())
    }

    @Test
    fun `remove takes out every copy of the screen`() {
        val project = ProjectChats("p1", "Thesis")
        val navigator = createNavigator(NewChat(), Projects, project, Chat("c1"), project)

        navigator.remove(project)

        assertEquals(listOf(NewChat(), Projects, Chat("c1")), navigator.backStack.toList())
    }

    @Test
    fun `remove never empties the stack`() {
        val landing = NewChat(chatProjectId = "p1", chatProjectName = "Thesis")
        val navigator = createNavigator(landing)

        navigator.remove(landing)

        assertEquals(listOf<NavKey>(landing), navigator.backStack.toList())
    }

    @Test
    fun `remove leaves the stack alone once the screen is gone`() {
        // The editor's save lands after the user left it for a chat through the drawer.
        val navigator = createNavigator(NewChat(), Chat("c1"))

        navigator.remove(AgentEditorEdit("a1"))

        assertEquals(listOf(NewChat(), Chat("c1")), navigator.backStack.toList())
    }

    @Test
    fun `remove takes the editor out from under a screen opened over it`() {
        val navigator = createNavigator(AgentMarketplace, AgentDetail("a1"), AgentEditorEdit("a1"), Search(""))

        navigator.remove(AgentEditorEdit("a1"))

        assertEquals(listOf(AgentMarketplace, AgentDetail("a1"), Search("")), navigator.backStack.toList())
    }

    @Test
    fun `replace swaps the screen on top for its replacement`() {
        val navigator = createNavigator(NewChat(), AgentMarketplace, AgentDetail("a1"))

        navigator.replace(AgentDetail("a1"), AgentDetail("copy"))

        assertEquals(listOf(NewChat(), AgentMarketplace, AgentDetail("copy")), navigator.backStack.toList())
    }

    @Test
    fun `replace leaves the stack alone once the user has left the screen`() {
        // Back from the agent page, then the duplicate lands during the page's exit animation.
        val navigator = createNavigator(NewChat(), AgentMarketplace)

        navigator.replace(AgentDetail("a1"), AgentDetail("copy"))

        assertEquals(listOf(NewChat(), AgentMarketplace), navigator.backStack.toList())
    }

    @Test
    fun `replace leaves the stack alone when another screen is on top of it`() {
        val navigator = createNavigator(AgentMarketplace, AgentDetail("a1"), NewChat("a1"))

        navigator.replace(AgentDetail("a1"), AgentDetail("copy"))

        assertEquals(listOf(AgentMarketplace, AgentDetail("a1"), NewChat("a1")), navigator.backStack.toList())
    }
}
