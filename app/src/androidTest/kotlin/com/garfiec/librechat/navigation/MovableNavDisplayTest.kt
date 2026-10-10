package com.garfiec.librechat.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
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
