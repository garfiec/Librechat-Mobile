package com.garfiec.librechat.feature.chat.prompts

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.PromptRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.util.PermissionGate
import com.garfiec.librechat.core.model.ProductionPromptEmbed
import com.garfiec.librechat.core.model.PromptGroup
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.feature.chat.prompts.components.PromptSortOrder
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The library sorts, filters and lists its categories on the client (#297), so every one of those
 * is only right over the whole library. Over one page, A–Z would sort only the ten most-used
 * prompts, and a category used only by less popular prompts would get no chip.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PromptsViewModelFullLibraryTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val promptRepository = mockk<PromptRepository>(relaxed = true)
    private val roleRepository = mockk<RoleRepository>(relaxed = true)
    private val permissionGate = mockk<PermissionGate>(relaxed = true)

    /**
     * In the server's order, most used first. Names run backwards against that order, so A–Z over
     * any leading slice starts well past "p01"; the one `late` group sits past the first ten.
     */
    private val library = (0 until LIBRARY_SIZE).map { i ->
        PromptGroup(
            id = "g-$i",
            name = "p" + (LIBRARY_SIZE - i).toString().padStart(2, '0'),
            numberOfGenerations = 100 - i,
            category = if (i == LATE_INDEX) "late" else "general",
            author = "author-1",
            authorName = "Author",
            productionId = "g-$i-p",
            productionPrompt = ProductionPromptEmbed(prompt = "body $i"),
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { roleRepository.userPermissions } returns MutableStateFlow(null)
        every { promptRepository.revision } returns MutableStateFlow(0L)
        coEvery { permissionGate.awaitRole() } returns UserRolePermissions(name = "USER")
        coEvery { promptRepository.getAllGroups() } returns Result.Success(library)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun everyPromptInTheLibraryIsListed() = runTest(testDispatcher) {
        val viewModel = PromptsViewModel(promptRepository, roleRepository, permissionGate)
        advanceUntilIdle()

        assertEquals(LIBRARY_SIZE, viewModel.uiState.value.groups.size)
    }

    @Test
    fun aCategoryUsedOnlyByALessPopularPromptStillGetsAChip() = runTest(testDispatcher) {
        val viewModel = PromptsViewModel(promptRepository, roleRepository, permissionGate)
        advanceUntilIdle()

        assertTrue("late" in viewModel.uiState.value.availableCategories)

        viewModel.onCategorySelected("late")
        assertEquals(listOf("p05"), viewModel.uiState.value.filteredGroups.map { it.name })
    }

    @Test
    fun alphabeticalSortCoversTheWholeLibrary() = runTest(testDispatcher) {
        val viewModel = PromptsViewModel(promptRepository, roleRepository, permissionGate)
        advanceUntilIdle()

        viewModel.onSortOrderChanged(PromptSortOrder.ALPHABETICAL)

        val expected = (1..LIBRARY_SIZE).map { "p" + it.toString().padStart(2, '0') }
        assertEquals(expected, viewModel.uiState.value.filteredGroups.map { it.name })
    }

    private companion object {
        const val LIBRARY_SIZE = 25
        const val LATE_INDEX = 20
    }
}
