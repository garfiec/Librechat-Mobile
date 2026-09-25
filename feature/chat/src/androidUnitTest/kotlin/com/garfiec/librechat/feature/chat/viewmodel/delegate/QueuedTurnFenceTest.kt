package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.data.endpoint.EndpointDispatch
import com.garfiec.librechat.core.data.repository.QueuedTurnRepository
import com.garfiec.librechat.core.model.queuedturn.AgentQueuedTurnReceipt
import com.garfiec.librechat.core.model.queuedturn.QueuedTurnOutcome
import com.garfiec.librechat.core.model.queuedturn.QueuedTurnReceiptSource
import com.garfiec.librechat.core.model.queuedturn.QueuedTurnStatus
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.QueueHandle
import com.garfiec.librechat.feature.chat.viewmodel.QueuedMessage
import com.garfiec.librechat.feature.chat.viewmodel.QueuedTurnServerState
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Where [QueuedTurnDelegate] and [MessageQueueDelegate] meet: the evidence one keeps and the
 * fence the other reads.
 *
 * Both delegates pass their own suites while this composition is broken, which is the whole
 * reason this file exists. The evidence is retained by one, matched by the other, and the only
 * thing standing between a server-owned turn and the same words being submitted twice is that
 * the two agree about what counts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QueuedTurnFenceTest {

    private val stateFlow = MutableStateFlow(ChatUiState())
    private val scope = TestScope()
    private val stateHandle = ChatStateHandle(stateFlow, scope)

    private val sent = mutableListOf<QueuedMessage>()
    private var successorOwedCount = 0

    private val queueDelegate = MessageQueueDelegate(
        handle = QueueHandle(stateHandle),
        activeAccountProvider = InMemoryActiveAccountProvider(AccountState.Resolved(AccountId("srv:user-1"))),
        sendWithSpec = { spec, _ -> sent.add(spec) },
        onQueuedDropped = { },
    )

    private val queuedTurnDelegate = QueuedTurnDelegate(
        handle = QueueHandle(stateHandle),
        repository = mockk<QueuedTurnRepository>(relaxed = true),
        onSuccessorOwed = { successorOwedCount++; true },
        projectOrphan = { null },
        nowMillis = { NOW },
    )

    private fun legacyRow(id: String) = QueuedMessage(
        localId = id,
        text = id,
        endpoint = "agents",
        model = "agent_abc",
        agentId = "agent_abc",
        dispatch = EndpointDispatch(endpointType = "agents", key = null, modelDisplayLabel = null),
    )

    private fun receipt(
        status: String,
        clientRequestId: String = "req-1",
        effectivePredecessorCreatedAt: Long? = null,
        rootPredecessor: Boolean? = null,
    ) = AgentQueuedTurnReceipt(
        queuedTurnId = "qt-1",
        clientRequestId = clientRequestId,
        conversationId = "conv-1",
        status = status,
        revision = 1,
        effectivePredecessorCreatedAt = effectivePredecessorCreatedAt,
        rootPredecessor = rootPredecessor,
    )

    /** An admission the server reported against the boundary [EPOCH], with a legacy row behind it. */
    private fun admitLegacyBehind(rootPredecessor: Boolean? = null) {
        queuedTurnDelegate.applyReceipts(
            listOf(
                receipt(
                    QueuedTurnStatus.ADMITTED,
                    effectivePredecessorCreatedAt = EPOCH,
                    rootPredecessor = rootPredecessor,
                ),
            ),
            QueuedTurnReceiptSource.Snapshot,
        )
        queueDelegate.enqueue(legacyRow("legacy"))
    }

    /**
     * The row leaves the queue the moment the poll sees it admitted, so the evidence is all that
     * is left when the run's own `Final` lands moments later. A legacy follow-up draining into
     * that boundary is a second concurrent turn in the same conversation.
     */
    @Test
    fun `an admitted turn fences the run end it consumed`() {
        admitLegacyBehind()

        queueDelegate.drainNext(endedGenerationCreatedAt = EPOCH)

        assertThat(sent).isEmpty()
    }

    /**
     * The same fence, reached through "Send queued". [MessageQueueDelegate.resume] passes no
     * boundary epoch — nor does the ViewModel's idle re-drain — so an evidence check that only
     * runs when one is supplied is never consulted on either path, and the admission's successor
     * and this row run at once.
     */
    @Test
    fun `send queued does not fire a legacy row past an unconsumed admission`() {
        admitLegacyBehind()
        queueDelegate.pause()

        queueDelegate.resume()

        assertThat(sent).isEmpty()
    }

    /** Same hole, reached from the idle re-drain rather than the pause. */
    @Test
    fun `an idle re-drain does not fire a legacy row past an unconsumed admission`() {
        admitLegacyBehind()

        queueDelegate.drainNext(awaitSettle = false)

        assertThat(sent).isEmpty()
    }

    /**
     * Blocking must not consume. Only the epoch-matched run end retires an admission, so a
     * "Send queued" tap before the run finishes cannot be what burns the fence the real `Final`
     * still needs.
     */
    @Test
    fun `a blocked resume leaves the fence armed for the real run end`() {
        admitLegacyBehind()
        queueDelegate.pause()
        queueDelegate.resume()

        queueDelegate.drainNext(endedGenerationCreatedAt = EPOCH)

        assertThat(sent).isEmpty()
        // One admission fences one boundary and no more: the NEXT run end is free to drain.
        queueDelegate.drainNext(endedGenerationCreatedAt = EPOCH + 1)
        assertThat(sent.map { it.localId }).containsExactly("legacy")
    }

    /**
     * The fence has to be retirable by something other than the boundary's own `Final`, or a run
     * that dies without one leaves "Send queued" refusing for the ViewModel's life with nothing
     * on screen to explain it. Attaching to a server-started run is that something.
     */
    @Test
    fun `attaching to a later run retires the admission it overtook`() {
        admitLegacyBehind()
        queueDelegate.pause()
        // The predecessor died without a Final, so nothing consumed the evidence.
        queueDelegate.resume()
        assertThat(sent).isEmpty()

        queueDelegate.retireAdmissionsBefore(EPOCH + 1)
        queueDelegate.resume()

        assertThat(sent.map { it.localId }).containsExactly("legacy")
    }

    /**
     * The run now streaming is the one an admission anchored to it is owed a successor AFTER, so
     * retiring that one would unfence the very boundary the evidence exists for.
     */
    @Test
    fun `attaching does not retire an admission anchored to the run itself`() {
        admitLegacyBehind()

        queueDelegate.retireAdmissionsBefore(EPOCH)

        queueDelegate.drainNext(endedGenerationCreatedAt = EPOCH)
        assertThat(sent).isEmpty()
    }

    /**
     * A receipt can carry a reported boundary AND `rootPredecessor`. Retention keyed on the flag
     * rather than on the timestamp throws that evidence away while the fence still needs it, so
     * `Final` finds nothing and drains into a boundary the admission already consumed.
     */
    @Test
    fun `an admission reporting both a boundary and a root flag is still retained`() {
        admitLegacyBehind(rootPredecessor = true)

        queueDelegate.drainNext(endedGenerationCreatedAt = EPOCH)

        assertThat(sent).isEmpty()
    }

    /** A genuine root admission consumed no boundary, so it must not fence — or block — anything. */
    @Test
    fun `a root admission with no boundary fences nothing`() {
        queuedTurnDelegate.applyReceipts(
            listOf(receipt(QueuedTurnStatus.ADMITTED, rootPredecessor = true)),
            QueuedTurnReceiptSource.Snapshot,
        )
        queueDelegate.enqueue(legacyRow("legacy"))

        queueDelegate.drainNext(endedGenerationCreatedAt = EPOCH)

        assertThat(sent.map { it.localId }).containsExactly("legacy")
    }

    /**
     * The announce fires on the FIRST sighting of a turn, which is `queued` — while this client
     * is still streaming and has nothing to attach to. If the id is remembered then, the
     * `admitted` receipt that actually needs the look-up is deduped away and the follow-up
     * completes server-side against an idle screen.
     */
    @Test
    fun `admission re-announces a turn already seen queued`() {
        queuedTurnDelegate.applyReceipts(
            listOf(receipt(QueuedTurnStatus.QUEUED)),
            QueuedTurnReceiptSource.Snapshot,
        )
        val afterQueued = successorOwedCount

        queuedTurnDelegate.applyReceipts(
            listOf(receipt(QueuedTurnStatus.ADMITTED, effectivePredecessorCreatedAt = EPOCH)),
            QueuedTurnReceiptSource.Snapshot,
        )

        assertThat(afterQueued).isEqualTo(1)
        assertThat(successorOwedCount).isEqualTo(2)
    }

    /** …but a row simply sitting in the queue across polls must not spend a lookup every tick. */
    @Test
    fun `an unchanged queued row is announced only once`() {
        repeat(3) {
            queuedTurnDelegate.applyReceipts(
                listOf(receipt(QueuedTurnStatus.QUEUED)),
                QueuedTurnReceiptSource.Snapshot,
            )
        }

        assertThat(successorOwedCount).isEqualTo(1)
    }

    /**
     * What the cancel route's refusals mean, which is not what the ENQUEUE route's mean.
     *
     * `isDefiniteQueuedTurnRejection` answers "the row was never committed" and every bounded 4xx
     * qualifies — correct for an enqueue, and backwards for a withdrawal's 404. The route answers
     * `QUEUED_TURN_NOT_FOUND` when the turn is not in its queue, which is the withdrawal's own
     * goal already met; reported as a refusal the row stays, its × is a permanent no-op, and that
     * row then refuses every drain for the life of the ViewModel.
     */
    @Test
    fun `a cancel the server has no row for is a successful withdrawal`() = runTest {
        val repository = mockk<QueuedTurnRepository>(relaxed = true)
        val delegate = QueuedTurnDelegate(
            handle = QueueHandle(stateHandle),
            repository = repository,
            onSuccessorOwed = { true },
            projectOrphan = { null },
            nowMillis = { NOW },
        )
        val row = legacyRow("row").copy(
            server = QueuedTurnServerState(status = QueuedTurnServerState.Status.Queued, id = "qt-1"),
        )

        coEvery { repository.cancel("qt-1") } returns
            QueuedTurnOutcome.Rejected(HTTP_NOT_FOUND, "QUEUED_TURN_NOT_FOUND", null)
        assertThat(delegate.cancel(row)).isTrue()

        // 409 QUEUED_TURN_ALREADY_ADMITTING is the case the refusal arm exists for: the server
        // still intends to run it, so dropping the row locally hides a turn that will happen.
        coEvery { repository.cancel("qt-1") } returns
            QueuedTurnOutcome.Rejected(HTTP_CONFLICT, "QUEUED_TURN_ALREADY_ADMITTING", null)
        assertThat(delegate.cancel(row)).isFalse()

        // An unrevealed outcome is not evidence either way and must never drop the row.
        coEvery { repository.cancel("qt-1") } returns QueuedTurnOutcome.Indeterminate(null, null, null)
        assertThat(delegate.cancel(row)).isFalse()
    }

    private companion object {
        const val EPOCH = 1_758_000_000_000L
        const val NOW = 1_758_000_050_000L
        const val HTTP_NOT_FOUND = 404
        const val HTTP_CONFLICT = 409
    }
}
