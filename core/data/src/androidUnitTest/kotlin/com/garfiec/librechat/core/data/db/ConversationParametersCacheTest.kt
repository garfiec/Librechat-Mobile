package com.garfiec.librechat.core.data.db

import com.garfiec.librechat.core.data.mapper.toEntity
import com.garfiec.librechat.core.data.mapper.toModel
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.hasParameters
import com.garfiec.librechat.core.model.parameterPreset
import com.garfiec.librechat.core.model.parametersStale
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

/**
 * A conversation's model parameters ride the cached row (`modelParams`), so a reopened chat sends
 * what it was last sent with. The list route carries none, so its upserts keep what a full read
 * cached.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConversationParametersCacheTest : AccountIsolationTestBase() {

    private val full = Conversation(
        conversationId = "c1",
        endpoint = "openAI",
        model = "o3",
        temperature = 0.3,
        maxContextTokens = 64000,
        reasoningEffort = "high",
        promptPrefix = "Be brief.",
        thinkingBudget = 2048,
        stop = listOf("END"),
    )
    private val listed = Conversation(conversationId = "c1", endpoint = "openAI", model = "o3", title = "Renamed")

    @Test
    fun theParametersRoundTripThroughTheRow() {
        assertThat(full.toEntity().toModel().parameterPreset()).isEqualTo(full.parameterPreset())
    }

    @Test
    fun aConversationWithNoParametersStoresNone() {
        assertThat(listed.toEntity().modelParams).isNull()
        assertThat(listed.toEntity().toModel().hasParameters).isFalse()
    }

    @Test
    fun aListUpsertKeepsTheParametersAFullReadCached() = runTest {
        val dao = db.conversationDao()
        dao.upsertPreservingTags(accountA, full.toEntity().copy(accountId = accountA))

        dao.upsertPreservingTags(accountA, listed.toEntity().copy(accountId = accountA))

        val row = dao.getByIdForAccount("c1", accountA)!!.toModel()
        assertThat(row.title).isEqualTo("Renamed")
        assertThat(row.temperature).isEqualTo(0.3)
        assertThat(row.reasoningEffort).isEqualTo("high")
    }

    @Test
    fun aFullReadAfterAListUpsertStoresItsParameters() = runTest {
        val dao = db.conversationDao()
        dao.upsertPreservingTags(accountA, listed.toEntity().copy(accountId = accountA))

        dao.upsertPreservingTags(accountA, full.copy(temperature = 0.6).toEntity().copy(accountId = accountA))

        assertThat(dao.getByIdForAccount("c1", accountA)!!.toModel().temperature).isEqualTo(0.6)
    }

    @Test
    fun aListUpsertOfALaterVersionMarksTheParametersStale() = runTest {
        val dao = db.conversationDao()
        val read = Instant.fromEpochMilliseconds(1_000)
        dao.upsertPreservingTags(accountA, full.copy(updatedAt = read).toEntity().copy(accountId = accountA))
        assertThat(dao.getByIdForAccount("c1", accountA)!!.toModel().parametersStale).isFalse()

        // Changed elsewhere: the list carries the new updatedAt and no parameters.
        dao.upsertPreservingTags(
            accountA,
            listed.copy(updatedAt = Instant.fromEpochMilliseconds(2_000)).toEntity().copy(accountId = accountA),
        )

        val row = dao.getByIdForAccount("c1", accountA)!!.toModel()
        assertThat(row.temperature).isEqualTo(0.3)
        assertThat(row.parametersAsOf).isEqualTo(read)
        assertThat(row.parametersStale).isTrue()
    }

    @Test
    fun aListUpsertOfTheSameVersionKeepsThemFresh() = runTest {
        val dao = db.conversationDao()
        val read = Instant.fromEpochMilliseconds(1_000)
        dao.upsertPreservingTags(accountA, full.copy(updatedAt = read).toEntity().copy(accountId = accountA))

        dao.upsertPreservingTags(accountA, listed.copy(updatedAt = read).toEntity().copy(accountId = accountA))

        assertThat(dao.getByIdForAccount("c1", accountA)!!.toModel().parametersStale).isFalse()
    }
}
