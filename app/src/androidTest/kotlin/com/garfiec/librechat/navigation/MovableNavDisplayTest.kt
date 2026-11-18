package com.garfiec.librechat.navigation

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.components.AdaptiveExposedDropdownMenu
import com.garfiec.librechat.core.ui.components.AdaptiveExposedDropdownMenuBox
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.windowUsesSidebarLayout
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

/**
 * Guards the premise of `rememberMovableNavDisplay`: a NavDisplay placed as movable content keeps its
 * entries' ViewModels when the layout around it is swapped in place, as iOS does on rotation, Split
 * View and fold. The two hosts mirror PhoneLayout (inside a ModalNavigationDrawer) and TabletLayout
 * (a custom Layout beside a sidebar), so a subcomposition in either would fail this.
 */
class MovableNavDisplayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private data object Entry

    class EntryViewModel : ViewModel() {
        var cleared = false
            private set

        override fun onCleared() {
            cleared = true
        }
    }

    @Test
    fun entryViewModelSurvivesLayoutSwap() {
        var wide by mutableStateOf(false)
        val seen = mutableListOf<EntryViewModel>()

        composeTestRule.setContent {
            val navDisplay = remember {
                movableContentOf {
                    NavDisplay(
                        backStack = listOf(Entry),
                        onBack = {},
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        entryProvider = entryProvider {
                            entry<Entry> {
                                val vm = viewModel { EntryViewModel() }
                                if (seen.lastOrNull() !== vm) seen += vm
                                Text("entry")
                            }
                        },
                    )
                }
            }
            if (wide) TabletLikeHost { navDisplay() } else PhoneLikeHost { navDisplay() }
        }

        composeTestRule.waitForIdle()
        val before = seen.single()

        wide = true
        composeTestRule.waitForIdle()
        wide = false
        composeTestRule.waitForIdle()

        assertSame(before, seen.last())
        assertFalse(before.cleared)
    }

    @Test
    fun exposedDropdownSurvivesLayoutSwapAfterPick_material() = exposedDropdownSurvivesLayoutSwapAfterPick(UiStyle.MATERIAL)

    @Test
    fun exposedDropdownSurvivesLayoutSwapAfterPick_liquidGlass() =
        exposedDropdownSurvivesLayoutSwapAfterPick(UiStyle.LIQUID_GLASS)

    /**
     * Unfolds and folds back after a pick, switching hosts on [windowUsesSidebarLayout] as WindowLayout
     * does. The configuration changes with the window size, as on a real resize: without that the move
     * doesn't crash.
     */
    private fun exposedDropdownSurvivesLayoutSwapAfterPick(style: UiStyle) {
        var unfolded by mutableStateOf(false)

        composeTestRule.setContent {
            val density = LocalDensity.current
            val windowInfo = remember(density) {
                object : WindowInfo {
                    override val isWindowFocused = true
                    override val containerSize: IntSize
                        get() = with(density) {
                            IntSize((if (unfolded) 840.dp else 412.dp).roundToPx(), 900.dp.roundToPx())
                        }
                }
            }
            val baseConfiguration = LocalConfiguration.current
            val configuration = remember(unfolded) {
                Configuration(baseConfiguration).apply { screenWidthDp = if (unfolded) 840 else 412 }
            }
            CompositionLocalProvider(LocalWindowInfo provides windowInfo, LocalConfiguration provides configuration) {
                LibreChatTheme(uiStyle = style) {
                    val navDisplay = remember {
                        movableContentOf {
                            NavDisplay(
                                backStack = listOf(Entry),
                                onBack = {},
                                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                                entryProvider = entryProvider {
                                    entry<Entry> {
                                        AdaptiveScaffold { padding ->
                                            Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
                                                Picker(options = listOf("Alpha", "Bravo"))
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                    if (windowUsesSidebarLayout()) TabletLikeHost { navDisplay() } else PhoneLikeHost { navDisplay() }
                }
            }
        }

        composeTestRule.onNodeWithText("Alpha").performClick()
        composeTestRule.onNodeWithText("Bravo").performClick()
        composeTestRule.waitForIdle()

        unfolded = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Bravo").assertExists()

        unfolded = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Bravo").assertExists()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Picker(options: List<String>) {
        var expanded by remember { mutableStateOf(false) }
        var selected by remember { mutableStateOf(options.first()) }
        AdaptiveExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            AdaptiveOutlinedTextField(
                value = selected,
                onValueChange = {},
                readOnly = true,
                label = { Text("Role") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            AdaptiveExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            selected = option
                            expanded = false
                        },
                    )
                }
            }
        }
    }

    @Composable
    private fun PhoneLikeHost(content: @Composable () -> Unit) {
        ModalNavigationDrawer(drawerContent = { Text("drawer") }) {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }

    @Composable
    private fun TabletLikeHost(content: @Composable () -> Unit) {
        Layout(
            content = {
                Text("sidebar")
                Box { content() }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val sidebar = measurables[0].measure(constraints.copy(minWidth = 0))
            val main = measurables[1].measure(
                constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth - sidebar.width),
            )
            layout(constraints.maxWidth, constraints.maxHeight) {
                sidebar.placeRelative(0, 0)
                main.placeRelative(sidebar.width, 0)
            }
        }
    }
}
