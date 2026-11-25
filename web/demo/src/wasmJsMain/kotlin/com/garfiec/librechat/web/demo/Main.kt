package com.garfiec.librechat.web.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.components.AdaptiveGroupedPage
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader
import com.garfiec.librechat.core.ui.components.AdaptiveSegmentedChoice
import com.garfiec.librechat.core.ui.components.adaptiveRowColor
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.components.topbar.MaterialBarStyle
import com.garfiec.librechat.core.ui.glass.GlassSheetHost
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.conversations.drawer.AccountChip
import com.garfiec.librechat.feature.conversations.drawer.AccountSwitcherSheet
import com.garfiec.librechat.feature.conversations.drawer.AccountUiModel
import com.garfiec.librechat.feature.conversations.drawer.RemoveAccountDialog

/**
 * The initial state follows the browser: dark when it prefers dark, Liquid Glass on Apple systems,
 * Material elsewhere. The URL hash, comma-separated, overrides it so a link or a headless screenshot
 * can pick: `#glass,dark,accent=2,settings` (`material` and `light` too, `accounts` to open the
 * account switcher, `chat=<id>` to open one of the active account's sample chats). index.html's
 * loading screen picks its colours by the same rule.
 */
private class InitialState(hash: String, prefersDark: Boolean, apple: Boolean) {
    private val tokens = hash.removePrefix("#").split(',').map { it.trim().lowercase() }.toSet()
    val glass = "glass" in tokens || (apple && "material" !in tokens)
    val dark = "dark" in tokens || (prefersDark && "light" !in tokens)
    val settings = "settings" in tokens
    val accounts = "accounts" in tokens
    val chat = tokens.firstOrNull { it.startsWith("chat=") }?.substringAfter('=')
    val accent = tokens.firstOrNull { it.startsWith("accent=") }?.substringAfter('=')?.toIntOrNull()
        ?.coerceIn(0, Accents.lastIndex) ?: 0
}

private val Accents = listOf(
    "Indigo" to Color(0xFF4F46E5),
    "Teal" to Color(0xFF0D9488),
    "Rose" to Color(0xFFE11D48),
    "Amber" to Color(0xFFD97706),
    "Violet" to Color(0xFF7C3AED),
)

/** What the content pane shows: a chat by id (at a search hit, from Search), Search, or Settings. */
private sealed interface Route {
    data class Chat(val conversationId: String, val jump: ChatJump? = null) : Route
    /** Holds its screen's state, so Back from a hit returns to the search as it was left. */
    class Search(val session: DemoSearchSession) : Route
    data object Settings : Route
}

private fun locationHash(): String = js("window.location.hash || ''")

private fun prefersDark(): Boolean = js("window.matchMedia('(prefers-color-scheme: dark)').matches")

/** iPadOS reports itself as a Mac, so both count. */
private fun isApple(): Boolean = js("/mac|iphone|ipad|ipod/i.test(navigator.userAgentData?.platform || navigator.platform || navigator.userAgent)")

// Each screen the visitor opens is a browser history entry, so the browser's Back button and the
// on-screen back arrow both return to the one before (the app has the system back for this).
private fun pushHistoryEntry(): Unit = js("window.history.pushState(null, '')")
private fun historyBack(): Unit = js("window.history.back()")
private fun historyGo(delta: Int): Unit = js("window.history.go(delta)")
private fun onBrowserBack(handler: () -> Unit): Unit = js("window.addEventListener('popstate', () => handler())")

/** Hides index.html's loading screen. */
private fun notifyReady(): Unit = js("window.switchboardReady && window.switchboardReady()")

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val initial = InitialState(locationHash(), prefersDark(), isApple())
    ComposeViewport("compose-root") {
        Demo(initial)
        LaunchedEffect(Unit) {
            // The frame after the first one: the first is drawn by then.
            withFrameNanos {}
            withFrameNanos {}
            notifyReady()
            prefetchSampleLibraries()
        }
    }
}

@Composable
private fun Demo(initial: InitialState) {
    var glass by remember { mutableStateOf(initial.glass) }
    var dark by remember { mutableStateOf(initial.dark) }
    var accent by remember { mutableIntStateOf(initial.accent) }
    var showAccounts by remember { mutableStateOf(initial.accounts) }
    var accounts by remember { mutableStateOf(SampleAccounts) }
    var removing by remember { mutableStateOf<AccountUiModel?>(null) }
    val active = accounts.firstOrNull { it.isActive }
    var newChats by remember { mutableStateOf(1) }
    var jumps by remember { mutableStateOf(0) }
    var backStack by remember {
        val first = Route.Chat(initial.chat ?: "new-0")
        mutableStateOf(if (initial.settings) listOf(first, Route.Settings) else listOf<Route>(first))
    }
    val route = backStack.last()
    val goBack: (() -> Unit)? = if (backStack.size > 1) ::historyBack else null
    LaunchedEffect(Unit) {
        if (initial.settings) pushHistoryEntry()
        onBrowserBack { if (backStack.size > 1) backStack = backStack.dropLast(1) }
    }
    val navigate: (Route) -> Unit = { next ->
        if (next != route) {
            backStack = backStack + next
            pushHistoryEntry()
        }
    }
    val openHit: (String, ChatJump) -> Unit = { id, jump -> navigate(Route.Chat(id, jump.copy(visit = jumps++))) }
    // As in the app, a switch opens a new chat on the other account, with a fresh stack. The history
    // entries the old stack pushed go too, or Back would pop them with nothing left to show; their
    // popstate finds a one-screen stack and does nothing.
    val switchTo: (String) -> Unit = { id ->
        accounts = accounts.map { it.copy(isActive = it.accountId == id) }
        if (backStack.size > 1) historyGo(-(backStack.size - 1))
        backStack = listOf(Route.Chat("new-${newChats++}"))
    }

    LibreChatTheme(
        darkTheme = dark,
        accentColor = Accents[accent].second,
        uiStyle = if (glass) UiStyle.LIQUID_GLASS else UiStyle.MATERIAL,
    ) {
        // The Android host draws the app on a Surface, which gives text its color; so does the demo.
        Surface(Modifier.fillMaxSize()) {
            GlassSheetHost(Modifier.fillMaxSize()) {
                AppShell(
                    sidebar = { close ->
                        DrawerDemo(
                            accounts = accounts,
                            selectedConversationId = (route as? Route.Chat)?.conversationId,
                            onSelectConversation = { id ->
                                navigate(Route.Chat(id))
                                close()
                            },
                            onNewChat = {
                                navigate(Route.Chat("new-${newChats++}"))
                                close()
                            },
                            onOpenSettings = {
                                navigate(Route.Settings)
                                close()
                            },
                            onOpenAccounts = { showAccounts = true },
                            onSwitchAccount = switchTo,
                            onOpenHit = { id, jump ->
                                openHit(id, jump)
                                close()
                            },
                            onOpenSearch = { query ->
                                navigate(Route.Search(DemoSearchSession(active?.accountId.orEmpty(), query)))
                                close()
                            },
                        )
                    },
                    content = { openDrawer ->
                        when (val current = route) {
                            Route.Settings -> SettingsScreen(
                                onBack = goBack ?: {},
                                glass = glass,
                                onGlass = { glass = it },
                                dark = dark,
                                onDark = { dark = it },
                                accent = accent,
                                onAccent = { accent = it },
                                active = active,
                                onOpenAccounts = { showAccounts = true },
                            )
                            is Route.Chat -> ChatDemo(
                                conversationId = current.conversationId,
                                openDrawer = openDrawer,
                                onBack = goBack,
                                jump = current.jump,
                            )
                            is Route.Search -> SearchDemo(
                                session = current.session,
                                onBack = goBack,
                                onOpenMessage = openHit,
                                onOpenConversation = { navigate(Route.Chat(it)) },
                            )
                        }
                    },
                )
                if (showAccounts) {
                    AccountSwitcherSheet(
                        accounts = accounts.sortedByDescending { it.isActive },
                        onSwitchAccount = { id ->
                            switchTo(id)
                            showAccounts = false
                        },
                        onRemoveAccountRequest = { removing = it },
                        onAddAccount = null,
                        onDismiss = { showAccounts = false },
                    )
                }
                removing?.let { account ->
                    RemoveAccountDialog(
                        account = account,
                        onConfirm = {
                            val rest = accounts.filterNot { it.accountId == account.accountId }
                            val next = rest.firstOrNull { it.isActive } ?: rest.firstOrNull { !it.needsSignIn }
                            accounts = rest.map { it.copy(isActive = it.accountId == next?.accountId) }
                            removing = null
                        },
                        onDismiss = { removing = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    onBack: () -> Unit,
    glass: Boolean,
    onGlass: (Boolean) -> Unit,
    dark: Boolean,
    onDark: (Boolean) -> Unit,
    accent: Int,
    onAccent: (Int) -> Unit,
    active: AccountUiModel?,
    onOpenAccounts: () -> Unit,
) {
    AdaptiveScaffold(
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, "Back", onBack),
                    title = BarTitle("Settings"),
                ),
                materialStyle = MaterialBarStyle(translucent = true),
            )
        },
    ) { padding ->
        AdaptiveGroupedPage(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                item(key = "account-header") { AdaptiveSectionHeader("Account") }
                adaptiveSection {
                    row("account") {
                        Row(
                            Modifier.fillMaxWidth().clickable(onClick = onOpenAccounts).background(adaptiveRowColor)
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (active != null) AccountChip(account = active, onClick = onOpenAccounts)
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(active?.displayLabel ?: "Signed out", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    active?.serverHost.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Chevron()
                        }
                    }
                }

                item(key = "appearance-header") { AdaptiveSectionHeader("Appearance") }
                adaptiveSection {
                    row("style") {
                        LabeledControl("Interface style") {
                            AdaptiveSegmentedChoice(
                                options = listOf("Material", "Liquid Glass"),
                                selectedIndex = if (glass) 1 else 0,
                                onSelect = { onGlass(it == 1) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    row("theme") {
                        LabeledControl("Theme") {
                            AdaptiveSegmentedChoice(
                                options = listOf("Light", "Dark"),
                                selectedIndex = if (dark) 1 else 0,
                                onSelect = { onDark(it == 1) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    row("accent") {
                        LabeledControl("Accent colour") {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Accents.forEachIndexed { index, (name, color) ->
                                    Swatch(color = color, selected = index == accent, name = name) { onAccent(index) }
                                }
                            }
                        }
                    }
                }

                item(key = "bottom") { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

private val SampleAccounts = listOf(
    AccountUiModel("personal", "Sam", "chat.example.com", avatarUrl = null, isActive = true),
    AccountUiModel("work", "Sam (Acme)", "librechat.acme.dev", avatarUrl = null, isActive = false),
    AccountUiModel("family", "Family", "home.example.net", avatarUrl = null, isActive = false),
    AccountUiModel("staging", "Staging", "staging.example.org", avatarUrl = null, isActive = false, needsSignIn = true),
)

@Composable
private fun LabeledControl(label: String, control: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(adaptiveRowColor).padding(16.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.widthIn(max = 480.dp)) { control() }
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, name: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .background(color, CircleShape)
            .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
            .clickable(onClickLabel = name, onClick = onClick),
    )
}

@Composable
private fun Chevron() {
    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}
