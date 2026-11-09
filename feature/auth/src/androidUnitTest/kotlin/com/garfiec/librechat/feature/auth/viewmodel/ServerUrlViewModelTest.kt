package com.garfiec.librechat.feature.auth.viewmodel

import com.garfiec.librechat.core.common.result.FailureKind
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.message
import com.garfiec.librechat.core.data.datastore.AccountEntry
import com.garfiec.librechat.core.data.datastore.AccountRoster
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.AccountSwitcher
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.HeaderWriteFailure
import com.garfiec.librechat.core.data.repository.HeaderWriteResult
import com.garfiec.librechat.core.data.repository.ServerRepository
import com.garfiec.librechat.core.data.repository.ServerResolution
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.network.client.HeaderRejection
import com.garfiec.librechat.core.ui.components.CustomHeaderRow
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The add-account mode contract: validating a server being ADDED must never touch the live
 * account's state — no global URL write (the bearer-to-wrong-host leak) and no config
 * publish/cache (that's the srv:-key poisoning). In both modes nothing is stored until the server
 * has been found, and what is stored is the address that answered.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ServerUrlViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val serverDataStore = mockk<ServerDataStore>(relaxed = true)
    private val serverRepository = mockk<ServerRepository>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)
    private val accountSwitcher = mockk<AccountSwitcher>(relaxed = true)
    private val rosterEntries = MutableStateFlow<List<AccountEntry>>(emptyList())
    private val accountRoster = mockk<AccountRoster> { every { entriesFlow() } returns rosterEntries }

    private val pendingUrl = "https://b.example.com"
    private val config = StartupConfig(serverDomain = pendingUrl)

    private val liveUrl = "https://live.example.com"
    private val liveHeaders = mapOf("CF-Access-Client-Id" to "id-value")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        // A relaxed default is not HeaderWriteResult.Saved, and Connect stops on anything else —
        // reading it as "the credential never reached disk". Default to a store that works; the
        // tests about failure say so.
        coEvery { serverRepository.setHeaders(any(), any()) } returns HeaderWriteResult.Saved
        // The typed address answers unless a test says otherwise. A relaxed mock can't make up a
        // sealed result.
        coEvery { configRepository.resolveServer(any(), any()) } coAnswers {
            ServerResolution.Resolved(firstArg<List<String>>().first(), config)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(addAccount: Boolean, cleartextBlockedForDomainNames: Boolean = false) =
        ServerUrlViewModel(
            serverDataStore = serverDataStore,
            serverRepository = serverRepository,
            configRepository = configRepository,
            accountSwitcher = accountSwitcher,
            accountRoster = accountRoster,
            addAccount = addAccount,
            cleartextBlockedForDomainNames = cleartextBlockedForDomainNames,
        )

    @Test
    fun `normal mode does not publish config when detaching the old session fails`() = runTest(testDispatcher) {
        coEvery { accountSwitcher.prepareForSignIn(any()) } throws IllegalStateException("storage unavailable")
        val viewModel = createViewModel(addAccount = false)
        advanceUntilIdle()

        viewModel.onUrlChanged(pendingUrl)
        viewModel.validateAndConnect()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isValidated).isFalse()
        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(viewModel.uiState.value.error).isNotNull()
        coVerify(exactly = 0) { configRepository.publishStartupConfig(any()) }
        coVerify(exactly = 0) { serverDataStore.setServerUrl(any()) }
    }

    @Test
    fun `add mode starts a pending session and never touches the global URL`() = runTest(testDispatcher) {
        val viewModel = createViewModel(addAccount = true)
        advanceUntilIdle()

        viewModel.onUrlChanged(pendingUrl)
        viewModel.validateAndConnect()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isValidated).isTrue()
        coVerify(exactly = 1) { accountSwitcher.beginAdd(pendingUrl) }
        coVerify(exactly = 1) { accountSwitcher.attachPendingConfig(config) }
        coVerify(exactly = 0) { accountSwitcher.prepareForSignIn(any()) }
        coVerify(exactly = 0) { serverDataStore.setServerUrl(any()) }
        coVerify(exactly = 0) { configRepository.publishStartupConfig(any()) }
    }

    /**
     * `isValidated` is a one-shot navigation signal, and the screen re-fires its navigate effect
     * whenever it re-composes with the flag still set. Since this ViewModel outlives the forward
     * navigation (it sits in the nav back stack), a latched flag would throw the user forward again
     * the moment they pop BACK onto the screen — in add-account mode, an inescapable loop that only
     * force-stopping the app can break.
     */
    @Test
    fun `the validated signal is consumable so back does not bounce forward`() = runTest(testDispatcher) {
        val viewModel = createViewModel(addAccount = true)
        advanceUntilIdle()

        viewModel.onUrlChanged(pendingUrl)
        viewModel.validateAndConnect()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isValidated).isTrue()

        viewModel.consumeValidated()

        assertThat(viewModel.uiState.value.isValidated).isFalse()
    }

    /** Consuming must not wedge the screen: a second Connect has to navigate again. */
    @Test
    fun `connecting again after a consume re-raises the signal`() = runTest(testDispatcher) {
        val viewModel = createViewModel(addAccount = true)
        advanceUntilIdle()

        viewModel.onUrlChanged(pendingUrl)
        viewModel.validateAndConnect()
        advanceUntilIdle()
        viewModel.consumeValidated()

        viewModel.validateAndConnect()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isValidated).isTrue()
    }

    @Test
    fun `add mode validation failure cancels the pending session and surfaces the error`() =
        runTest(testDispatcher) {
            coEvery { configRepository.resolveServer(any(), any()) } returns
                ServerResolution.Failed(Result.Error(message = "not librechat"))
            val viewModel = createViewModel(addAccount = true)
            advanceUntilIdle()

            viewModel.onUrlChanged(pendingUrl)
            viewModel.validateAndConnect()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isValidated).isFalse()
            assertThat(viewModel.uiState.value.error).isEqualTo("not librechat")
            coVerify(exactly = 1) { accountSwitcher.cancelAdd() }
            coVerify(exactly = 0) { accountSwitcher.beginAdd(any()) }
            coVerify(exactly = 0) { accountSwitcher.prepareForSignIn(any()) }
            coVerify(exactly = 0) { serverDataStore.setServerUrl(any()) }
        }

    @Test
    fun `add mode surfaces a beginAdd failure as an error instead of crashing`() = runTest(testDispatcher) {
        coEvery { accountSwitcher.beginAdd(any()) } throws IllegalStateException("no active account")
        val viewModel = createViewModel(addAccount = true)
        advanceUntilIdle()

        viewModel.onUrlChanged(pendingUrl)
        viewModel.validateAndConnect()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isValidated).isFalse()
        assertThat(viewModel.uiState.value.error).isNotNull()
    }

    @Test
    fun `normal mode never stores a URL that failed to resolve`() = runTest(testDispatcher) {
        coEvery { configRepository.resolveServer(any(), any()) } returns
            ServerResolution.Failed(Result.Error(message = "nope"))
        val viewModel = createViewModel(addAccount = false)
        advanceUntilIdle()

        viewModel.onUrlChanged(pendingUrl)
        viewModel.validateAndConnect()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo("nope")
        coVerify(exactly = 0) { accountSwitcher.prepareForSignIn(any()) }
        coVerify(exactly = 0) { serverDataStore.setServerUrl(any()) }
        coVerify(exactly = 0) { configRepository.publishStartupConfig(any()) }
        coVerify(exactly = 0) { accountSwitcher.beginAdd(any()) }
    }

    /** The config's cache key derives from the live URL, so the URL has to be stored first. */
    @Test
    fun `normal mode stores the resolved URL, then publishes its config`() = runTest(testDispatcher) {
        val viewModel = createViewModel(addAccount = false)
        advanceUntilIdle()

        viewModel.onUrlChanged(pendingUrl)
        viewModel.validateAndConnect()
        advanceUntilIdle()

        coVerifyOrder {
            configRepository.resolveServer(listOf(pendingUrl), any())
            accountSwitcher.prepareForSignIn(pendingUrl)
            configRepository.publishStartupConfig(config)
        }
        assertThat(viewModel.uiState.value.isValidated).isTrue()
    }

    /**
     * Add mode starts empty: the live server is a known-server row instead. Its headers are a
     * credential and must never be read into a screen about a different account.
     */
    @Test
    fun `add mode starts with an empty field and never reads the active server's headers`() =
        runTest(testDispatcher) {
            coEvery { serverDataStore.awaitBaseUrl() } returns liveUrl
            coEvery { serverRepository.headersForServer(any()) } returns liveHeaders

            val viewModel = createViewModel(addAccount = true)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.url).isEmpty()
            assertThat(viewModel.uiState.value.customHeaders).isEmpty()
            assertThat(viewModel.uiState.value.showAdvanced).isFalse()
            coVerify(exactly = 0) { serverRepository.headersForServer(any()) }
        }

    /** The same prefill in normal mode DOES restore the headers — that's what makes logout survivable. */
    @Test
    fun `normal mode prefills the saved headers and opens the advanced section`() = runTest(testDispatcher) {
        coEvery { serverDataStore.awaitBaseUrl() } returns liveUrl
        coEvery { serverRepository.headersForServer(liveUrl) } returns liveHeaders

        val viewModel = createViewModel(addAccount = false)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.customHeaders)
            .containsExactly(CustomHeaderRow("CF-Access-Client-Id", "id-value"))
        assertThat(viewModel.uiState.value.showAdvanced).isTrue()
    }

    /**
     * The probe is the FIRST request to a gateway-protected server, so it is exactly the request that
     * has to already carry the headers. Persisting them in a separate coroutine (or after the probe)
     * would make the first Connect fail and the second succeed — indistinguishable from flakiness.
     */
    @Test
    fun `normal mode persists the headers before probing the server`() = runTest(testDispatcher) {
        val viewModel = createViewModel(addAccount = false)
        advanceUntilIdle()

        viewModel.onUrlChanged(pendingUrl)
        viewModel.addHeaderRow()
        viewModel.onHeaderNameChanged(0, "CF-Access-Client-Id")
        // Padded on purpose: pasting a token out of a dashboard is the likely entry path, and the
        // trailing whitespace must be normalized away before it is persisted.
        viewModel.onHeaderValueChanged(0, "  id-value  ")
        viewModel.validateAndConnect()
        advanceUntilIdle()

        coVerifyOrder {
            serverRepository.setHeaders(pendingUrl, mapOf("CF-Access-Client-Id" to "id-value"))
            configRepository.resolveServer(listOf(pendingUrl), mapOf("CF-Access-Client-Id" to "id-value"))
            accountSwitcher.prepareForSignIn(pendingUrl)
        }
    }

    /**
     * Add mode's deadline is earlier still: `beginAdd` mints the PendingRequestIdentity whose headers
     * lambda reads the store, so a save landing after it probes with an empty header set.
     */
    @Test
    fun `add mode persists the headers before beginAdd mints the pending identity`() = runTest(testDispatcher) {
        val viewModel = createViewModel(addAccount = true)
        advanceUntilIdle()

        viewModel.onUrlChanged(pendingUrl)
        viewModel.addHeaderRow()
        viewModel.onHeaderNameChanged(0, "CF-Access-Client-Id")
        viewModel.onHeaderValueChanged(0, "id-value")
        viewModel.validateAndConnect()
        advanceUntilIdle()

        coVerifyOrder {
            serverRepository.setHeaders(pendingUrl, mapOf("CF-Access-Client-Id" to "id-value"))
            configRepository.resolveServer(listOf(pendingUrl), mapOf("CF-Access-Client-Id" to "id-value"))
            accountSwitcher.beginAdd(pendingUrl)
        }
    }

    /**
     * The single-tap "add another account on the same server" flow the init comment advertises. Add
     * mode prefills the ACTIVE server's URL with an empty editor, so an unconditional save writes an
     * empty map — and an empty map means "delete this server's headers" to the store. That wipes the
     * live session's gateway credential, which is plaintext-only and has no second copy.
     */
    @Test
    fun `add mode tapping a signed-in server never writes over its headers`() =
        runTest(testDispatcher) {
            coEvery { serverRepository.headersForServer(liveUrl) } returns liveHeaders

            val viewModel = createViewModel(addAccount = true)
            advanceUntilIdle()

            viewModel.connectTo(liveUrl)
            advanceUntilIdle()

            coVerify(exactly = 0) { serverRepository.setHeaders(any(), any()) }
            // The stored credential still rides the probe and the pending session.
            coVerify { configRepository.resolveServer(listOf(liveUrl), liveHeaders) }
            coVerify { accountSwitcher.beginAdd(liveUrl) }
        }

    /**
     * The untouched-editor test above only covers a user who never opened Advanced. Touching a row
     * and leaving it blank is the same situation — add mode's editor starts empty *by design*, so it
     * never represented the active server's stored headers and an empty map from it is not a clear.
     * The URL is the active server's, so writing one deletes a live session's credential.
     */
    @Test
    fun `add mode with a touched but blank editor still writes nothing`() = runTest(testDispatcher) {
        val viewModel = createViewModel(addAccount = true)
        advanceUntilIdle()

        viewModel.onUrlChanged(liveUrl)
        viewModel.addHeaderRow()
        viewModel.validateAndConnect()
        advanceUntilIdle()

        coVerify(exactly = 0) { serverRepository.setHeaders(any(), any()) }
    }

    /** Typing a credential in add mode is a real edit and must still reach the store. */
    @Test
    fun `add mode still saves headers the user actually typed`() = runTest(testDispatcher) {
        val viewModel = createViewModel(addAccount = true)
        advanceUntilIdle()

        viewModel.onUrlChanged(pendingUrl)
        viewModel.addHeaderRow()
        viewModel.onHeaderNameChanged(0, "CF-Access-Client-Id")
        viewModel.onHeaderValueChanged(0, "id-value")
        viewModel.validateAndConnect()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            serverRepository.setHeaders(pendingUrl, mapOf("CF-Access-Client-Id" to "id-value"))
        }
    }

    /**
     * The refusal message tells the user to re-enter the credential, and it renders in the same slot
     * as the load warning that explains why. Leaving it up while they comply means a stale error over
     * valid input, with the explanation suppressed behind it.
     */
    @Test
    fun `editing a row clears a stale store failure`() = runTest(testDispatcher) {
        coEvery { serverRepository.setHeaders(any(), any()) } returns
            HeaderWriteResult.Refused(HeaderWriteFailure.UnverifiedDelete)

        val viewModel = createViewModel(addAccount = false)
        advanceUntilIdle()

        viewModel.onUrlChanged(pendingUrl)
        viewModel.addHeaderRow()
        viewModel.removeHeaderRow(0)
        viewModel.validateAndConnect()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.headersSaveFailure).isNotNull()

        viewModel.addHeaderRow()

        assertThat(viewModel.uiState.value.headersSaveFailure).isNull()
    }

    /** Same guard pre-login: an editor that never loaded must not be mistaken for a cleared one. */
    @Test
    fun `normal mode with an untouched editor does not rewrite stored headers`() = runTest(testDispatcher) {
        coEvery { serverDataStore.awaitBaseUrl() } returns liveUrl
        coEvery { serverRepository.headersForServer(liveUrl) } returns liveHeaders

        val viewModel = createViewModel(addAccount = false)
        advanceUntilIdle()

        viewModel.validateAndConnect()
        advanceUntilIdle()

        coVerify(exactly = 0) { serverRepository.setHeaders(any(), any()) }
    }

    /** Clearing every row IS an edit, so it must still reach the store as an empty map. */
    @Test
    fun `removing the last row persists the clear`() = runTest(testDispatcher) {
        coEvery { serverDataStore.awaitBaseUrl() } returns liveUrl
        coEvery { serverRepository.headersForServer(liveUrl) } returns liveHeaders

        val viewModel = createViewModel(addAccount = false)
        advanceUntilIdle()

        viewModel.removeHeaderRow(0)
        viewModel.validateAndConnect()
        advanceUntilIdle()

        coVerify(exactly = 1) { serverRepository.setHeaders(liveUrl, emptyMap()) }
    }

    /**
     * Both init awaits can outlast the first frame, and `headersForServer` adds a second suspension on
     * the header store's warm-up. A user typing a rotated token in that window must not have it
     * replaced by the stale persisted set.
     */
    @Test
    fun `a late prefill does not clobber rows the user already typed`() = runTest(testDispatcher) {
        coEvery { serverDataStore.awaitBaseUrl() } returns liveUrl
        coEvery { serverRepository.headersForServer(liveUrl) } returns liveHeaders

        val viewModel = createViewModel(addAccount = false)
        // Deliberately NOT advancing to idle — init is still suspended.
        viewModel.addHeaderRow()
        viewModel.onHeaderNameChanged(0, "CF-Access-Client-Secret")
        viewModel.onHeaderValueChanged(0, "rotated-secret")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.customHeaders)
            .containsExactly(CustomHeaderRow("CF-Access-Client-Secret", "rotated-secret"))
    }

    /** A reserved name is rejected inline, before any network or storage write. */
    @Test
    fun `a reserved header name blocks the connect entirely`() = runTest(testDispatcher) {
        val viewModel = createViewModel(addAccount = false)
        advanceUntilIdle()

        viewModel.onUrlChanged(pendingUrl)
        viewModel.addHeaderRow()
        viewModel.onHeaderNameChanged(0, "Authorization")
        viewModel.onHeaderValueChanged(0, "Basic abc")
        viewModel.validateAndConnect()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.headerError)
            .isEqualTo(HeaderFieldError(index = 0, reason = HeaderRejection.ReservedName))
        assertThat(viewModel.uiState.value.showAdvanced).isTrue()
        coVerify(exactly = 0) { serverRepository.setHeaders(any(), any()) }
        coVerify(exactly = 0) { configRepository.resolveServer(any(), any()) }
    }

    /**
     * A probe without the credential comes back as the gateway's own login page, which surfaces as a
     * generic connection error — sending the user to re-check a URL that was never the problem while
     * the token they typed was never stored.
     */
    @Test
    fun `a header write the store refuses stops the connect instead of probing`() = runTest(testDispatcher) {
        coEvery { serverRepository.setHeaders(any(), any()) } returns
            HeaderWriteResult.Refused(HeaderWriteFailure.StorageUnavailable)

        val viewModel = createViewModel(addAccount = false)
        advanceUntilIdle()

        viewModel.onUrlChanged(pendingUrl)
        viewModel.addHeaderRow()
        viewModel.onHeaderNameChanged(0, "CF-Access-Client-Id")
        viewModel.onHeaderValueChanged(0, "id-value")
        viewModel.validateAndConnect()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.headersSaveFailure)
            .isEqualTo(HeaderWriteFailure.StorageUnavailable)
        assertThat(viewModel.uiState.value.showAdvanced).isTrue()
        assertThat(viewModel.uiState.value.isLoading).isFalse()
        coVerify(exactly = 0) { configRepository.resolveServer(any(), any()) }
    }

    /**
     * The pre-login twin of the settings editor's refusal. This screen is reached *because* the
     * server is unreachable, so an editor that could not load its credential is the normal case here,
     * not an edge one — and clearing a row in it must not be able to delete the only copy.
     */
    @Test
    fun `a clear the store refuses stops the connect and names the reason`() = runTest(testDispatcher) {
        coEvery { serverDataStore.awaitBaseUrl() } returns liveUrl
        coEvery { serverRepository.headersForServer(liveUrl) } returns null
        coEvery { serverRepository.setHeaders(any(), any()) } returns
            HeaderWriteResult.Refused(HeaderWriteFailure.UnverifiedDelete)

        val viewModel = createViewModel(addAccount = false)
        advanceUntilIdle()

        viewModel.addHeaderRow()
        viewModel.removeHeaderRow(0)
        viewModel.validateAndConnect()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.headersSaveFailure)
            .isEqualTo(HeaderWriteFailure.UnverifiedDelete)
        assertThat(viewModel.uiState.value.showAdvanced).isTrue()
        assertThat(viewModel.uiState.value.isLoading).isFalse()
        // Probing anyway would blame the URL for a credential that was never written.
        coVerify(exactly = 0) { configRepository.resolveServer(any(), any()) }
    }

    @Test
    fun `an unreadable store is flagged rather than shown as an empty editor`() = runTest(testDispatcher) {
        coEvery { serverDataStore.awaitBaseUrl() } returns liveUrl
        coEvery { serverRepository.headersForServer(liveUrl) } returns null

        val viewModel = createViewModel(addAccount = false)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.headersLoadFailed).isTrue()
        // Expanded, so the warning isn't hidden behind a collapsed section.
        assertThat(viewModel.uiState.value.showAdvanced).isTrue()
    }

    /**
     * The web UI's login redirect, pasted from a browser. Stored as typed it would validate —
     * `/api/config` resolves against the origin — and every hand-built URL would then carry
     * `/login?redirect_to=…` in front of its path. The deployment address is what gets stored and
     * shown.
     */
    @Test
    fun `a pasted web login URL is stored as the deployment address`() = runTest(testDispatcher) {
        val viewModel = createViewModel(addAccount = false)
        advanceUntilIdle()

        viewModel.onUrlChanged("$pendingUrl/login?redirect_to=%2Fc%2Fnew")
        viewModel.validateAndConnect()
        advanceUntilIdle()

        coVerify(exactly = 1) { accountSwitcher.prepareForSignIn(pendingUrl) }
        // The cleaned address has no path left, so there is nothing to fall back from.
        coVerify(exactly = 1) { configRepository.resolveServer(listOf(pendingUrl), any()) }
        assertThat(viewModel.uiState.value.url).isEqualTo(pendingUrl)
        assertThat(viewModel.uiState.value.isValidated).isTrue()
    }

    /** The http warning's confirm path goes through the same cleaning as the main path. */
    @Test
    fun `confirming an http URL cleans it the same way`() = runTest(testDispatcher) {
        val httpUrl = "http://10.0.0.5:3080"
        val viewModel = createViewModel(addAccount = false)
        advanceUntilIdle()

        viewModel.onUrlChanged("$httpUrl/c/new")
        viewModel.validateAndConnect()
        assertThat(viewModel.uiState.value.showHttpWarning).isTrue()
        viewModel.confirmHttpConnection()
        advanceUntilIdle()

        coVerify(exactly = 1) { accountSwitcher.prepareForSignIn(httpUrl) }
        assertThat(viewModel.uiState.value.url).isEqualTo(httpUrl)
    }

    @Test
    fun `where cleartext to a domain name is blocked, an http domain is refused before the prompt`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel(addAccount = false, cleartextBlockedForDomainNames = true)
            advanceUntilIdle()

            viewModel.onUrlChanged("http://chat.example.com:3080")
            viewModel.validateAndConnect()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.showHttpWarning).isFalse()
            assertThat(viewModel.uiState.value.error).isEqualTo(FailureKind.CleartextBlocked.message())
            coVerify(exactly = 0) { configRepository.resolveServer(any(), any()) }
        }

    @Test
    fun `where cleartext to a domain name is blocked, a local http address still gets the prompt`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel(addAccount = false, cleartextBlockedForDomainNames = true)
            advanceUntilIdle()

            for (url in listOf("http://192.168.1.5:3080", "http://nas", "http://nas.local:3080", "http://[fd00::1]:3080")) {
                viewModel.dismissHttpWarning()
                viewModel.onUrlChanged(url)
                viewModel.validateAndConnect()

                assertWithMessage(url).that(viewModel.uiState.value.showHttpWarning).isTrue()
                assertWithMessage(url).that(viewModel.uiState.value.error).isNull()
            }
        }

    @Test
    fun `where cleartext is allowed, an http domain gets the prompt`() = runTest(testDispatcher) {
        val viewModel = createViewModel(addAccount = false)
        advanceUntilIdle()

        viewModel.onUrlChanged("http://chat.example.com:3080")
        viewModel.validateAndConnect()

        assertThat(viewModel.uiState.value.showHttpWarning).isTrue()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `a dotted DNS name other than local is a qualified domain name, an address or bare name is not`() {
        val qualified = listOf(
            "http://chat.example.com", "http://librechat.lan:3080", "http://host.home.arpa/librechat",
            "http://user@chat.example.com", "http://CHAT.Example.COM.", "http://box.tail1234.ts.net:8080",
        )
        val notQualified = listOf(
            "http://192.168.1.5", "http://10.0.0.5:3080", "http://[::1]:3080", "http://[fd00::1]",
            "http://nas", "http://nas:3080", "http://nas.local", "http://NAS.LOCAL.:3080", "http://localhost",
        )
        for (url in qualified) assertWithMessage(url).that(hostIsQualifiedDomainName(url)).isTrue()
        for (url in notQualified) assertWithMessage(url).that(hostIsQualifiedDomainName(url)).isFalse()
    }

    // --- Subpath or origin ----------------------------------------------------------------------

    private val origin = "https://example.com"
    private val pastedPage = "https://example.com/some/page"
    private val typedHeaders = mapOf("CF-Access-Client-Id" to "id-value")

    private fun originAnswers() {
        coEvery { configRepository.resolveServer(any(), any()) } returns ServerResolution.Resolved(origin, config)
    }

    @Test
    fun `an address with a path offers the origin as the fallback candidate`() = runTest(testDispatcher) {
        val viewModel = createViewModel(addAccount = false)
        advanceUntilIdle()

        viewModel.onUrlChanged(pastedPage)
        viewModel.validateAndConnect()
        advanceUntilIdle()

        coVerify(exactly = 1) { configRepository.resolveServer(listOf(pastedPage, origin), any()) }
    }

    /** A deployment under a path is a different server from its host; it answered, so it stays. */
    @Test
    fun `a subpath that answers is stored as typed`() = runTest(testDispatcher) {
        val subpath = "https://example.com/librechat"
        val viewModel = createViewModel(addAccount = false)
        advanceUntilIdle()

        viewModel.onUrlChanged(subpath)
        viewModel.validateAndConnect()
        advanceUntilIdle()

        coVerify(exactly = 1) { accountSwitcher.prepareForSignIn(subpath) }
        assertThat(viewModel.uiState.value.url).isEqualTo(subpath)
    }

    @Test
    fun `when only the origin answers, the origin is stored and shown`() = runTest(testDispatcher) {
        originAnswers()
        val viewModel = createViewModel(addAccount = false)
        advanceUntilIdle()

        viewModel.onUrlChanged(pastedPage)
        viewModel.validateAndConnect()
        advanceUntilIdle()

        coVerify(exactly = 1) { accountSwitcher.prepareForSignIn(origin) }
        coVerify(exactly = 0) { accountSwitcher.prepareForSignIn(pastedPage) }
        coVerify(exactly = 0) { serverDataStore.setServerUrl(pastedPage) }
        assertThat(viewModel.uiState.value.url).isEqualTo(origin)
        assertThat(viewModel.uiState.value.isValidated).isTrue()
    }

    /**
     * The serverId that keys gateway headers hashes the stored URL. Left under the typed address, the
     * credential would be filed under a server the app never contacts, and every request after the
     * probe would go out without it.
     */
    @Test
    fun `typed headers follow the origin, before the add session reads them`() = runTest(testDispatcher) {
        originAnswers()
        val viewModel = createViewModel(addAccount = true)
        advanceUntilIdle()

        viewModel.onUrlChanged(pastedPage)
        viewModel.addHeaderRow()
        viewModel.onHeaderNameChanged(0, "CF-Access-Client-Id")
        viewModel.onHeaderValueChanged(0, "id-value")
        viewModel.validateAndConnect()
        advanceUntilIdle()

        coVerifyOrder {
            serverRepository.setHeaders(pastedPage, typedHeaders)
            configRepository.resolveServer(listOf(pastedPage, origin), typedHeaders)
            serverRepository.setHeaders(origin, typedHeaders)
            serverRepository.setHeaders(pastedPage, emptyMap())
            accountSwitcher.beginAdd(origin)
        }
    }

    /** Headers stored earlier under the typed address are carried to the probe, and to the origin. */
    @Test
    fun `stored headers for the typed address ride the probe`() = runTest(testDispatcher) {
        coEvery { serverRepository.headersForServer(pastedPage) } returns typedHeaders
        originAnswers()
        val viewModel = createViewModel(addAccount = false)
        advanceUntilIdle()

        viewModel.onUrlChanged(pastedPage)
        viewModel.validateAndConnect()
        advanceUntilIdle()

        coVerify(exactly = 1) { configRepository.resolveServer(any(), typedHeaders) }
        coVerify(exactly = 1) { serverRepository.setHeaders(origin, typedHeaders) }
        // Not this attempt's to delete: the entry may belong to another account's server.
        coVerify(exactly = 0) { serverRepository.setHeaders(pastedPage, any()) }
    }

    @Test
    fun `a refused re-key stops before anything is stored`() = runTest(testDispatcher) {
        coEvery { serverRepository.setHeaders(origin, any()) } returns
            HeaderWriteResult.Refused(HeaderWriteFailure.StorageUnavailable)
        originAnswers()
        val viewModel = createViewModel(addAccount = false)
        advanceUntilIdle()

        viewModel.onUrlChanged(pastedPage)
        viewModel.addHeaderRow()
        viewModel.onHeaderNameChanged(0, "CF-Access-Client-Id")
        viewModel.onHeaderValueChanged(0, "id-value")
        viewModel.validateAndConnect()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.headersSaveFailure).isEqualTo(HeaderWriteFailure.StorageUnavailable)
        assertThat(viewModel.uiState.value.isValidated).isFalse()
        coVerify(exactly = 0) { accountSwitcher.prepareForSignIn(any()) }
        coVerify(exactly = 0) { serverDataStore.setServerUrl(any()) }
    }

    @Test
    fun `known servers group accounts by server, most recently used first`() {
        val servers = knownServersOf(
            listOf(
                entry("a", "https://chat.example.com", lastActiveAt = 10),
                entry("b", "https://host.example.net/librechat/", lastActiveAt = 30),
                entry("c", "https://chat.example.com", lastActiveAt = 50),
            ),
        )

        assertThat(servers).containsExactly(
            KnownServer("https://chat.example.com", "chat.example.com", "chat.example.com", accountCount = 2),
            KnownServer(
                "https://host.example.net/librechat/",
                "host.example.net/librechat",
                "host.example.net",
                accountCount = 1,
            ),
        ).inOrder()
    }

    @Test
    fun `known servers follow the roster`() = runTest(testDispatcher) {
        val viewModel = createViewModel(addAccount = false)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.knownServers).isEmpty()

        rosterEntries.value = listOf(entry("a", liveUrl, lastActiveAt = 1))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.knownServers.map { it.url }).containsExactly(liveUrl)
    }

    @Test
    fun `a known server tap drops rows typed for another server`() = runTest(testDispatcher) {
        coEvery { serverRepository.headersForServer(liveUrl) } returns liveHeaders
        val viewModel = createViewModel(addAccount = true)
        advanceUntilIdle()

        viewModel.addHeaderRow()
        viewModel.onHeaderNameChanged(0, "CF-Access-Client-Id")
        viewModel.onHeaderValueChanged(0, "other-server-token")
        viewModel.connectTo(liveUrl)
        advanceUntilIdle()

        coVerify(exactly = 0) { serverRepository.setHeaders(any(), any()) }
        coVerify { configRepository.resolveServer(listOf(liveUrl), liveHeaders) }
        assertThat(viewModel.uiState.value.customHeaders).isEmpty()
    }

    private fun entry(id: String, serverUrl: String, lastActiveAt: Long) =
        AccountEntry(accountId = id, serverUrl = serverUrl, displayLabel = id, lastActiveAt = lastActiveAt)
}
