package com.garfiec.librechat.feature.auth.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AccountSwitcher
import com.garfiec.librechat.core.data.repository.BannerRepository
import com.garfiec.librechat.core.model.Banner
import com.garfiec.librechat.core.model.config.StartupConfig
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

class AuthServerConfigTest {

    private val bannerRepository = mockk<BannerRepository>()
    private val accountSwitcher = mockk<AccountSwitcher> {
        coEvery { withPendingIdentity<Any?>(any()) } coAnswers { firstArg<suspend () -> Any?>().invoke() }
    }

    @Test
    fun `only a public banner shows before sign-in`() = runTest {
        val banner = Banner(bannerId = "b1", message = "Maintenance tonight", isPublic = true)
        coEvery { bannerRepository.getBanner() } returns Result.Success(banner)
        assertThat(publicBanner(bannerRepository, accountSwitcher)).isEqualTo(banner)

        coEvery { bannerRepository.getBanner() } returns Result.Success(banner.copy(isPublic = false))
        assertThat(publicBanner(bannerRepository, accountSwitcher)).isNull()

        coEvery { bannerRepository.getBanner() } returns Result.Success(banner.copy(isPublic = null))
        assertThat(publicBanner(bannerRepository, accountSwitcher)).isNull()
    }

    @Test
    fun `a failed banner fetch shows no banner`() = runTest {
        coEvery { bannerRepository.getBanner() } returns Result.Error(IOException("offline"))
        assertThat(publicBanner(bannerRepository, accountSwitcher)).isNull()
    }

    @Test
    fun `the banner comes from the pending server during add-account`() = runTest {
        var routed = false
        coEvery { accountSwitcher.withPendingIdentity<Any?>(any()) } coAnswers {
            routed = true
            firstArg<suspend () -> Any?>().invoke().also { routed = false }
        }
        val banner = Banner(bannerId = "b1", message = "Pending maintenance", isPublic = true)
        coEvery { bannerRepository.getBanner() } answers { Result.Success(banner.takeIf { routed }) }
        assertThat(publicBanner(bannerRepository, accountSwitcher)).isEqualTo(banner)
    }

    @Test
    fun `a blank app title reads as LibreChat`() {
        assertThat(appTitleOf(StartupConfig(appTitle = "Acme Chat"))).isEqualTo("Acme Chat")
        assertThat(appTitleOf(StartupConfig(appTitle = " "))).isEqualTo("LibreChat")
    }

    @Test
    fun `the server label drops the scheme and keeps port and path`() {
        assertThat(serverLabel("https://chat.example.com:8443/team-a/")).isEqualTo("chat.example.com:8443/team-a")
        assertThat(serverLabel("http://10.0.2.2:3080")).isEqualTo("10.0.2.2:3080")
    }
}
