package com.garfiec.librechat.core.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.garfiec.librechat.core.common.ChannelPolicy
import com.garfiec.librechat.core.common.DistributionChannel
import com.garfiec.librechat.core.data.datastore.ArtifactLibraryConsentStore.Decision
import com.garfiec.librechat.core.data.datastore.ArtifactLibraryConsentStore.Subject
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ArtifactLibraryConsentStoreTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val vaul = Subject("vaul", "unmaintained")
    private val datGui = Subject("dat.gui", "unmaintained")

    private fun TestScope.newStore(
        file: String = "consent",
        scope: CoroutineScope = backgroundScope,
    ): ArtifactLibraryConsentStore {
        val ds = PreferenceDataStoreFactory.create(scope = scope) {
            File(tmpFolder.root, "$file.preferences_pb")
        }
        return ArtifactLibraryConsentStore(PreferenceStore(ds, ChannelPolicy(DistributionChannel.OPEN_SOURCE)), scope)
    }

    private suspend fun ArtifactLibraryConsentStore.decision(subject: Subject, artifact: String): Decision =
        snapshot.filterNotNull().first().decisionFor(subject, artifact)

    @Test
    fun `an unanswered library asks`() = runTest(UnconfinedTestDispatcher()) {
        assertThat(newStore().decision(vaul, "a")).isEqualTo(Decision.ASK)
    }

    @Test
    fun `a once answer holds for that artifact only`() = runTest(UnconfinedTestDispatcher()) {
        val store = newStore()
        store.decideOnce(vaul, "a", allow = false)
        assertThat(store.decision(vaul, "a")).isEqualTo(Decision.DECLINE)
        assertThat(store.decision(vaul, "b")).isEqualTo(Decision.ASK)
        assertThat(store.decision(datGui, "a")).isEqualTo(Decision.ASK)
    }

    @Test
    fun `a remembered answer wins over a once answer, everywhere`() = runTest(UnconfinedTestDispatcher()) {
        val store = newStore()
        store.decideOnce(vaul, "a", allow = false)
        store.remember(vaul, allow = true)
        assertThat(store.decision(vaul, "a")).isEqualTo(Decision.ALLOW)
        assertThat(store.decision(vaul, "b")).isEqualTo(Decision.ALLOW)
    }

    @Test
    fun `remembering again replaces the earlier answer`() = runTest(UnconfinedTestDispatcher()) {
        val store = newStore()
        store.remember(vaul, allow = true)
        store.remember(vaul, allow = false)
        assertThat(store.decision(vaul, "a")).isEqualTo(Decision.DECLINE)
    }

    @Test
    fun `a yes to one advisory is not a yes to a worse one`() = runTest(UnconfinedTestDispatcher()) {
        val store = newStore()
        store.remember(datGui, allow = true)
        assertThat(store.decision(Subject("dat.gui", "vulnerable"), "a")).isEqualTo(Decision.ASK)
    }

    @Test
    fun `reconsider takes back the remembered answer and this artifact's once answer only`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = newStore()
            store.decideOnce(vaul, "a", allow = false)
            store.decideOnce(vaul, "b", allow = false)
            store.reconsider(vaul, "a")
            assertThat(store.decision(vaul, "a")).isEqualTo(Decision.ASK)
            assertThat(store.decision(vaul, "b")).isEqualTo(Decision.DECLINE)

            store.remember(datGui, allow = true)
            store.reconsider(datGui, "a")
            assertThat(store.decision(datGui, "c")).isEqualTo(Decision.ASK)
        }

    @Test
    fun `forget drops the remembered answer only`() = runTest(UnconfinedTestDispatcher()) {
        val store = newStore()
        store.decideOnce(vaul, "a", allow = true)
        store.decideOnce(vaul, "b", allow = false)
        store.remember(datGui, allow = true)
        store.remember(vaul, allow = false)
        assertThat(store.snapshot.filterNotNull().first().remembered(vaul)).isFalse()

        store.forget(vaul)

        val snapshot = store.snapshot.filterNotNull().first()
        assertThat(snapshot.remembered(vaul)).isNull()
        assertThat(snapshot.decisionFor(vaul, "a")).isEqualTo(Decision.ALLOW)
        assertThat(snapshot.decisionFor(vaul, "b")).isEqualTo(Decision.DECLINE)
        assertThat(snapshot.decisionFor(vaul, "c")).isEqualTo(Decision.ASK)
        assertThat(snapshot.remembered(datGui)).isTrue()
    }

    @Test
    fun `the generated catalog names only well-formed subjects`() {
        assertThat(FlaggedArtifactLibraries.SUBJECTS).isNotEmpty()
        FlaggedArtifactLibraries.SUBJECTS.forEach { subject ->
            assertThat(subject.library).doesNotContain("|")
            assertThat(subject.advisory).isAnyOf("unmaintained", "deprecated", "vulnerable")
        }
    }

    @Test
    fun `remembered answers survive a new store, once answers do not`() = runTest(UnconfinedTestDispatcher()) {
        // A second DataStore on the same file may only open once the first one's scope is gone,
        // which is also what a process restart looks like to the store.
        val firstJob = Job(backgroundScope.coroutineContext[Job])
        val first = newStore("persist", CoroutineScope(backgroundScope.coroutineContext + firstJob))
        first.remember(vaul, allow = false)
        first.decideOnce(datGui, "a", allow = true)
        firstJob.cancelAndJoin()
        val second = newStore("persist")
        assertThat(second.decision(vaul, "a")).isEqualTo(Decision.DECLINE)
        assertThat(second.decision(datGui, "a")).isEqualTo(Decision.ASK)
    }
}
