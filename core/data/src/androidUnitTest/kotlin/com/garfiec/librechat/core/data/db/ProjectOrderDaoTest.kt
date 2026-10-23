package com.garfiec.librechat.core.data.db

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.data.repository.AccountDataPurger
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProjectOrderDaoTest : AccountIsolationTestBase() {

    private val dao get() = db.projectOrderDao()

    private suspend fun idsOf(accountId: String) = dao.positionsForAccount(accountId).map { it.projectId }

    @Test
    fun replacingRewritesOnlyThatAccountsOrder() = runTest {
        dao.replaceForAccount(accountA, listOf("x", "y", "z"))
        dao.replaceForAccount(accountB, listOf("y", "x"))

        dao.replaceForAccount(accountA, listOf("z", "x"))

        assertThat(idsOf(accountA)).containsExactly("z", "x").inOrder()
        assertThat(idsOf(accountB)).containsExactly("y", "x").inOrder()
    }

    @Test
    fun deletingOneProjectLeavesTheOtherAccountsRow() = runTest {
        dao.replaceForAccount(accountA, listOf("x", "y"))
        dao.replaceForAccount(accountB, listOf("x"))

        dao.delete(accountA, "x")

        assertThat(idsOf(accountA)).containsExactly("y")
        assertThat(idsOf(accountB)).containsExactly("x")
    }

    @Test
    fun thePurgerClearsOnlyThePurgedAccountsOrder() = runTest {
        dao.replaceForAccount(accountA, listOf("x"))
        dao.replaceForAccount(accountB, listOf("x"))
        val purger = AccountDataPurger(
            conversationDao = db.conversationDao(),
            messageDao = db.messageDao(),
            draftDao = db.draftDao(),
            tagDao = db.conversationTagDao(),
            prefetchWatermarkDao = db.prefetchWatermarkDao(),
            projectOrderDao = dao,
            ioDispatcher = Dispatchers.Unconfined,
        )

        purger.purge(AccountId(accountA))

        assertThat(idsOf(accountA)).isEmpty()
        assertThat(idsOf(accountB)).containsExactly("x")
    }
}
