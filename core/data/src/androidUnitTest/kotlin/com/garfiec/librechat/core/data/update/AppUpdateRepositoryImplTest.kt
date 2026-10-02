package com.garfiec.librechat.core.data.update

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.garfiec.librechat.core.common.AppInfo
import com.garfiec.librechat.core.network.api.GitHubReleasesApi
import com.garfiec.librechat.core.network.di.librechatJson
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Real DataStore on real IO, as in ThemeDataStoreUiStyleTest: DataStore reads on its own scope,
 * which a virtual clock does not wait for.
 */
class AppUpdateRepositoryImplTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun release(tag: String, prerelease: Boolean = false, draft: Boolean = false) = """
        {
          "tag_name": "$tag",
          "html_url": "https://github.com/garfiec/Librechat-Mobile/releases/tag/$tag",
          "published_at": "2026-10-01T05:01:11Z",
          "prerelease": $prerelease,
          "draft": $draft,
          "body": "# Highlights\n\nNotes for $tag\n\n## What's Changed\n* a PR",
          "assets": [
            {"name": "switchboard-$tag.apk.sha256", "browser_download_url": "https://example.test/$tag.sha256"},
            {"name": "switchboard-$tag.apk", "browser_download_url": "https://example.test/$tag.apk"}
          ]
        }
    """.trimIndent()

    private class Harness(val repo: AppUpdateRepositoryImpl, val store: UpdateCheckStore, val requests: () -> Int)

    private fun harness(
        installed: String = "2026.09.0",
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = "[]",
        supported: Boolean = true,
        now: Long = 1_000L,
        hang: Boolean = false,
    ): Harness {
        var requests = 0
        val engine = MockEngine {
            requests++
            if (hang) awaitCancellation()
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) { json(librechatJson) }
        }
        val prefs = PreferenceDataStoreFactory.create(scope = scope) { File(tmpFolder.root, "u.preferences_pb") }
        val store = UpdateCheckStore(prefs)
        val appInfo = object : AppInfo {
            override val versionName = installed
            override val versionCode = 0L
            override val gitSha = "unknown"
        }
        val repo = AppUpdateRepositoryImpl(supported, GitHubReleasesApi(client), store, appInfo) { now }
        return Harness(repo, store, { requests })
    }

    @Test
    fun keepsOnlyStableReleasesNewerThanInstalledNewestFirst() = runBlocking {
        val h = harness(
            body = "[" + listOf(
                release("v2026.10.1-rc1", prerelease = true),
                release("v2026.10.0"),
                release("v2026.09.1"),
                release("v2026.09.0"),
                release("v2026.08.4"),
            ).joinToString() + "]",
        )
        val state = h.repo.check() as UpdateCheckState.Available
        assertThat(state.releases.map { it.tag }).containsExactly("v2026.10.0", "v2026.09.1").inOrder()
        assertThat(state.latest.version).isEqualTo("2026.10.0")
        assertThat(state.latest.apkUrl).isEqualTo("https://example.test/v2026.10.0.apk")
        assertThat(state.latest.highlights).isEqualTo("Notes for v2026.10.0")
        assertThat(h.repo.checkState.value).isEqualTo(state)
        assertThat(h.store.availableTag.first()).isEqualTo("v2026.10.0")
        assertThat(h.store.lastCheckedAt.first()).isEqualTo(1_000L)
    }

    @Test
    fun debugBuildOfLatestIsUpToDate() = runBlocking {
        val h = harness(installed = "2026.10.0-debug", body = "[${release("v2026.10.0")}]")
        assertThat(h.repo.check()).isEqualTo(UpdateCheckState.UpToDate)
        assertThat(h.store.availableTag.first()).isNull()
    }

    @Test
    fun releaseCandidateUserIsToldAboutTheFinalRelease() = runBlocking {
        val h = harness(installed = "2026.10.1-rc2", body = "[${release("v2026.10.1")}]")
        assertThat(h.repo.check()).isInstanceOf(UpdateCheckState.Available::class.java)
    }

    @Test
    fun failureStillStampsLastCheckedAt() = runBlocking {
        val h = harness(status = HttpStatusCode.Forbidden, body = """{"message":"API rate limit exceeded"}""")
        assertThat(h.repo.check()).isInstanceOf(UpdateCheckState.Failed::class.java)
        assertThat(h.store.lastCheckedAt.first()).isEqualTo(1_000L)
    }

    @Test
    fun unsupportedPlatformNeverRequests() = runBlocking {
        val h = harness(supported = false, body = "[${release("v2026.10.0")}]")
        assertThat(h.repo.check()).isEqualTo(UpdateCheckState.Idle)
        assertThat(h.requests()).isEqualTo(0)
    }

    @Test
    fun pendingUpdateNeedsAutoCheckOn() = runBlocking {
        val h = harness(body = "[${release("v2026.10.0")}]")
        h.repo.check()
        assertThat(h.repo.pendingUpdate.first()).isNull()

        h.repo.setAutoCheckEnabled(true)
        assertThat(h.repo.pendingUpdate.first()).isEqualTo(PendingUpdate("v2026.10.0", "2026.10.0"))
    }

    @Test
    fun pendingUpdateClearsOnceNotifiedAndReturnsForANewerTag() = runBlocking {
        val h = harness(body = "[${release("v2026.10.0")}]")
        h.repo.setAutoCheckEnabled(true)
        h.repo.check()
        h.repo.markNotified("v2026.10.0")
        assertThat(h.repo.pendingUpdate.first()).isNull()

        h.store.setAvailableTag("v2026.10.1")
        assertThat(h.repo.pendingUpdate.first()?.tag).isEqualTo("v2026.10.1")
    }

    @Test
    fun pendingUpdateClearsAfterTheUserUpdates() = runBlocking {
        // Tag persisted by a check that ran on the old build; the app is now on that version.
        val h = harness(installed = "2026.10.0")
        h.repo.setAutoCheckEnabled(true)
        h.store.setAvailableTag("v2026.10.0")
        assertThat(h.repo.pendingUpdate.first()).isNull()
    }

    @Test
    fun cancelledCheckDoesNotLeaveCheckingBehind() = runBlocking {
        val h = harness(hang = true)
        val job = launch(Dispatchers.IO) { h.repo.check() }
        while (h.repo.checkState.value != UpdateCheckState.Checking) yield()
        job.cancelAndJoin()
        assertThat(h.repo.checkState.value).isEqualTo(UpdateCheckState.Idle)
    }
}
