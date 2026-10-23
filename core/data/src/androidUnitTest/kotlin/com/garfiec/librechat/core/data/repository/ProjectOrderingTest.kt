package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.model.ChatProject
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProjectOrderingTest {

    private fun project(id: String, createdAt: String? = null) = ChatProject(id = id, name = id, createdAt = createdAt)

    @Test
    fun nothingSavedKeepsTheServersOrder() {
        val server = listOf(project("b"), project("a"), project("c"))

        assertThat(ProjectOrdering.arrange(server, emptyMap()).map { it.id }).containsExactly("b", "a", "c").inOrder()
    }

    @Test
    fun savedSlotsWinOverTheServersOrder() {
        val server = listOf(project("a"), project("b"), project("c"))

        val arranged = ProjectOrdering.arrange(server, mapOf("c" to 0L, "a" to 1L, "b" to 2L))

        assertThat(arranged.map { it.id }).containsExactly("c", "a", "b").inOrder()
    }

    @Test
    fun projectsNeverPlacedGoAtTheEndOldestFirst() {
        val server = listOf(
            project("new2", createdAt = "2026-10-20T00:00:00.000Z"),
            project("kept"),
            project("undated"),
            project("new1", createdAt = "2026-10-01T00:00:00.000Z"),
        )

        val arranged = ProjectOrdering.arrange(server, mapOf("kept" to 0L))

        assertThat(arranged.map { it.id }).containsExactly("kept", "new1", "new2", "undated").inOrder()
    }

    @Test
    fun aProjectGoneFromTheServerDropsOut() {
        val arranged = ProjectOrdering.arrange(listOf(project("a")), mapOf("gone" to 0L, "a" to 1L))

        assertThat(arranged.map { it.id }).containsExactly("a")
    }
}
