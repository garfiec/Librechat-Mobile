package com.garfiec.librechat.core.data.db

import com.garfiec.librechat.core.data.mapper.toEntity
import com.garfiec.librechat.core.data.mapper.toModel
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.asFullDocument
import com.garfiec.librechat.core.model.isCodeConversation
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The cached `isCodeConversation` verdict decides whether the drawer asks for a row's pull request
 * from a cold start: a full read settles it, a stamped list row says false, and anything that says
 * nothing keeps what the cache knew.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConversationCodeVerdictCacheTest : AccountIsolationTestBase() {

    @Test
    fun aFullReadOfAnOrdinaryChatSettlesFalse() {
        val row = Conversation(conversationId = "c1").asFullDocument().toEntity()
        assertThat(row.isCodeConversation).isFalse()
    }

    @Test
    fun anUnstampedListRowIsUnknown() {
        assertThat(Conversation(conversationId = "c1").toEntity().isCodeConversation).isNull()
        assertThat(Conversation(conversationId = "c1", codeDecisionListed = true).toEntity().isCodeConversation).isFalse()
    }

    @Test
    fun theVerdictRoundTripsThroughTheRow() {
        val code = Conversation(conversationId = "c1", codeEnvironmentMode = "attached")
        assertThat(code.toEntity().toModel().isCodeConversation).isTrue()
    }

    @Test
    fun aListUpsertThatSaysNothingKeepsAFullReadsVerdict() = runTest {
        val dao = db.conversationDao()
        val full = Conversation(conversationId = "c1", codeEnvironmentMode = "attached").asFullDocument()
        dao.upsertPreservingTags(accountA, full.toEntity().copy(accountId = accountA))

        dao.upsertPreservingTags(accountA, Conversation(conversationId = "c1", title = "Renamed").toEntity().copy(accountId = accountA))

        val row = dao.getByIdForAccount("c1", accountA)!!
        assertThat(row.title).isEqualTo("Renamed")
        assertThat(row.isCodeConversation).isTrue()
    }

    @Test
    fun aStampedListRowReplacesAnUnknownVerdict() = runTest {
        val dao = db.conversationDao()
        dao.upsertPreservingTags(accountA, Conversation(conversationId = "c1").toEntity().copy(accountId = accountA))

        dao.upsertPreservingTags(
            accountA,
            Conversation(conversationId = "c1", codeDecisionListed = true).toEntity().copy(accountId = accountA),
        )

        assertThat(dao.getByIdForAccount("c1", accountA)!!.isCodeConversation).isFalse()
    }
}
